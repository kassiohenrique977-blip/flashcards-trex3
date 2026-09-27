package com.flashcards.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flashcards.core.model.CardState
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.model.SyncStatus

// Enums são gravados pelo nome (conversor embutido do Room). As queries usam 'NEW', 'REVIEW' etc.

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncToWatch: Boolean,
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deckId", "dueAt"])],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    /** Tags separadas por espaço (ver [com.flashcards.core.model.Tags]). */
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: CardState,
    val dueAt: Long,
    /** "interval" do SRS, em dias. */
    val intervalDays: Int,
    val easeFactor: Double,
    val repetitions: Int,
    val lapses: Int,
    val learningStep: Int,
    val lastReviewedAt: Long?,
)

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId"), Index("deckId"), Index("reviewedAt")],
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val deckId: String,
    val rating: Rating,
    val reviewedAt: Long,
    val previousInterval: Int,
    val newInterval: Int,
    val previousState: CardState,
    val newState: CardState,
    val durationMs: Long,
    val source: ReviewSource,
    val deviceId: String?,
    val sessionId: String?,
    val algorithm: String,
)

/**
 * Feed de mudanças para a sincronização incremental. Uma linha por entidade,
 * sempre com a última versão. Linhas só são apagadas quando um deck é removido
 * (as dos seus cartões), e a lápide do deck tem versão maior que todas elas,
 * então o MAX(version) nunca diminui.
 */
@Entity(
    tableName = "sync_state",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["version"], unique = true), Index("parentId")],
)
data class SyncStateEntity(
    val entityId: String,
    val entityType: EntityType,
    val version: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val deleted: Boolean,
    val parentId: String?,
)

/** Relógios que já sincronizaram, com o último cursor confirmado. */
@Entity(tableName = "watch_devices")
data class WatchDeviceEntity(
    @PrimaryKey val deviceId: String,
    val lastAckSeq: Long,
    val lastSyncAt: Long,
)
