package com.flashcards.app.data.sync

import androidx.room.withTransaction
import com.flashcards.app.data.db.CardEntity
import com.flashcards.app.data.db.DeckEntity
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.db.SyncStateEntity
import com.flashcards.app.data.db.WatchDeviceEntity
import com.flashcards.app.data.repository.ChangeTracker
import com.flashcards.app.data.repository.toDomain
import com.flashcards.app.data.repository.toEntity
import com.flashcards.app.data.repository.withScheduling
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.srs.Scheduler
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.srs.replay
import com.flashcards.core.sync.AckRequest
import com.flashcards.core.sync.ChangesResponse
import com.flashcards.core.sync.HelloResponse
import com.flashcards.core.sync.MAX_PAGE_CHARS
import com.flashcards.core.sync.PROTOCOL_VERSION
import com.flashcards.core.sync.PushRequest
import com.flashcards.core.sync.PushResponse
import com.flashcards.core.sync.RejectedReview
import com.flashcards.core.sync.SyncBackend
import com.flashcards.core.sync.WireChange
import com.flashcards.core.sync.deletedCardChange
import com.flashcards.core.sync.deletedDeckChange
import com.flashcards.core.sync.estimatedChars
import com.flashcards.core.sync.toReview
import com.flashcards.core.sync.toWire
import com.flashcards.core.sync.toWireChange
import com.flashcards.core.util.Clock

/**
 * Lado do celular da sincronização (docs/PROTOCOL.md), sobre o banco Room.
 *
 * - push: grava as respostas pelo ID (reenvio não duplica) e recalcula cada cartão
 *   afetado reproduzindo TODAS as revisões dele em ordem cronológica.
 * - changes: lê o feed `sync_state` a partir do cursor do relógio.
 * - ack: marca o que o relógio confirmou e registra o relógio.
 */
class RoomSyncBackend(
    private val db: FlashcardsDatabase,
    private val scheduler: Scheduler,
    private val schedulerConfig: SchedulerConfig,
    private val settings: SettingsRepository,
    private val serverId: () -> String,
    private val clock: Clock,
) : SyncBackend {

    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val reviewDao = db.reviewDao()
    private val syncStateDao = db.syncStateDao()
    private val watchDeviceDao = db.watchDeviceDao()
    private val changes = ChangeTracker(syncStateDao, clock)

    override suspend fun hello(deviceId: String) = HelloResponse(
        protocol = PROTOCOL_VERSION,
        serverId = serverId(),
        serverTime = clock.now(),
        deviceKnown = watchDeviceDao.get(deviceId) != null,
    )

    override suspend fun push(request: PushRequest): PushResponse = db.withTransaction {
        val known = reviewDao.existingIds(request.reviews.map { it.id }).toHashSet()
        val accepted = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val rejected = mutableListOf<RejectedReview>()
        val affected = LinkedHashMap<String, CardEntity>()

        for (wire in request.reviews) {
            if (wire.id in known) {
                duplicates += wire.id
                continue
            }
            val card = affected[wire.cardId] ?: cardDao.get(wire.cardId)
            if (card == null) {
                rejected += RejectedReview(wire.id, REASON_UNKNOWN_CARD)
                continue
            }
            val review = try {
                wire.toReview(card.deckId, request.deviceId)
            } catch (_: IllegalArgumentException) {
                rejected += RejectedReview(wire.id, REASON_INVALID)
                continue
            }
            reviewDao.insert(review.toEntity())
            known += wire.id
            accepted += wire.id
            affected[card.id] = card
        }

        affected.values.forEach { recompute(it) }
        PushResponse(accepted, duplicates, rejected)
    }

    override suspend fun changes(deviceId: String, since: Long, limit: Int): ChangesResponse =
        db.withTransaction {
            // Um item a mais só para saber se existe próxima página.
            val candidates = syncStateDao.changesSince(since, limit + 1)
            val cards = cardDao.getByIds(
                candidates.filter { it.entityType == EntityType.CARD && !it.deleted }.map { it.entityId },
            ).associateBy { it.id }
            val deckIds = candidates
                .mapNotNull { if (it.entityType == EntityType.DECK) it.entityId else it.parentId }
                .distinct()
            val decks = deckDao.getByIds(deckIds).associateBy { it.id }

            val page = mutableListOf<WireChange>()
            var chars = 0
            var consumed = 0
            var nextSince = since
            for (state in candidates) {
                if (consumed == limit) break
                val change = toWire(state, cards, decks)
                val size = change?.estimatedChars() ?: 0
                // A primeira mudança sempre entra, para a página nunca ficar parada.
                if (consumed > 0 && chars + size > MAX_PAGE_CHARS) break
                consumed++
                nextSince = state.version
                if (change != null) {
                    page += change
                    chars += size
                }
            }

            ChangesResponse(
                changes = page,
                nextSince = nextSince,
                hasMore = consumed < candidates.size,
                settings = settings.settings.value.toWire(schedulerConfig),
            )
        }

    override suspend fun ack(request: AckRequest) {
        db.withTransaction {
            // Um cursor maior que o feed só pode ser erro do relógio: não marca o que não existe.
            val cursor = minOf(request.cursor, syncStateDao.maxVersion())
            syncStateDao.markSyncedUpTo(cursor)
            watchDeviceDao.upsert(WatchDeviceEntity(request.deviceId, lastAckSeq = cursor, lastSyncAt = clock.now()))
        }
    }

    /** Novo estado do cartão = todas as revisões dele reproduzidas desde o estado NEW. */
    private suspend fun recompute(card: CardEntity) {
        val reviews = reviewDao.getForCard(card.id).map { it.toDomain() }
        val state = scheduler.replay(SchedulingState(dueAt = card.createdAt), reviews)
        cardDao.update(card.withScheduling(state))
        changes.record(EntityType.CARD, card.id, parentId = card.deckId)
    }

    /**
     * Mudança para o relógio, ou null se ela não interessa a ele (cartão de um deck
     * que não vai para o relógio). Mesmo ignorada, ela avança o cursor.
     */
    private fun toWire(
        state: SyncStateEntity,
        cards: Map<String, CardEntity>,
        decks: Map<String, DeckEntity>,
    ): WireChange? = when (state.entityType) {
        EntityType.DECK -> {
            val deck = decks[state.entityId]
            if (state.deleted || deck == null || !deck.syncToWatch) {
                deletedDeckChange(state.entityId)
            } else {
                deck.toDomain().toWireChange()
            }
        }
        EntityType.CARD -> {
            val card = cards[state.entityId]
            val deck = state.parentId?.let { decks[it] }
            when {
                state.deleted || card == null -> deletedCardChange(state.entityId, state.parentId)
                deck == null || !deck.syncToWatch -> null
                else -> card.toDomain().toWireChange()
            }
        }
    }

    companion object {
        const val REASON_UNKNOWN_CARD = "UNKNOWN_CARD"
        const val REASON_INVALID = "INVALID"
    }
}
