package com.flashcards.core.stats

import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.StatsRepository
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.srs.TimeUnits.DAY_MS
import com.flashcards.core.study.StudyDay
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Junta os dados do banco e calcula as estatísticas de um deck, ou de todos ([deckId] nulo). */
class StatisticsLoader(
    private val stats: StatsRepository,
    private val decks: DeckRepository,
    private val study: StudyRepository,
) {

    suspend fun load(
        deckId: String?,
        now: Long,
        zone: ZoneId,
        settings: StudySettings,
        days: Int = DEFAULT_DAYS,
    ): Statistics {
        val today = StudyDay.window(now, zone, settings.dayCutoffHour)
        val deckIds = deckId?.let(::listOf) ?: decks.observeDeckSummaries(now).first().map { it.deck.id }
        val remaining = deckIds.sumOf { study.loadSession(it, now, today, settings).size }
        return StatsCalculator.compute(
            events = stats.reviewEvents(deckId, since = today.start - HISTORY_DAYS * DAY_MS),
            firstLearnedAt = stats.firstLearnedTimes(deckId),
            cardsByState = stats.cardsByState(deckId),
            totals = stats.totals(deckId),
            remainingToday = remaining,
            now = now,
            zone = zone,
            cutoffHour = settings.dayCutoffHour,
            days = days,
        )
    }

    companion object {
        const val DEFAULT_DAYS = 30

        /** Até onde a sequência de dias é contada. */
        const val HISTORY_DAYS = 400
    }
}
