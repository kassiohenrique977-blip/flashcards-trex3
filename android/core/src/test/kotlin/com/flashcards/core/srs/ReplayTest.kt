package com.flashcards.core.srs

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.Review
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.TimeUnits.DAY_MS
import com.flashcards.core.srs.TimeUnits.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayTest {

    private val scheduler = Sm2Scheduler()
    private val t0 = 1_700_000_000_000L
    private val initial = SchedulingState()

    private val history = listOf(
        review("a", Rating.GOOD, t0),
        review("b", Rating.GOOD, t0 + 10 * MINUTE_MS),
        review("c", Rating.GOOD, t0 + 1 * DAY_MS),
        review("d", Rating.AGAIN, t0 + 4 * DAY_MS),
        review("e", Rating.GOOD, t0 + 4 * DAY_MS + 10 * MINUTE_MS),
        review("f", Rating.EASY, t0 + 6 * DAY_MS),
    )

    @Test
    fun `replay equals answering one by one`() {
        val incremental = history.fold(initial) { state, r -> scheduler.schedule(state, r.rating, r.reviewedAt) }

        assertEquals(incremental, scheduler.replay(initial, history))
    }

    @Test
    fun `arrival order does not change the result`() {
        val expected = scheduler.replay(initial, history)

        repeat(20) { seed ->
            val shuffled = history.shuffled(kotlin.random.Random(seed))
            assertEquals(expected, scheduler.replay(initial, shuffled))
        }
    }

    @Test
    fun `same timestamp is broken by id`() {
        val first = review("1", Rating.AGAIN, t0)
        val second = review("2", Rating.EASY, t0)

        val result = scheduler.replay(initial, listOf(second, first))

        // AGAIN (id "1") primeiro, depois EASY: gradua direto.
        assertEquals(CardState.REVIEW, result.state)
        assertEquals(4, result.intervalDays)
    }

    @Test
    fun `no reviews keeps the initial state`() {
        assertEquals(initial, scheduler.replay(initial, emptyList()))
    }

    private fun review(id: String, rating: Rating, at: Long) = Review(
        id = id,
        cardId = "card",
        deckId = "deck",
        rating = rating,
        reviewedAt = at,
        previousInterval = 0,
        newInterval = 0,
        previousState = CardState.NEW,
        newState = CardState.NEW,
        durationMs = 0,
        source = ReviewSource.WATCH,
        deviceId = null,
        sessionId = null,
        algorithm = Sm2Scheduler.ID,
    )
}
