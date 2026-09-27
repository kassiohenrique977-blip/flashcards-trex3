package com.flashcards.core.stats

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.study.StudyDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatsCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val now = millis("2026-09-14T12:00:00Z")
    private val noTotals = ReviewTotals(0, 0, 0)

    private fun event(card: String, rating: Rating, at: Long, from: CardState = CardState.REVIEW, to: CardState = CardState.REVIEW) =
        ReviewEvent(card, rating, at, from, to)

    private fun compute(
        events: List<ReviewEvent> = emptyList(),
        learned: List<Long> = emptyList(),
        states: Map<CardState, Int> = emptyMap(),
        days: Int = 30,
    ) = StatsCalculator.compute(events, learned, states, noTotals, remainingToday = 7, now, zone, cutoffHour = 0, days = days)

    @Test
    fun `reviews are grouped by study day with today at the end`() {
        val stats = compute(
            events = listOf(
                event("c1", Rating.GOOD, now - 3_600_000),
                event("c1", Rating.AGAIN, now - 1_800_000),
                event("c2", Rating.HARD, now),
                event("c3", Rating.EASY, now - day),
                event("c4", Rating.GOOD, now - 40 * day), // fora da janela de 30 dias
            ),
        )

        assertEquals(30, stats.days.size)
        assertEquals(DayStats(millis("2026-09-14T00:00:00Z"), reviews = 3, correct = 1, hard = 1, again = 1), stats.days.last())
        assertEquals(1, stats.days[28].reviews)
        assertEquals(4, stats.days.sumOf { it.reviews })
        assertEquals(2, stats.studiedToday)
        assertEquals(3, stats.reviewsToday)
        assertEquals(33, stats.accuracyToday)
        assertEquals(7, stats.remainingToday)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val stats = compute(events = listOf(0, 1, 2, 4).map { event("c", Rating.GOOD, now - it * day) })

        assertEquals(3, stats.streakDays)
    }

    @Test
    fun `not having studied yet today keeps yesterday's streak`() {
        val stats = compute(events = listOf(1, 2).map { event("c", Rating.GOOD, now - it * day) })

        assertEquals(2, stats.streakDays)
        assertNull(stats.accuracyToday)
    }

    @Test
    fun `a missed day breaks the streak`() {
        val stats = compute(events = listOf(event("c", Rating.GOOD, now - 2 * day)))

        assertEquals(0, stats.streakDays)
    }

    @Test
    fun `learned cards accumulate day by day including older ones`() {
        val stats = compute(
            learned = listOf(now - 40 * day, now - 5 * day, now - 60_000),
            days = 10,
        )

        assertEquals(1, stats.learnedByDay.first())
        assertEquals(2, stats.learnedByDay[stats.learnedByDay.size - 2])
        assertEquals(3, stats.learnedByDay.last())
        assertEquals(10, stats.learnedByDay.size)
    }

    @Test
    fun `every state appears even with zero cards`() {
        val stats = compute(states = mapOf(CardState.NEW to 5, CardState.REVIEW to 2))

        assertEquals(mapOf(CardState.NEW to 5, CardState.LEARNING to 0, CardState.REVIEW to 2, CardState.RELEARNING to 0), stats.cardsByState)
        assertEquals(7, stats.totalCards)
    }

    @Test
    fun `day cutoff moves late night reviews to the previous day`() {
        val stats = StatsCalculator.compute(
            events = listOf(event("c", Rating.GOOD, millis("2026-09-14T02:00:00Z"))),
            firstLearnedAt = emptyList(),
            cardsByState = emptyMap(),
            totals = noTotals,
            remainingToday = 0,
            now = now,
            zone = zone,
            cutoffHour = 4,
            days = 2,
        )

        assertEquals(listOf(1, 0), stats.days.map { it.reviews })
    }

    @Test
    fun `recent windows follow the local calendar across daylight saving`() {
        val lisbon = ZoneId.of("Europe/Lisbon") // horário de verão começa em 29/03/2026
        val windows = StudyDay.recentWindows(millis("2026-03-30T12:00:00+01:00"), lisbon, cutoffHour = 0, count = 3)

        assertEquals(listOf(24L, 23L, 24L), windows.map { (it.end - it.start) / 3_600_000 })
        assertEquals(millis("2026-03-28T00:00:00Z"), windows.first().start)
    }

    private fun millis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}
