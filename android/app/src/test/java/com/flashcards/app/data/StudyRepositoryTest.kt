package com.flashcards.app.data

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.data.repository.RoomStudyRepository
import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.app.testutil.inMemoryDatabase
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.srs.Sm2Scheduler
import com.flashcards.core.study.StudyDayWindow
import com.flashcards.core.study.StudySession
import com.flashcards.core.study.StudySettings
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
class StudyRepositoryTest {

    private lateinit var db: FlashcardsDatabase
    private lateinit var study: RoomStudyRepository
    private lateinit var deckId: String
    private val clock = FakeClock(time = 1_700_000_000_000L)
    private val ids = SequentialIds()
    private val scheduler = Sm2Scheduler()
    private val today get() = StudyDayWindow(start = clock.time - 3_600_000, end = clock.time + 3_600_000)

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        study = RoomStudyRepository(db, clock)
        deckId = RoomDeckRepository(db, clock, ids).createDeck("Inglês").id
        RoomCardRepository(db, clock, ids).addCards(
            deckId,
            (1..4).map { CardDraft("front $it", "back $it") },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recordAnswer stores the review, the new schedule and a sync change`() = runTest {
        val session = newSession()
        val versionBefore = db.syncStateDao().maxVersion()

        val result = session.answer(Rating.EASY)
        val stored = study.recordAnswer(result.review, result.card.scheduling)

        assertTrue(stored)
        val entity = db.cardDao().get(result.card.id)!!
        assertEquals(CardState.REVIEW, entity.state)
        assertEquals(4, entity.intervalDays)
        assertEquals(listOf(result.review.id), db.reviewDao().getForCard(result.card.id).map { it.id })
        val change = db.syncStateDao().get("CARD", result.card.id)!!
        assertEquals(versionBefore + 1, change.version)
    }

    @Test
    fun `recording the same review twice changes nothing the second time`() = runTest {
        val result = newSession().answer(Rating.GOOD)
        study.recordAnswer(result.review, result.card.scheduling)
        val version = db.syncStateDao().maxVersion()

        val again = study.recordAnswer(result.review, result.card.scheduling)

        assertFalse(again)
        assertEquals(version, db.syncStateDao().maxVersion())
        assertEquals(1, db.reviewDao().getForCard(result.card.id).size)
    }

    @Test
    fun `answer for a deleted card is ignored`() = runTest {
        val result = newSession().answer(Rating.GOOD)
        RoomCardRepository(db, clock, ids).deleteCard(result.card.id)

        assertFalse(study.recordAnswer(result.review, result.card.scheduling))
    }

    @Test
    fun `loadSession respects new cards already introduced today`() = runTest {
        val settings = StudySettings(newCardsPerDay = 2)
        val first = newSession(settings).answer(Rating.GOOD) // novo -> aprendizado (10 min)
        study.recordAnswer(first.review, first.card.scheduling)

        val plan = study.loadSession(deckId, clock.time, today, settings)

        // O cartão em aprendizado vence em 10 min (dentro da janela de 20) e só sobra 1 novo.
        assertEquals(listOf(CardState.LEARNING, CardState.NEW), plan.map { it.scheduling.state })
        assertEquals(first.card.id, plan.first().id)
    }

    private suspend fun newSession(settings: StudySettings = StudySettings()): StudySession {
        val cards = study.loadSession(deckId, clock.time, today, settings)
        return StudySession(cards, scheduler, clock, ids, ReviewSource.PHONE)
    }
}
