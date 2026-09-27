package com.flashcards.app.data.repository

import androidx.room.withTransaction
import com.flashcards.app.data.db.DeckEntity
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.Validation
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDeckRepository(
    private val db: FlashcardsDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) : DeckRepository {

    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val reviewDao = db.reviewDao()
    private val syncStateDao = db.syncStateDao()
    private val changes = ChangeTracker(syncStateDao, clock)

    override fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>> =
        deckDao.observeSummaries(now).map { rows -> rows.map { it.toDomain() } }

    override fun observeDeck(deckId: String): Flow<Deck?> =
        deckDao.observe(deckId).map { it?.toDomain() }

    override suspend fun getDeck(deckId: String): Deck? = deckDao.get(deckId)?.toDomain()

    override suspend fun createDeck(name: String, description: String): Deck {
        val cleanName = Validation.deckName(name)
        val cleanDescription = Validation.deckDescription(description)
        return db.withTransaction {
            val now = clock.now()
            val entity = DeckEntity(
                id = ids.newId(),
                name = cleanName,
                description = cleanDescription,
                createdAt = now,
                updatedAt = now,
                syncToWatch = true,
            )
            deckDao.insert(entity)
            changes.record(EntityType.DECK, entity.id, parentId = null)
            entity.toDomain()
        }
    }

    override suspend fun updateDeck(deckId: String, name: String, description: String): Deck {
        val cleanName = Validation.deckName(name)
        val cleanDescription = Validation.deckDescription(description)
        return db.withTransaction {
            val current = requireDeck(deckId)
            if (current.name == cleanName && current.description == cleanDescription) {
                return@withTransaction current.toDomain()
            }
            val updated = current.copy(
                name = cleanName,
                description = cleanDescription,
                updatedAt = clock.now(),
            )
            deckDao.update(updated)
            changes.record(EntityType.DECK, deckId, parentId = null)
            updated.toDomain()
        }
    }

    override suspend fun setSyncToWatch(deckId: String, enabled: Boolean) {
        db.withTransaction {
            val current = requireDeck(deckId)
            if (current.syncToWatch == enabled) return@withTransaction
            deckDao.update(current.copy(syncToWatch = enabled, updatedAt = clock.now()))
            changes.record(EntityType.DECK, deckId, parentId = null)
            // Ao voltar para o relógio, o deck precisa ser reenviado inteiro.
            if (enabled) {
                changes.recordAll(EntityType.CARD, cardDao.getIdsByDeck(deckId), parentId = deckId)
            }
        }
    }

    override suspend fun deleteDeck(deckId: String) {
        db.withTransaction {
            deckDao.get(deckId) ?: return@withTransaction
            reviewDao.deleteByDeck(deckId)
            cardDao.deleteByDeck(deckId)
            deckDao.delete(deckId)
            // A lápide do deck é gravada antes de limpar as linhas dos cartões,
            // assim ela recebe uma versão maior que todas e o relógio apaga o deck inteiro.
            changes.record(EntityType.DECK, deckId, parentId = null, deleted = true)
            syncStateDao.deleteCardStatesOfDeck(deckId)
        }
    }

    private suspend fun requireDeck(deckId: String): DeckEntity =
        deckDao.get(deckId) ?: throw NoSuchElementException("Deck não encontrado: $deckId")
}
