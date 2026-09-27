package com.flashcards.core.stats

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.Rating
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.StatsRepository
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.study.StudyDayWindow
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class StatisticsLoaderTest {

    private val now = 1_700_000_000_000L

    private val stats = object : StatsRepository {
        val deckFilters = mutableListOf<String?>()
        override suspend fun reviewEvents(deckId: String?, since: Long): List<ReviewEvent> {
            deckFilters += deckId
            return listOf(ReviewEvent("c1", Rating.GOOD, now, CardState.NEW, CardState.LEARNING))
        }
        override suspend fun firstLearnedTimes(deckId: String?) = emptyList<Long>()
        override suspend fun cardsByState(deckId: String?) = mapOf(CardState.NEW to 3)
        override suspend fun totals(deckId: String?) = ReviewTotals(reviews = 10, correct = 8, again = 1)
    }

    private val decks = object : DeckRepository {
        override fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>> = flowOf(
            listOf("a", "b").map { DeckSummary(Deck(it, it, "", 0, 0, true), 0, 0, 0) },
        )
        override fun observeDeck(deckId: String) = error("não usado")
        override suspend fun getDeck(deckId: String) = error("não usado")
        override suspend fun createDeck(name: String, description: String) = error("não usado")
        override suspend fun updateDeck(deckId: String, name: String, description: String) = error("não usado")
        override suspend fun setSyncToWatch(deckId: String, enabled: Boolean) = error("não usado")
        override suspend fun deleteDeck(deckId: String) = error("não usado")
    }

    private val study = object : StudyRepository {
        override suspend fun loadSession(deckId: String, now: Long, day: StudyDayWindow, settings: StudySettings): List<Card> =
            List(if (deckId == "a") 2 else 5) { Card("$deckId$it", deckId, "f", "b", emptyList(), 0, 0, SchedulingState()) }
        override suspend fun recordAnswer(review: Review, scheduling: SchedulingState) = error("não usado")
    }

    private val loader = StatisticsLoader(stats, decks, study)

    @Test
    fun `all decks add up the remaining cards`() = runTest {
        val result = loader.load(deckId = null, now, ZoneOffset.UTC, StudySettings())

        assertEquals(7, result.remainingToday)
        assertEquals(10, result.totalReviews)
        assertEquals(1, result.reviewsToday)
        assertEquals(3, result.cardsByState[CardState.NEW])
        assertEquals(listOf<String?>(null), stats.deckFilters)
    }

    @Test
    fun `one deck filters every query`() = runTest {
        val result = loader.load(deckId = "b", now, ZoneOffset.UTC, StudySettings())

        assertEquals(5, result.remainingToday)
        assertEquals(listOf<String?>("b"), stats.deckFilters)
    }
}
