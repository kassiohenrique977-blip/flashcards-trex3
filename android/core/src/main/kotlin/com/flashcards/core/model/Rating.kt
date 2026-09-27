package com.flashcards.core.model

/**
 * Resposta do usuário a um cartão.
 *
 * [code] é o valor compacto trafegado no protocolo com o relógio (1 a 4).
 */
enum class Rating(val code: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    /** GOOD e EASY contam como acerto nas estatísticas. */
    val isCorrect: Boolean
        get() = this == GOOD || this == EASY

    companion object {
        fun fromCode(code: Int): Rating =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Rating inválido: $code")
    }
}
