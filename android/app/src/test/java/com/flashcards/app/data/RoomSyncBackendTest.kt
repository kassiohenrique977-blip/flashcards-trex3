package com.flashcards.app.data

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.data.repository.RoomStudyRepository
import com.flashcards.app.data.sync.RoomSyncBackend
import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.FakeSettingsRepository
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.app.testutil.inMemoryDatabase
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.srs.Sm2Scheduler
import com.flashcards.core.srs.TimeUnits.MINUTE_MS
import com.flashcards.core.study.StudySession
import com.flashcards.core.sync.AckRequest
import com.flashcards.core.sync.PushRequest
import com.flashcards.core.sync.RejectedReview
import com.flashcards.core.sync.WireChange
import com.flashcards.core.sync.WireReview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSyncBackendTest {

    private val t0 = 1_700_000_000_000L
    private val clock = FakeClock(t0)
    private val ids = SequentialIds()
    private val scheduler = Sm2Scheduler()

    private lateinit var db: FlashcardsDatabase
    private lateinit var decks: RoomDeckRepository
    private lateinit var cards: RoomCardRepository
    private lateinit var backend: RoomSyncBackend
    private lateinit var deckId: String

    // Versões no feed: deck v1, cartões "Hello" v2, "Bye" v3, "Thanks" v4.
    private val hello = "id-2"
    private val bye = "id-3"

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        decks = RoomDeckRepository(db, clock, ids)
        cards = RoomCardRepository(db, clock, ids)
        backend = RoomSyncBackend(db, scheduler, SchedulerConfig(), FakeSettingsRepository(), { "phone-1" }, clock)
        deckId = decks.createDeck("Inglês").id
        cards.addCards(deckId, listOf(CardDraft("Hello", "Olá"), CardDraft("Bye", "Tchau"), CardDraft("Thanks", "Obrigado")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun review(id: String, cardId: String, rating: Int, at: Long = t0) = WireReview(
        id = id, cardId = cardId, deckId = deckId, rating = rating, reviewedAt = at,
        durationMs = 3_000, sessionId = "s1", previousState = 0, newState = 2,
    )

    @Test
    fun `first pull sends the deck before its cards along with the settings`() = runTest {
        val page = backend.changes("w1", since = 0, limit = 30)

        assertEquals(listOf("d", "c", "c", "c"), page.changes.map { it.type })
        assertEquals(WireChange(type = "d", id = deckId, name = "Inglês"), page.changes.first())
        assertEquals("Hello", page.changes[1].front)
        assertEquals(0, page.changes[1].state)
        assertEquals(4L, page.nextSince)
        assertFalse(page.hasMore)
        assertEquals(20, page.settings.newCardsPerDay)
        assertEquals(listOf(1.0, 10.0), page.settings.scheduler.learningStepsMinutes)
    }

    @Test
    fun `pages follow the limit and say when there is more`() = runTest {
        val first = backend.changes("w1", since = 0, limit = 2)
        val second = backend.changes("w1", since = first.nextSince, limit = 2)

        assertEquals(2, first.changes.size)
        assertEquals(2L, first.nextSince)
        assertTrue(first.hasMore)
        assertEquals(listOf(bye, "id-4"), second.changes.map { it.id })
        assertFalse(second.hasMore)
    }

    @Test
    fun `long texts end a page early`() = runTest {
        // ~3.900 caracteres por cartão: cabem 4 em MAX_PAGE_CHARS (16.000).
        cards.addCards(deckId, (1..6).map { CardDraft("x".repeat(1_900) + it, "y".repeat(1_900)) }) // v5..v10

        val page = backend.changes("w1", since = 4, limit = 30)

        assertEquals(4, page.changes.size)
        assertEquals(8L, page.nextSince)
        assertTrue(page.hasMore)
    }

    @Test
    fun `deck kept off the watch goes as a deletion and its cards are skipped`() = runTest {
        decks.setSyncToWatch(deckId, enabled = false) // v5
        cards.createCard(deckId, CardDraft("New", "Novo")) // v6: cartão de deck fora do relógio

        val page = backend.changes("w1", since = 4, limit = 30)

        assertEquals(listOf(WireChange(type = "d", id = deckId, deleted = true)), page.changes)
        assertEquals(6L, page.nextSince)
        assertFalse(page.hasMore)
    }

    @Test
    fun `deleted card is sent as a tombstone with its deck`() = runTest {
        cards.deleteCard(bye)

        val page = backend.changes("w1", since = 4, limit = 30)

        assertEquals(listOf(WireChange(type = "c", id = bye, deleted = true, deckId = deckId)), page.changes)
    }

    @Test
    fun `push stores the watch answer and sends the recalculated card back`() = runTest {
        val response = backend.push(PushRequest("w1", listOf(review("r1", hello, rating = 4))))

        assertEquals(listOf("r1"), response.accepted)
        val stored = db.reviewDao().getForCard(hello).single()
        assertEquals(ReviewSource.WATCH, stored.source)
        assertEquals("w1", stored.deviceId)
        assertEquals(Rating.EASY, stored.rating)

        val page = backend.changes("w1", since = 4, limit = 30)
        val card = page.changes.single()
        assertEquals(hello, card.id)
        assertEquals(CardState.REVIEW.code, card.state)
        assertEquals(4, card.intervalDays)
        assertEquals(t0, card.lastReviewedAt)
    }

    @Test
    fun `sending the same answers again changes nothing`() = runTest {
        val batch = PushRequest("w1", listOf(review("r1", hello, rating = 3)))
        backend.push(batch)
        val version = db.syncStateDao().maxVersion()

        val again = backend.push(batch)

        assertEquals(emptyList<String>(), again.accepted)
        assertEquals(listOf("r1"), again.duplicates)
        assertEquals(version, db.syncStateDao().maxVersion())
        assertEquals(1, db.reviewDao().getForCard(hello).size)
    }

    @Test
    fun `repeated id inside one batch is stored once`() = runTest {
        val response = backend.push(PushRequest("w1", listOf(review("r1", hello, 3), review("r1", hello, 3))))

        assertEquals(listOf("r1"), response.accepted)
        assertEquals(listOf("r1"), response.duplicates)
    }

    @Test
    fun `unknown cards and invalid answers are rejected for good`() = runTest {
        val response = backend.push(
            PushRequest("w1", listOf(review("r1", "sumiu", 3), review("r2", hello, rating = 9), review("r3", bye, 3))),
        )

        assertEquals(listOf("r3"), response.accepted)
        assertEquals(
            listOf(
                RejectedReview("r1", RoomSyncBackend.REASON_UNKNOWN_CARD),
                RejectedReview("r2", RoomSyncBackend.REASON_INVALID),
            ),
            response.rejected,
        )
    }

    @Test
    fun `phone and watch answers are merged in the order they happened`() = runTest {
        // No celular, às t0 + 10 min: BOM num cartão novo.
        clock.time = t0 + 10 * MINUTE_MS
        val study = RoomStudyRepository(db, clock)
        val phoneCard = requireNotNull(cards.getCard(hello))
        val phoneAnswer = StudySession(listOf(phoneCard), scheduler, clock, ids, ReviewSource.PHONE).answer(Rating.GOOD)
        study.recordAnswer(phoneAnswer.review, phoneAnswer.card.scheduling)

        // O relógio, offline, tinha respondido FÁCIL antes, às t0. Só chega agora.
        backend.push(PushRequest("w1", listOf(review("watch-1", hello, rating = 4, at = t0))))

        val expected = scheduler.schedule(
            scheduler.schedule(SchedulingState(dueAt = phoneCard.createdAt), Rating.EASY, t0),
            Rating.GOOD,
            t0 + 10 * MINUTE_MS,
        )
        assertEquals(expected, requireNotNull(cards.getCard(hello)).scheduling)
        assertEquals(CardState.REVIEW, expected.state)
        assertEquals(10, expected.intervalDays)
    }

    @Test
    fun `ack marks what the watch has and remembers the watch`() = runTest {
        assertFalse(backend.hello("w1").deviceKnown)
        clock.time = t0 + 5_000

        backend.ack(AckRequest("w1", cursor = 3))

        assertEquals(1, db.syncStateDao().observePendingCount().first())
        val device = requireNotNull(db.watchDeviceDao().get("w1"))
        assertEquals(3L, device.lastAckSeq)
        assertEquals(t0 + 5_000, device.lastSyncAt)
        assertTrue(backend.hello("w1").deviceKnown)
    }

    @Test
    fun `cursor beyond the feed is clamped`() = runTest {
        backend.ack(AckRequest("w1", cursor = 999))

        assertEquals(4L, requireNotNull(db.watchDeviceDao().get("w1")).lastAckSeq)
        assertEquals(0, db.syncStateDao().observePendingCount().first())
    }
}
