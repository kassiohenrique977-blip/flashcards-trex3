package com.flashcards.core.model

enum class EntityType { DECK, CARD }

enum class SyncStatus {
    /** Mudou desde a última confirmação do relógio. */
    PENDING,

    /** O relógio já confirmou ter recebido esta versão. */
    SYNCED,
}

/**
 * Entrada do feed de mudanças. Cada alteração em deck ou cartão recebe uma
 * [version] global e crescente; o relógio pede tudo com `version > cursor`.
 */
data class SyncState(
    val entityId: String,
    val entityType: EntityType,
    val version: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    /** A entidade foi apagada; o relógio deve removê-la. */
    val deleted: Boolean,
    /** Deck dono do cartão (nulo para decks). */
    val parentId: String?,
)
