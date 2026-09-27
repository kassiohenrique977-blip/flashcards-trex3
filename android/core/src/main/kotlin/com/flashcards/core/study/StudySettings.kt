package com.flashcards.core.study

/** Preferências de estudo, iguais no celular e no relógio. */
data class StudySettings(
    val newCardsPerDay: Int = 20,
    val maxReviewsPerDay: Int = 200,
    /** Hora local em que o "dia de estudo" vira (4 = revisões da madrugada contam para o dia anterior). */
    val dayCutoffHour: Int = 4,
) {
    init {
        require(newCardsPerDay in 0..MAX_NEW_CARDS_PER_DAY) { "Novos por dia fora do limite." }
        require(maxReviewsPerDay in 0..MAX_REVIEWS_PER_DAY) { "Revisões por dia fora do limite." }
        require(dayCutoffHour in 0..23) { "Hora de virada do dia inválida." }
    }

    companion object {
        const val MAX_NEW_CARDS_PER_DAY = 500
        const val MAX_REVIEWS_PER_DAY = 5_000
    }
}
