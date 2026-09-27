package com.flashcards.core.srs

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.TimeUnits.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    private val scheduler = Sm2Scheduler()
    private val now = 1_700_000_000_000L

    @Test
    fun `every answer stamps lastReviewedAt`() {
        Rating.entries.forEach { rating ->
            assertEquals(now, scheduler.schedule(SchedulingState(), rating, now).lastReviewedAt)
        }
    }

    @Test
    fun `a new card starts from the configured ease`() {
        val result = scheduler.schedule(SchedulingState(easeFactor = 1.9), Rating.EASY, now)

        assertEquals(2.5, result.easeFactor, 0.0)
    }

    @Test
    fun `review answers are strictly ordered hard less than good less than easy`() {
        for (interval in listOf(1, 2, 3, 7, 15, 40, 120, 400)) {
            for (ease in listOf(1.3, 1.8, 2.5, 3.1)) {
                for (daysLate in listOf(0, 1, 5, 30)) {
                    val state = SchedulingState(
                        state = CardState.REVIEW,
                        dueAt = now - daysLate * DAY_MS,
                        intervalDays = interval,
                        easeFactor = ease,
                        repetitions = 3,
                    )
                    val hard = scheduler.schedule(state, Rating.HARD, now).intervalDays
                    val good = scheduler.schedule(state, Rating.GOOD, now).intervalDays
                    val easy = scheduler.schedule(state, Rating.EASY, now).intervalDays
                    val label = "iv=$interval ease=$ease atraso=$daysLate"
                    assertTrue("$label: difícil $hard > $interval", hard > interval)
                    assertTrue("$label: bom $good > difícil $hard", good > hard)
                    assertTrue("$label: fácil $easy > bom $good", easy > good)
                }
            }
        }
    }

    @Test
    fun `review due date matches the interval`() {
        val state = SchedulingState(state = CardState.REVIEW, dueAt = now, intervalDays = 10, repetitions = 2)

        val result = scheduler.schedule(state, Rating.GOOD, now)

        assertEquals(now + result.intervalDays * DAY_MS, result.dueAt)
    }

    @Test
    fun `forgetting a review card counts a lapse and resets repetitions`() {
        val state = SchedulingState(
            state = CardState.REVIEW,
            dueAt = now,
            intervalDays = 30,
            repetitions = 6,
            lapses = 1,
        )

        val result = scheduler.schedule(state, Rating.AGAIN, now)

        assertEquals(CardState.RELEARNING, result.state)
        assertEquals(2, result.lapses)
        assertEquals(0, result.repetitions)
    }

    @Test
    fun `without relearning steps a lapse goes straight back to review`() {
        val noRelearning = Sm2Scheduler(SchedulerConfig(relearningStepsMinutes = emptyList()))
        val state = SchedulingState(state = CardState.REVIEW, dueAt = now, intervalDays = 30)

        val result = noRelearning.schedule(state, Rating.AGAIN, now)

        assertEquals(CardState.REVIEW, result.state)
        assertEquals(1, result.intervalDays)
        assertEquals(now + DAY_MS, result.dueAt)
    }

    @Test
    fun `lapse multiplier keeps part of the old interval`() {
        val gentle = Sm2Scheduler(SchedulerConfig(lapseIntervalMultiplier = 0.5))
        val state = SchedulingState(state = CardState.REVIEW, dueAt = now, intervalDays = 30)

        assertEquals(15, gentle.schedule(state, Rating.AGAIN, now).intervalDays)
    }

    @Test
    fun `out of range learning step is clamped instead of crashing`() {
        val state = SchedulingState(state = CardState.LEARNING, dueAt = now, learningStep = 9)

        val result = scheduler.schedule(state, Rating.GOOD, now)

        assertEquals(CardState.REVIEW, result.state)
    }

    @Test
    fun `config rejects empty learning steps`() {
        assertThrows(IllegalArgumentException::class.java) {
            SchedulerConfig(learningStepsMinutes = emptyList())
        }
    }

    @Test
    fun `config rejects starting ease below the minimum`() {
        assertThrows(IllegalArgumentException::class.java) {
            SchedulerConfig(startingEase = 1.2, minimumEase = 1.3)
        }
    }
}
