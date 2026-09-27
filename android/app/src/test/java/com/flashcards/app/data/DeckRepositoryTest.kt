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
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.SyncStatus
import com.flashcards.core.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckRepositoryTest {

    private lateinit var db: FlashcardsDatabase
    private lateinit var decks: RoomDeckRepository
    private lateinit var cards: RoomCardRepository
    private val clock = FakeClock()
    private val ids = SequentialIds()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        decks = RoomDeckRepository(db, clock, ids)
        cards = RoomCardRepository(db, clock, ids)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createDeck trims input and records a pending change`() = runTest {
        val deck = decks.createDeck("  Inglês  ", " Vocabulário ")

        assertEquals("Inglês", deck.name)
        assertEquals("Vocabulário", deck.description)
        assertTrue(deck.syncToWatch)
        assertEquals(deck, decks.getDeck(deck.id))

        val change = db.syncStateDao().changesSince(0, 10).single()
        assertEquals(EntityType.DECK, change.entityType)
        assertEquals(deck.id, change.entityId)
        assertEquals(1L, change.version)
        assertEquals(SyncStatus.PENDING, change.syncStatus)
        assertFalse(change.deleted)
    }

    @Test
    fun `createDeck rejects a blank name and writes nothing`() = runTest {
        val result = runCatching { decks.createDeck("   ") }

        assertTrue(result.exceptionOrNull() is ValidationException)
        assertTrue(db.syncStateDao().changesSince(0, 10).isEmpty())
    }

    @Test
    fun `summaries count total due and new cards`() = runTest {
        val deck = decks.createDeck("Inglês")
        cards.addCards(deck.id, listOf(CardDraft("a", "b"), CardDraft("c", "d"), CardDraft("e", "f")))
        val all = db.cardDao().getByDeck(deck.id).sortedBy { it.dueAt }
        db.cardDao().update(all[0].copy(state = CardState.REVIEW, dueAt = 500))
        db.cardDao().update(all[1].copy(state = CardState.REVIEW, dueAt = 5_000))

        val summary = decks.observeDeckSummaries(now = 1_000).first().single()

        assertEquals(3, summary.cardCount)
        assertEquals(1, summary.dueCount)
        assertEquals(1, summary.newCount)
    }

    @Test
    fun `summaries are sorted by name ignoring case and include empty decks`() = runTest {
        decks.createDeck("zeta")
        decks.createDeck("Alfa")
        decks.createDeck("beta")

        val summaries = decks.observeDeckSummaries(now = 0).first()

        assertEquals(listOf("Alfa", "beta", "zeta"), summaries.map { it.deck.name })
        assertTrue(summaries.all { it.cardCount == 0 })
    }

    @Test
    fun `updateDeck changes content and bumps the version`() = runTest {
        val deck = decks.createDeck("Ingles")
        clock.time = 2_000

        val updated = decks.updateDeck(deck.id, "Inglês", "Nível B1")

        assertEquals("Inglês", updated.name)
        assertEquals("Nível B1", updated.description)
        assertEquals(2_000, updated.updatedAt)
        assertEquals(2L, db.syncStateDao().get("DECK", deck.id)!!.version)
    }

    @Test
    fun `updateDeck without changes keeps the version`() = runTest {
        val deck = decks.createDeck("Inglês")

        decks.updateDeck(deck.id, " Inglês ", "")

        assertEquals(1L, db.syncStateDao().get("DECK", deck.id)!!.version)
    }

    @Test
    fun `deleteDeck removes cards and reviews and leaves only a newer tombstone`() = runTest {
        val deck = decks.createDeck("Inglês")
        cards.addCards(deck.id, listOf(CardDraft("a", "b"), CardDraft("c", "d")))
        val card = db.cardDao().getByDeck(deck.id).first()
        db.reviewDao().insert(testReview(id = "r1", cardId = card.id, deckId = deck.id))

        decks.deleteDeck(deck.id)

        assertNull(decks.getDeck(deck.id))
        assertTrue(db.cardDao().getByDeck(deck.id).isEmpty())
        assertTrue(db.reviewDao().getForCard(card.id).isEmpty())
        val tombstone = db.syncStateDao().changesSince(0, 10).single()
        assertEquals(EntityType.DECK, tombstone.entityType)
        assertTrue(tombstone.deleted)
        // deck v1, cartões v2 e v3: a lápide precisa ser maior que o cursor que o relógio já tem.
        assertEquals(4L, tombstone.version)
    }

    @Test
    fun `re-enabling syncToWatch re-records every card after the deck`() = runTest {
        val deck = decks.createDeck("Inglês") // v1
        cards.addCards(deck.id, listOf(CardDraft("a", "b"), CardDraft("c", "d"))) // v2, v3
        decks.setSyncToWatch(deck.id, enabled = false) // v4

        decks.setSyncToWatch(deck.id, enabled = true)

        val changes = db.syncStateDao().changesSince(4, 10)
        assertEquals(listOf(5L, 6L, 7L), changes.map { it.version })
        assertEquals(
            listOf(EntityType.DECK, EntityType.CARD, EntityType.CARD),
            changes.map { it.entityType },
        )
        assertTrue(decks.getDeck(deck.id)!!.syncToWatch)
    }

    @Test
    fun `setSyncToWatch with the same value records nothing`() = runTest {
        val deck = decks.createDeck("Inglês")

        decks.setSyncToWatch(deck.id, enabled = true)

        assertEquals(1L, db.syncStateDao().maxVersion())
    }
}
