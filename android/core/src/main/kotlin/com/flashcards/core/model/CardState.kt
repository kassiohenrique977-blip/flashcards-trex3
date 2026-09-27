package com.flashcards.core.model

/**
 * Estado de um cartão no ciclo de repetição espaçada.
 *
 * [code] é o valor compacto trafegado no protocolo com o relógio.
 */
enum class CardState(val code: Int) {
    NEW(0),
    LEARNING(1),
    REVIEW(2),
    RELEARNING(3);

    /** Cartão em etapas curtas (minutos), que voltam na mesma sessão. */
    val inSteps: Boolean
        get() = this == LEARNING || this == RELEARNING

    companion object {
        fun fromCode(code: Int): CardState =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("CardState inválido: $code")
    }
}
