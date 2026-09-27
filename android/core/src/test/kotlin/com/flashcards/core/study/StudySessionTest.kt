package com.flashcards.core.study

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.srs.Sm2Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionTest {

    private val now = 1_700_000_000_000L
    private val clock = TestClock(now)
    private val reviewCards = (1..5).map { card("c$it", CardState.REVIEW, dueAt = now) }

    private fun session(cards: List<com.flashcards.core.model.Card> = reviewCards) =
        StudySession(cards, Sm2Scheduler(), clock, TestIds(), ReviewSource.WATCH, deviceId = "watch-1")

    @Test
    fun `a forgotten card comes back after three other cards`() {
        val session = session()
        val shown = mutableListOf<String>()

        shown += session.current!!.id
        session.answer(Rating.AGAIN)
        while (!session.isFinished) {
            shown += session.current!!.id
            session.answer(Rating.GOOD)
        }

        assertEquals(listOf("c1", "c2", "c3", "c4", "c1", "c5"), shown)
    }

    @Test
    fun `requeue goes to the end when fewer cards remain`() {
        val session = session(reviewCards.take(2))

        session.answer(Rating.AGAIN)

        assertEquals("c2", session.current!!.id)
        session.answer(Rating.GOOD)
        assertEquals("c1", session.current!!.id)
    }

    @Test
    fun `position keeps the number of a returning card`() {
        val session = session()
        val positions = mutableListOf<Int>()

        positions += session.position
        session.answer(Rating.AGAIN)
        while (!session.isFinished) {
            positions += session.position
            session.answer(Rating.GOOD)
        }

        assertEquals(listOf(1, 2, 3, 4, 4, 5), positions)
        assertEquals(5, session.total)
    }

    @Test
    fun `summary counts answers by rating and total time`() {
        val session = session()

        session.answer(Rating.AGAIN)
        clock.time += 5_000
        session.answer(Rating.HARD)
        clock.time += 5_000
        session.answer(Rating.GOOD)
        session.answer(Rating.EASY)
        session.answer(Rating.GOOD) // c1 de volta
        clock.time += 2_000
        session.answer(Rating.GOOD) // c5, fim

        val summary = session.summary()
        assertTrue(session.isFinished)
        assertEquals(5, summary.cardsStudied)
        assertEquals(6, summary.answers)
        assertEquals(4, summary.correct)
        assertEquals(1, summary.hard)
        assertEquals(1, summary.again)
        assertEquals(12_000, summary.durationMs)

        clock.time += 60_000
        assertEquals("o tempo para quando a sessão termina", 12_000, session.summary().durationMs)
    }

    @Test
    fun `review record describes the answer`() {
        val session = session()
        clock.time += 4_000

        val result = session.answer(Rating.GOOD)

        with(result.review) {
            assertEquals("c1", cardId)
            assertEquals("deck", deckId)
            assertEquals(Rating.GOOD, rating)
            assertEquals(now + 4_000, reviewedAt)
            assertEquals(5, previousInterval)
            assertEquals(result.card.scheduling.intervalDays, newInterval)
            assertEquals(CardState.REVIEW, previousState)
            assertEquals(CardState.REVIEW, newState)
            assertEquals(4_000, durationMs)
            assertEquals(ReviewSource.WATCH, source)
            assertEquals("watch-1", deviceId)
            assertEquals(session.sessionId, sessionId)
            assertEquals(Sm2Scheduler.ID, algorithm)
        }
        assertFalse(result.requeued)
    }

    @Test
    fun `learning steps keep a new card in the session until it graduates`() {
        val session = session(listOf(card("n1")))

        val first = session.answer(Rating.GOOD) // etapa 1 -> 2
        val second = session.answer(Rating.GOOD) // gradua

        assertTrue(first.requeued)
        assertEquals(CardState.LEARNING, first.card.scheduling.state)
        assertFalse(second.requeued)
        assertEquals(CardState.REVIEW, second.card.scheduling.state)
        assertEquals(0, second.review.previousInterval)
        assertTrue(session.isFinished)
    }

    @Test
    fun `empty session is finished immediately`() {
        val session = session(emptyList())

        assertTrue(session.isFinished)
        assertNull(session.current)
        assertEquals(0, session.summary().durationMs)
        assertThrows(IllegalStateException::class.java) { session.answer(Rating.GOOD) }
    }

    @Test
    fun `duplicated cards are studied once`() {
        val session = session(listOf(reviewCards[0], reviewCards[0], reviewCards[1]))

        assertEquals(2, session.total)
    }
}
