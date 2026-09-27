package com.flashcards.core.stats

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import kotlin.math.roundToInt

/** Revisão reduzida ao que as estatísticas usam. */
data class ReviewEvent(
    val cardId: String,
    val rating: Rating,
    val reviewedAt: Long,
    val previousState: CardState,
    val newState: CardState,
)

data class ReviewTotals(
    val reviews: Int,
    /** BOM + FÁCIL */
    val correct: Int,
    /** ERREI */
    val again: Int,
)

/** Um dia de estudo (vira na hora configurada, por padrão 4h). */
data class DayStats(
    val dayStart: Long,
    val reviews: Int,
    val correct: Int,
    val hard: Int,
    val again: Int,
)

data class Statistics(
    /** Cartões diferentes estudados hoje. */
    val studiedToday: Int,
    val reviewsToday: Int,
    val correctToday: Int,
    val hardToday: Int,
    val againToday: Int,
    /** Cartões que ainda entram numa sessão hoje (vencidos + novos até o limite). */
    val remainingToday: Int,
    /** Dias seguidos com estudo, terminando hoje (ou ontem, se hoje ainda não houve estudo). */
    val streakDays: Int,
    val totalReviews: Int,
    val totalCorrect: Int,
    val totalAgain: Int,
    /** Últimos dias, do mais antigo até hoje. */
    val days: List<DayStats>,
    /** Cartões já aprendidos (graduados pela primeira vez) ao fim de cada dia de [days]. */
    val learnedByDay: List<Int>,
    val cardsByState: Map<CardState, Int>,
) {
    /** Porcentagem de acertos hoje, ou null se ainda não houve revisão. */
    val accuracyToday: Int?
        get() = if (reviewsToday == 0) null else (correctToday * 100.0 / reviewsToday).roundToInt()

    val totalCards: Int
        get() = cardsByState.values.sum()
}
