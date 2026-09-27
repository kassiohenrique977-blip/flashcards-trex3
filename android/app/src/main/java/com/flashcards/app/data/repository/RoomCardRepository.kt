package com.flashcards.app.data.repository

import androidx.room.withTransaction
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.core.model.Card
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.Tags
import com.flashcards.core.model.Validation
import com.flashcards.core.repository.CardRepository
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCardRepository(
    private val db: FlashcardsDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) : CardRepository {

    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val reviewDao = db.reviewDao()
    private val changes = ChangeTracker(db.syncStateDao(), clock)

    override fun observeCards(deckId: String): Flow<List<Card>> =
        cardDao.observeByDeck(deckId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCard(cardId: String): Card? = cardDao.get(cardId)?.toDomain()

    override suspend fun createCard(deckId: String, draft: CardDraft): Card {
        val clean = Validation.card(draft)
        return db.withTransaction {
            requireDeck(deckId)
            val now = clock.now()
            val entity = newCardEntity(ids.newId(), deckId, clean, now, dueAt = now)
            cardDao.insert(entity)
            changes.record(EntityType.CARD, entity.id, parentId = deckId)
            entity.toDomain()
        }
    }

    override suspend fun updateCard(cardId: String, draft: CardDraft): Card {
        val clean = Validation.card(draft)
        return db.withTransaction {
            val current = cardDao.get(cardId)
                ?: throw NoSuchElementException("Cartão não encontrado: $cardId")
            val tags = Tags.format(clean.tags)
            if (current.front == clean.front && current.back == clean.back && current.tags == tags) {
                return@withTransaction current.toDomain()
            }
            val updated = current.copy(
                front = clean.front,
                back = clean.back,
                tags = tags,
                updatedAt = clock.now(),
            )
            cardDao.update(updated)
            changes.record(EntityType.CARD, cardId, parentId = current.deckId)
            updated.toDomain()
        }
    }

    override suspend fun deleteCard(cardId: String) {
        db.withTransaction {
            val current = cardDao.get(cardId) ?: return@withTransaction
            reviewDao.deleteByCard(cardId)
            cardDao.delete(cardId)
            changes.record(EntityType.CARD, cardId, parentId = current.deckId, deleted = true)
        }
    }

    override suspend fun addCards(deckId: String, drafts: List<CardDraft>): Int {
        if (drafts.isEmpty()) return 0
        val clean = drafts.map(Validation::card)
        return db.withTransaction {
            requireDeck(deckId)
            val now = clock.now()
            // dueAt crescente preserva a ordem do arquivo na fila de cartões novos.
            val entities = clean.mapIndexed { index, draft ->
                newCardEntity(ids.newId(), deckId, draft, now, dueAt = now + index)
            }
            cardDao.insertAll(entities)
            changes.recordAll(EntityType.CARD, entities.map { it.id }, parentId = deckId)
            entities.size
        }
    }

    private suspend fun requireDeck(deckId: String) {
        deckDao.get(deckId) ?: throw NoSuchElementException("Deck não encontrado: $deckId")
    }
}
