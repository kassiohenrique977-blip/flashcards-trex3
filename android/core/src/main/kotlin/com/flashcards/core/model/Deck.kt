package com.flashcards.core.model

data class Deck(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Se o deck deve ser enviado ao relógio na próxima sincronização. */
    val syncToWatch: Boolean,
)

/** Deck com as contagens mostradas na Home. */
data class DeckSummary(
    val deck: Deck,
    val cardCount: Int,
    /** Cartões já estudados cuja revisão venceu. */
    val dueCount: Int,
    /** Cartões nunca estudados. */
    val newCount: Int,
)
