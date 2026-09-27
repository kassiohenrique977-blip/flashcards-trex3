package com.flashcards.core.study

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.srs.TimeUnits

/** Monta a fila de uma sessão de estudo. */
object StudyPlanner {

    /** Cartões em etapas curtas que vencem nos próximos 20 minutos já entram na sessão. */
    const val DEFAULT_LEARN_AHEAD_MS = 20 * TimeUnits.MINUTE_MS

    /**
     * Ordem da fila:
     * 1. cartões em (re)aprendizado que vencem até `now + learnAheadMs`;
     * 2. revisões que vencem antes do fim do dia de estudo, as mais atrasadas primeiro,
     *    até `maxReviewsPerDay - reviewsDoneToday`;
     * 3. cartões novos na ordem de introdução, até `newCardsPerDay - newIntroducedToday`.
     */
    fun plan(
        cards: List<Card>,
        now: Long,
        dayEnd: Long,
        settings: StudySettings,
        newIntroducedToday: Int = 0,
        reviewsDoneToday: Int = 0,
        learnAheadMs: Long = DEFAULT_LEARN_AHEAD_MS,
    ): List<Card> {
        val byDue = compareBy<Card>({ it.scheduling.dueAt }, { it.id })

        val learning = cards
            .filter { it.scheduling.state.inSteps && it.scheduling.dueAt <= now + learnAheadMs }
            .sortedWith(byDue)

        val reviewQuota = (settings.maxReviewsPerDay - reviewsDoneToday).coerceAtLeast(0)
        val reviews = cards
            .filter { it.scheduling.state == CardState.REVIEW && it.scheduling.dueAt < dayEnd }
            .sortedWith(byDue)
            .take(reviewQuota)

        val newQuota = (settings.newCardsPerDay - newIntroducedToday).coerceAtLeast(0)
        val fresh = cards
            .filter { it.scheduling.state == CardState.NEW }
            .sortedWith(byDue)
            .take(newQuota)

        return learning + reviews + fresh
    }
}
