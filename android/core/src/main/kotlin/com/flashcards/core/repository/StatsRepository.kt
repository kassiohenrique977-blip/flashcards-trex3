package com.flashcards.core.repository

import com.flashcards.core.model.CardState
import com.flashcards.core.stats.ReviewEvent
import com.flashcards.core.stats.ReviewTotals

/** Consultas agregadas para as estatísticas. [deckId] nulo = todos os decks. */
interface StatsRepository {

    suspend fun reviewEvents(deckId: String?, since: Long): List<ReviewEvent>

    /** Para cada cartão, quando ele chegou a REVIEW pela primeira vez. */
    suspend fun firstLearnedTimes(deckId: String?): List<Long>

    suspend fun cardsByState(deckId: String?): Map<CardState, Int>

    suspend fun totals(deckId: String?): ReviewTotals
}
