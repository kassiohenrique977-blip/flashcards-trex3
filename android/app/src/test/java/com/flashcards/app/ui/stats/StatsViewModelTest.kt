package com.flashcards.app.ui.stats

import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.FakeDeckRepository
import com.flashcards.app.testutil.FakeSettingsRepository
import com.flashcards.app.testutil.FakeStudyRepository
import com.flashcards.core.model.CardState
import com.flashcards.core.repository.StatsRepository
import com.flashcards.core.stats.ReviewEvent
import com.flashcards.core.stats.ReviewTotals
import com.flashcards.core.stats.StatisticsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val stats = RecordingStatsRepository()
    private val decks = FakeDeckRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StatsViewModel(
        loader = StatisticsLoader(stats, decks, FakeStudyRepository(emptyList())),
        decks = decks,
        settings = FakeSettingsRepository(),
        clock = FakeClock(1_700_000_000_000L),
        zone = { ZoneOffset.UTC },
    )

    @Test
    fun `refresh loads statistics for every deck`() = runTest {
        val viewModel = viewModel()

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertFalse(state.refreshing)
        assertEquals(42, state.stats!!.totalReviews)
        assertEquals(30, state.stats.days.size)
        assertEquals(listOf<String?>(null), stats.filters)
    }

    @Test
    fun `choosing a deck reloads with that filter and keeps the old numbers meanwhile`() = runTest {
        val deck = decks.createDeck("Inglês")
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.selectDeck(deck.id)

        assertEquals(deck.id, viewModel.uiState.value.deckId)
        assertEquals(listOf(null, deck.id), stats.filters)
        assertEquals(listOf("Inglês"), viewModel.uiState.value.decks.map { it.name })
    }

    @Test
    fun `deleted filtered deck falls back to all decks`() = runTest {
        val deck = decks.createDeck("Inglês")
        val viewModel = viewModel()
        viewModel.selectDeck(deck.id)

        decks.deleteDeck(deck.id)

        assertNull(viewModel.uiState.value.deckId)
    }

    @Test
    fun `tapping a day selects it and tapping again goes back to today`() {
        val viewModel = viewModel()

        viewModel.selectDay(5)
        assertEquals(5, viewModel.uiState.value.selectedDay)
        viewModel.selectDay(5)
        assertNull(viewModel.uiState.value.selectedDay)
    }

    @Test
    fun `table view toggles`() {
        val viewModel = viewModel()

        viewModel.toggleTable()
        assertTrue(viewModel.uiState.value.showTable)
        viewModel.toggleTable()
        assertFalse(viewModel.uiState.value.showTable)
    }

    @Test
    fun `axis tops are round numbers`() {
        assertEquals(1, niceCeil(0))
        assertEquals(1, niceCeil(1))
        assertEquals(2, niceCeil(2))
        assertEquals(5, niceCeil(3))
        assertEquals(10, niceCeil(7))
        assertEquals(20, niceCeil(12))
        assertEquals(50, niceCeil(21))
        assertEquals(100, niceCeil(100))
        assertEquals(200, niceCeil(101))
    }
}

private class RecordingStatsRepository : StatsRepository {
    val filters = mutableListOf<String?>()

    override suspend fun reviewEvents(deckId: String?, since: Long): List<ReviewEvent> {
        filters += deckId
        return emptyList()
    }

    override suspend fun firstLearnedTimes(deckId: String?) = emptyList<Long>()

    override suspend fun cardsByState(deckId: String?) = mapOf(CardState.NEW to 1)

    override suspend fun totals(deckId: String?) = ReviewTotals(reviews = 42, correct = 30, again = 5)
}
