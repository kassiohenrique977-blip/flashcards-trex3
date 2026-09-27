package com.flashcards.app.data.repository

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.core.model.CardState
import com.flashcards.core.repository.StatsRepository
import com.flashcards.core.stats.ReviewEvent
import com.flashcards.core.stats.ReviewTotals

class RoomStatsRepository(db: FlashcardsDatabase) : StatsRepository {

    private val dao = db.statsDao()

    override suspend fun reviewEvents(deckId: String?, since: Long): List<ReviewEvent> =
        dao.reviewEvents(deckId, since).map {
            ReviewEvent(it.cardId, it.rating, it.reviewedAt, it.previousState, it.newState)
        }

    override suspend fun firstLearnedTimes(deckId: String?): List<Long> = dao.firstLearnedTimes(deckId)

    override suspend fun cardsByState(deckId: String?): Map<CardState, Int> =
        dao.countByState(deckId).associate { it.state to it.count }

    override suspend fun totals(deckId: String?): ReviewTotals =
        dao.totals(deckId).let { ReviewTotals(it.reviews, it.correct, it.again) }
}
