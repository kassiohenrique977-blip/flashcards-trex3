package com.flashcards.core.model

data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val scheduling: SchedulingState,
)

/**
 * Estado de repetição espaçada de um cartão.
 *
 * O algoritmo lê e devolve este objeto; o resto do app só o persiste.
 */
data class SchedulingState(
    val state: CardState = CardState.NEW,
    /** Quando o cartão volta a aparecer (epoch millis). Para NEW, define a ordem de introdução. */
    val dueAt: Long = 0L,
    /** Intervalo atual em dias ("interval"). Zero enquanto o cartão está em aprendizado. */
    val intervalDays: Int = 0,
    val easeFactor: Double = DEFAULT_EASE,
    /** Respostas corretas seguidas. */
    val repetitions: Int = 0,
    /** Quantas vezes o cartão foi esquecido depois de aprendido. */
    val lapses: Int = 0,
    /** Índice da etapa atual em LEARNING/RELEARNING. */
    val learningStep: Int = 0,
    val lastReviewedAt: Long? = null,
) {
    companion object {
        const val DEFAULT_EASE = 2.5
    }
}

/** Conteúdo de um cartão ainda não salvo (criação, edição e importação). */
data class CardDraft(
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
)
