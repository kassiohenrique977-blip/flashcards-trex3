package com.flashcards.app.data.db

import androidx.room.Dao
import androidx.room.Query
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating

data class ReviewEventRow(
    val cardId: String,
    val rating: Rating,
    val reviewedAt: Long,
    val previousState: CardState,
    val newState: CardState,
)

data class StateCountRow(val state: CardState, val count: Int)

data class ReviewTotalsRow(val reviews: Int, val correct: Int, val again: Int)

/** Consultas das estatísticas. `:deckId` nulo = todos os decks. */
@Dao
interface StatsDao {

    @Query(
        """
        SELECT cardId, rating, reviewedAt, previousState, newState FROM reviews
        WHERE reviewedAt >= :since AND (:deckId IS NULL OR deckId = :deckId)
        ORDER BY reviewedAt
        """,
    )
    suspend fun reviewEvents(deckId: String?, since: Long): List<ReviewEventRow>

    /** Primeira vez que cada cartão chegou a REVIEW vindo de NEW/LEARNING/RELEARNING. */
    @Query(
        """
        SELECT MIN(reviewedAt) FROM reviews
        WHERE newState = 'REVIEW' AND previousState != 'REVIEW' AND (:deckId IS NULL OR deckId = :deckId)
        GROUP BY cardId
        """,
    )
    suspend fun firstLearnedTimes(deckId: String?): List<Long>

    @Query("SELECT state, COUNT(*) AS count FROM cards WHERE (:deckId IS NULL OR deckId = :deckId) GROUP BY state")
    suspend fun countByState(deckId: String?): List<StateCountRow>

    @Query(
        """
        SELECT COUNT(*) AS reviews,
            COALESCE(SUM(CASE WHEN rating IN ('GOOD', 'EASY') THEN 1 ELSE 0 END), 0) AS correct,
            COALESCE(SUM(CASE WHEN rating = 'AGAIN' THEN 1 ELSE 0 END), 0) AS again
        FROM reviews WHERE (:deckId IS NULL OR deckId = :deckId)
        """,
    )
    suspend fun totals(deckId: String?): ReviewTotalsRow
}
