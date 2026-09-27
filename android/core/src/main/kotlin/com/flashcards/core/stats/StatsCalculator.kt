package com.flashcards.core.stats

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.study.StudyDay
import com.flashcards.core.study.StudyDayWindow
import java.time.Instant
import java.time.ZoneId

/** Cálculo das estatísticas a partir dos dados brutos. Puro: tudo entra por parâmetro. */
object StatsCalculator {

    /**
     * @param events revisões que cobrem pelo menos os dias da sequência (qualquer ordem).
     * @param firstLearnedAt momento em que cada cartão graduou pela primeira vez.
     */
    fun compute(
        events: List<ReviewEvent>,
        firstLearnedAt: List<Long>,
        cardsByState: Map<CardState, Int>,
        totals: ReviewTotals,
        remainingToday: Int,
        now: Long,
        zone: ZoneId,
        cutoffHour: Int,
        days: Int = 30,
    ): Statistics {
        require(days >= 1) { "É preciso ao menos um dia." }
        val windows = StudyDay.recentWindows(now, zone, cutoffHour, days)
        val indexByStart = windows.withIndex().associate { (i, w) -> w.start to i }
        val today = windows.last()

        val reviews = IntArray(days)
        val correct = IntArray(days)
        val hard = IntArray(days)
        val again = IntArray(days)
        val studiedDays = HashSet<Long>()
        val cardsToday = HashSet<String>()

        for (event in events) {
            val dayStart = StudyDay.window(event.reviewedAt, zone, cutoffHour).start
            studiedDays += dayStart
            val i = indexByStart[dayStart] ?: continue
            reviews[i]++
            when (event.rating) {
                Rating.AGAIN -> again[i]++
                Rating.HARD -> hard[i]++
                Rating.GOOD, Rating.EASY -> correct[i]++
            }
            if (dayStart == today.start) cardsToday += event.cardId
        }

        val dayStats = windows.mapIndexed { i, w -> DayStats(w.start, reviews[i], correct[i], hard[i], again[i]) }
        val learned = firstLearnedAt.sorted()
        val last = dayStats.last()

        return Statistics(
            studiedToday = cardsToday.size,
            reviewsToday = last.reviews,
            correctToday = last.correct,
            hardToday = last.hard,
            againToday = last.again,
            remainingToday = remainingToday,
            streakDays = streak(studiedDays, today, zone),
            totalReviews = totals.reviews,
            totalCorrect = totals.correct,
            totalAgain = totals.again,
            days = dayStats,
            learnedByDay = windows.map { w -> countBefore(learned, w.end) },
            cardsByState = CardState.entries.associateWith { cardsByState[it] ?: 0 },
        )
    }

    /** Hoje sem estudo ainda não quebra a sequência: ela vale até ontem. */
    private fun streak(studied: Set<Long>, today: StudyDayWindow, zone: ZoneId): Int {
        var day = Instant.ofEpochMilli(today.start).atZone(zone)
        if (day.toInstant().toEpochMilli() !in studied) day = day.minusDays(1)
        var count = 0
        while (day.toInstant().toEpochMilli() in studied) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Quantos valores de [sorted] são menores que [limit] (busca binária). */
    private fun countBefore(sorted: List<Long>, limit: Long): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid] < limit) low = mid + 1 else high = mid
        }
        return low
    }
}
