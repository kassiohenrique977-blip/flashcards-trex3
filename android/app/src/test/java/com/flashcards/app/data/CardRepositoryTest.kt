package com.flashcards.app.data

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.app.testutil.inMemoryDatabase
import com.flashcards.app.testutil.testReview
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.CardState
import com.flashcards.core.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {

    private lateinit var db: FlashcardsDatabase
    private lateinit var cards: RoomCardRepository
    private lateinit var deckId: String
    private val clock = FakeClock()
    private val ids = SequentialIds()

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        cards = RoomCardRepository(db, clock, ids)
        deckId = RoomDeckRepository(db, clock, ids).createDeck("Inglês").id // versão 1
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createCard stores normalized content as a NEW card`() = runTest {
        val card = cards.createCard(
            deckId,
            CardDraft("  Hello ", " Olá ", listOf("ingles", " Ingles ", "saudação")),
        )

        assertEquals("Hello", card.front)
        assertEquals("Olá", card.back)
        assertEquals(listOf("ingles", "saudação"), card.tags)
        assertEquals(CardState.NEW, card.scheduling.state)
        assertEquals(listOf(card), cards.observeCards(deckId).first())

        val change = db.syncStateDao().get("CARD", card.id)!!
        assertEquals(deckId, change.parentId)
        assertEquals(2L, change.version)
    }

    @Test
    fun `createCard in a missing deck fails`() = runTest {
        val result = runCatching { cards.createCard("nao-existe", CardDraft("a", "b")) }

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `createCard rejects a blank back`() = runTest {
        val result = runCatching { cards.createCard(deckId, CardDraft("Hello", "  ")) }

        assertTrue(result.exceptionOrNull() is ValidationException)
    }

    @Test
    fun `updateCard replaces content but keeps study progress`() = runTest {
        val card = cards.createCard(deckId, CardDraft("Hello", "Olá"))
        val entity = db.cardDao().get(card.id)!!
        db.cardDao().update(entity.copy(state = CardState.REVIEW, intervalDays = 10, repetitions = 3))
        clock.time = 5_000

        val updated = cards.updateCard(card.id, CardDraft("Hello!", "Olá!", listOf("ingles")))

        assertEquals("Hello!", updated.front)
        assertEquals(listOf("ingles"), updated.tags)
        assertEquals(5_000, updated.updatedAt)
        assertEquals(CardState.REVIEW, updated.scheduling.state)
        assertEquals(10, updated.scheduling.intervalDays)
        assertEquals(3, updated.scheduling.repetitions)
        assertEquals(3L, db.syncStateDao().get("CARD", card.id)!!.version)
    }

    @Test
    fun `updateCard with identical content keeps the version`() = runTest {
        val card = cards.createCard(deckId, CardDraft("Hello", "Olá", listOf("ingles")))

        // Só espaços nas pontas: depois de normalizado, o conteúdo é o mesmo.
        cards.updateCard(card.id, CardDraft(" Hello", "Olá ", listOf(" ingles ")))

        assertEquals(2L, db.syncStateDao().get("CARD", card.id)!!.version)
    }

    @Test
    fun `deleteCard removes reviews and leaves a tombstone pointing to the deck`() = runTest {
        val card = cards.createCard(deckId, CardDraft("Hello", "Olá"))
        db.reviewDao().insert(testReview(id = "r1", cardId = card.id, deckId = deckId))

        cards.deleteCard(card.id)

        assertNull(cards.getCard(card.id))
        assertTrue(db.reviewDao().getForCard(card.id).isEmpty())
        val tombstone = db.syncStateDao().get("CARD", card.id)!!
        assertTrue(tombstone.deleted)
        assertEquals(deckId, tombstone.parentId)
        assertEquals(3L, tombstone.version)
    }

    @Test
    fun `addCards keeps the input order with consecutive versions`() = runTest {
        val count = cards.addCards(
            deckId,
            listOf(CardDraft("1", "um"), CardDraft("2", "dois"), CardDraft("3", "três")),
        )

        assertEquals(3, count)
        val ordered = db.cardDao().getByDeck(deckId).sortedBy { it.dueAt }
        assertEquals(listOf("1", "2", "3"), ordered.map { it.front })
        assertEquals(listOf(2L, 3L, 4L), db.syncStateDao().changesSince(1, 10).map { it.version })
    }

    @Test
    fun `addCards is all or nothing when a draft is invalid`() = runTest {
        val result = runCatching {
            cards.addCards(deckId, listOf(CardDraft("1", "um"), CardDraft("2", " ")))
        }

        assertTrue(result.exceptionOrNull() is ValidationException)
        assertTrue(db.cardDao().getByDeck(deckId).isEmpty())
    }

    @Test
    fun `inserting the same review twice keeps a single row`() = runTest {
        val card = cards.createCard(deckId, CardDraft("Hello", "Olá"))
        val review = testReview(id = "r1", cardId = card.id, deckId = deckId)

        val first = db.reviewDao().insert(review)
        val second = db.reviewDao().insert(review)

        assertTrue(first > 0)
        assertEquals(-1L, second)
        assertEquals(1, db.reviewDao().getForCard(card.id).size)
    }
}
