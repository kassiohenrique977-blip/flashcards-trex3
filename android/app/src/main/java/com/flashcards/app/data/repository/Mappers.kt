package com.flashcards.app.data.repository

import com.flashcards.app.data.db.CardEntity
import com.flashcards.app.data.db.DeckEntity
import com.flashcards.app.data.db.DeckSummaryRow
import com.flashcards.app.data.db.ReviewEntity
import com.flashcards.app.data.db.SyncStateEntity
import com.flashcards.core.model.Card
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.model.SyncState
import com.flashcards.core.model.Tags

internal fun DeckEntity.toDomain() = Deck(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncToWatch = syncToWatch,
)

internal fun DeckSummaryRow.toDomain() = DeckSummary(
    deck = deck.toDomain(),
    cardCount = cardCount,
    dueCount = dueCount,
    newCount = newCount,
)

internal fun CardEntity.scheduling() = SchedulingState(
    state = state,
    dueAt = dueAt,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    repetitions = repetitions,
    lapses = lapses,
    learningStep = learningStep,
    lastReviewedAt = lastReviewedAt,
)

internal fun CardEntity.withScheduling(s: SchedulingState) = copy(
    state = s.state,
    dueAt = s.dueAt,
    intervalDays = s.intervalDays,
    easeFactor = s.easeFactor,
    repetitions = s.repetitions,
    lapses = s.lapses,
    learningStep = s.learningStep,
    lastReviewedAt = s.lastReviewedAt,
)

internal fun CardEntity.toDomain() = Card(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    tags = Tags.parse(tags),
    createdAt = createdAt,
    updatedAt = updatedAt,
    scheduling = scheduling(),
)

/** Cartão recém-criado: estado NEW; [dueAt] define a ordem em que ele será apresentado. */
internal fun newCardEntity(id: String, deckId: String, draft: CardDraft, now: Long, dueAt: Long) =
    CardEntity(
        id = id,
        deckId = deckId,
        front = draft.front,
        back = draft.back,
        tags = Tags.format(draft.tags),
        createdAt = now,
        updatedAt = now,
        state = SchedulingState().state,
        dueAt = dueAt,
        intervalDays = 0,
        easeFactor = SchedulingState.DEFAULT_EASE,
        repetitions = 0,
        lapses = 0,
        learningStep = 0,
        lastReviewedAt = null,
    )

internal fun ReviewEntity.toDomain() = Review(
    id = id,
    cardId = cardId,
    deckId = deckId,
    rating = rating,
    reviewedAt = reviewedAt,
    previousInterval = previousInterval,
    newInterval = newInterval,
    previousState = previousState,
    newState = newState,
    durationMs = durationMs,
    source = source,
    deviceId = deviceId,
    sessionId = sessionId,
    algorithm = algorithm,
)

internal fun Review.toEntity() = ReviewEntity(
    id = id,
    cardId = cardId,
    deckId = deckId,
    rating = rating,
    reviewedAt = reviewedAt,
    previousInterval = previousInterval,
    newInterval = newInterval,
    previousState = previousState,
    newState = newState,
    durationMs = durationMs,
    source = source,
    deviceId = deviceId,
    sessionId = sessionId,
    algorithm = algorithm,
)

internal fun SyncStateEntity.toDomain() = SyncState(
    entityId = entityId,
    entityType = entityType,
    version = version,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deleted = deleted,
    parentId = parentId,
)
