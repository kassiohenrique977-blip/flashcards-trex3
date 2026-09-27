package com.flashcards.core.model

enum class ReviewSource { PHONE, WATCH }

/**
 * Uma resposta registrada. O [id] é gerado no aparelho que respondeu e serve de
 * chave de idempotência na sincronização: a mesma revisão nunca é contada duas vezes.
 */
data class Review(
    val id: String,
    val cardId: String,
    val deckId: String,
    val rating: Rating,
    val reviewedAt: Long,
    /** Intervalo em dias antes da resposta. */
    val previousInterval: Int,
    /** Intervalo em dias depois da resposta. */
    val newInterval: Int,
    val previousState: CardState,
    val newState: CardState,
    val durationMs: Long,
    val source: ReviewSource,
    val deviceId: String?,
    val sessionId: String?,
    /** Identificador do algoritmo que calculou [newInterval] (por exemplo, "sm2-v1"). */
    val algorithm: String,
)
