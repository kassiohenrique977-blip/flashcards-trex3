package com.flashcards.core.sync

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Deck
import com.flashcards.core.model.Rating
import com.flashcards.core.model.Review
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.study.StudySettings

/**
 * Tamanho aproximado máximo de uma página de mudanças. Com textos longos, a página
 * encerra antes do limite de itens, para não criar mensagens BLE enormes.
 */
const val MAX_PAGE_CHARS = 16_000

fun Deck.toWireChange() = WireChange(type = WireChange.DECK, id = id, name = name)

fun deletedDeckChange(deckId: String) = WireChange(type = WireChange.DECK, id = deckId, deleted = true)

fun deletedCardChange(cardId: String, deckId: String?) =
    WireChange(type = WireChange.CARD, id = cardId, deleted = true, deckId = deckId)

fun Card.toWireChange() = WireChange(
    type = WireChange.CARD,
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    state = scheduling.state.code,
    dueAt = scheduling.dueAt,
    intervalDays = scheduling.intervalDays,
    easeFactor = scheduling.easeFactor,
    repetitions = scheduling.repetitions,
    lapses = scheduling.lapses,
    learningStep = scheduling.learningStep,
    lastReviewedAt = scheduling.lastReviewedAt,
)

/** Estimativa de caracteres no JSON: texto do cartão mais as chaves e números. */
fun WireChange.estimatedChars(): Int =
    120 + (name?.length ?: 0) + (front?.length ?: 0) + (back?.length ?: 0)

/**
 * Converte uma resposta do relógio. O [deckId] vem do cartão no celular (a fonte da verdade),
 * não do que o relógio mandou.
 *
 * @throws IllegalArgumentException se códigos ou horário forem inválidos.
 */
fun WireReview.toReview(deckId: String, deviceId: String): Review {
    require(id.isNotBlank()) { "ID vazio" }
    require(reviewedAt > 0) { "Horário inválido" }
    return Review(
        id = id,
        cardId = cardId,
        deckId = deckId,
        rating = Rating.fromCode(rating),
        reviewedAt = reviewedAt,
        previousInterval = previousInterval.coerceAtLeast(0),
        newInterval = newInterval.coerceAtLeast(0),
        previousState = CardState.fromCode(previousState),
        newState = CardState.fromCode(newState),
        durationMs = durationMs.coerceAtLeast(0),
        source = ReviewSource.WATCH,
        deviceId = deviceId,
        sessionId = sessionId,
        algorithm = algorithm,
    )
}

fun SchedulerConfig.toWire() = WireSchedulerConfig(
    learningStepsMinutes = learningStepsMinutes,
    relearningStepsMinutes = relearningStepsMinutes,
    graduatingIntervalDays = graduatingIntervalDays,
    easyIntervalDays = easyIntervalDays,
    startingEase = startingEase,
    minimumEase = minimumEase,
    easyBonus = easyBonus,
    hardMultiplier = hardMultiplier,
    lapseIntervalMultiplier = lapseIntervalMultiplier,
    minimumLapseIntervalDays = minimumLapseIntervalDays,
    maximumIntervalDays = maximumIntervalDays,
)

fun StudySettings.toWire(config: SchedulerConfig) = WireSettings(
    newCardsPerDay = newCardsPerDay,
    maxReviewsPerDay = maxReviewsPerDay,
    dayCutoffHour = dayCutoffHour,
    scheduler = config.toWire(),
)
