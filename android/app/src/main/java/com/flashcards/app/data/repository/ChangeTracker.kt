package com.flashcards.app.data.repository

import com.flashcards.app.data.db.SyncStateDao
import com.flashcards.app.data.db.SyncStateEntity
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.SyncStatus
import com.flashcards.core.util.Clock

/**
 * Registra alterações no feed `sync_state` com versões globais crescentes.
 * Sempre chamar dentro da mesma transação que altera a entidade.
 */
internal class ChangeTracker(
    private val dao: SyncStateDao,
    private val clock: Clock,
) {

    suspend fun record(
        type: EntityType,
        id: String,
        parentId: String?,
        deleted: Boolean = false,
    ): Long {
        val version = dao.maxVersion() + 1
        dao.upsert(
            SyncStateEntity(
                entityId = id,
                entityType = type,
                version = version,
                updatedAt = clock.now(),
                syncStatus = SyncStatus.PENDING,
                deleted = deleted,
                parentId = parentId,
            ),
        )
        return version
    }

    /** Versões consecutivas, na ordem de [ids]. Retorna a última. */
    suspend fun recordAll(type: EntityType, ids: List<String>, parentId: String?): Long {
        var version = dao.maxVersion()
        if (ids.isEmpty()) return version
        val now = clock.now()
        dao.upsertAll(
            ids.map { id ->
                SyncStateEntity(
                    entityId = id,
                    entityType = type,
                    version = ++version,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    deleted = false,
                    parentId = parentId,
                )
            },
        )
        return version
    }
}
