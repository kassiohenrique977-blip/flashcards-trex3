package com.flashcards.app.data

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.data.repository.RoomStatsRepository
import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.app.testutil.inMemoryDatabase
import com.flashcards.app.testutil.testReview
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.stats.ReviewTotals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomStatsRepositoryTest {

    private lateinit var db: FlashcardsDatabase
    private lateinit var stats: RoomStatsRepository
    private lateinit var english: String
    private lateinit var history: String
    private lateinit var cardA: String
    private lateinit var cardB: String
    private lateinit var cardC: String

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        val clock = FakeClock()
        val ids = SequentialIds()
        val decks = RoomDeckRepository(db, clock, ids)
        val cards = RoomCardRepository(db, clock, ids)
        english = decks.createDeck("Inglês").id
        history = decks.createDeck("História").id
        cardA = cards.createCard(english, CardDraft("a", "1")).id
        cardB = cards.createCard(english, CardDraft("b", "2")).id
        cardC = cards.createCard(history, CardDraft("c", "3")).id
        stats = RoomStatsRepository(db)

        val dao = db.reviewDao()
        // cardA: aprende em 100, esquece em 300, reaprende em 400 (conta a primeira vez).
        dao.insert(testReview("r1", cardA, english, reviewedAt = 100, rating = Rating.EASY).copy(newState = CardState.REVIEW))
        dao.insert(testReview("r2", cardA, english, reviewedAt = 300, rating = Rating.AGAIN).copy(previousState = CardState.REVIEW, newState = CardState.RELEARNING))
        dao.insert(testReview("r3", cardA, english, reviewedAt = 400, rating = Rating.GOOD).copy(previousState = CardState.RELEARNING, newState = CardState.REVIEW))
        // cardB: só aprendendo.
        dao.insert(testReview("r4", cardB, english, reviewedAt = 200, rating = Rating.HARD))
        // cardC: outro deck.
        dao.insert(testReview("r5", cardC, history, reviewedAt = 250, rating = Rating.GOOD).copy(newState = CardState.REVIEW))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `events are filtered by deck and date in time order`() = runTest {
        val events = stats.reviewEvents(english, since = 150)

        assertEquals(listOf(200L, 300L, 400L), events.map { it.reviewedAt })
        assertEquals(Rating.AGAIN, events[1].rating)
        assertEquals(CardState.RELEARNING, events[1].newState)
        assertEquals(5, stats.reviewEvents(null, since = 0).size)
    }

    @Test
    fun `first learning time is counted once per card`() = runTest {
        assertEquals(listOf(100L), stats.firstLearnedTimes(english))
        assertEquals(listOf(100L, 250L), stats.firstLearnedTimes(null).sorted())
    }

    @Test
    fun `cards are counted by state`() = runTest {
        assertEquals(mapOf(CardState.NEW to 2), stats.cardsByState(english))
        assertEquals(mapOf(CardState.NEW to 3), stats.cardsByState(null))
    }

    @Test
    fun `totals count correct and wrong answers`() = runTest {
        assertEquals(ReviewTotals(reviews = 4, correct = 2, again = 1), stats.totals(english))
        assertEquals(ReviewTotals(reviews = 5, correct = 3, again = 1), stats.totals(null))
    }
}
