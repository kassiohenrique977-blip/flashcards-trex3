package com.flashcards.core.srs

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState

/**
 * Algoritmo de repetição espaçada. Implementações devem ser puras e determinísticas:
 * o mesmo estado, resposta e horário sempre produzem o mesmo resultado. Para trocar
 * de algoritmo (por exemplo, FSRS), basta outra implementação desta interface.
 */
interface Scheduler {

    /** Gravado em cada revisão, para saber qual algoritmo calculou o intervalo. */
    val id: String

    fun schedule(current: SchedulingState, rating: Rating, now: Long): SchedulingState
}

object TimeUnits {
    const val MINUTE_MS = 60_000L
    const val DAY_MS = 86_400_000L
}

/** Intervalo em dias que conta como "intervalo de revisão" (zero durante as etapas curtas). */
val SchedulingState.reviewIntervalDays: Int
    get() = if (state == CardState.REVIEW) intervalDays else 0

/**
 * Recalcula o estado reproduzindo as respostas em ordem cronológica, com desempate
 * pelo ID. Assim a ordem em que as revisões chegam (celular ou relógio) não muda o resultado.
 */
fun Scheduler.replay(initial: SchedulingState, reviews: Collection<Review>): SchedulingState =
    reviews
        .sortedWith(compareBy<Review>({ it.reviewedAt }, { it.id }))
        .fold(initial) { state, review -> schedule(state, review.rating, review.reviewedAt) }
