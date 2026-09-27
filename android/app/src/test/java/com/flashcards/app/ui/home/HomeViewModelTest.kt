package com.flashcards.app.ui.home

import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.Validation
import com.flashcards.core.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val repository = FakeDeckRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui state lists the repository decks`() = runTest {
        repository.createDeck("Inglês")
        val viewModel = HomeViewModel(repository, flowOf(0L))

        val state = viewModel.uiState.first { !it.loading }

        assertEquals(listOf("Inglês"), state.decks.map { it.deck.name })
    }

    @Test
    fun `blank name keeps the dialog open with an error`() = runTest {
        val viewModel = HomeViewModel(repository, flowOf(0L))

        viewModel.openNewDeckDialog()
        viewModel.onNewDeckNameChange("   ")
        viewModel.confirmNewDeck()

        val dialog = viewModel.dialog.value
        assertNotNull(dialog)
        assertEquals("Dê um nome ao deck.", dialog!!.error)
        assertFalse(dialog.saving)
        assertEquals(emptyList<DeckSummary>(), repository.summaries.value)
    }

    @Test
    fun `typing after an error clears it`() = runTest {
        val viewModel = HomeViewModel(repository, flowOf(0L))
        viewModel.openNewDeckDialog()
        viewModel.confirmNewDeck()

        viewModel.onNewDeckNameChange("I")

        assertNull(viewModel.dialog.value!!.error)
    }

    @Test
    fun `valid name creates the deck and closes the dialog`() = runTest {
        val viewModel = HomeViewModel(repository, flowOf(0L))

        viewModel.openNewDeckDialog()
        viewModel.onNewDeckNameChange(" Inglês ")
        viewModel.onNewDeckDescriptionChange("Vocabulário")
        viewModel.confirmNewDeck()

        assertNull(viewModel.dialog.value)
        val created = repository.summaries.value.single().deck
        assertEquals("Inglês", created.name)
        assertEquals("Vocabulário", created.description)
    }
}

private class FakeDeckRepository : DeckRepository {

    val summaries = MutableStateFlow<List<DeckSummary>>(emptyList())

    override fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>> = summaries

    override fun observeDeck(deckId: String): Flow<Deck?> =
        summaries.map { list -> list.firstOrNull { it.deck.id == deckId }?.deck }

    override suspend fun getDeck(deckId: String): Deck? =
        summaries.value.firstOrNull { it.deck.id == deckId }?.deck

    override suspend fun createDeck(name: String, description: String): Deck {
        val deck = Deck(
            id = "deck-${summaries.value.size + 1}",
            name = Validation.deckName(name),
            description = Validation.deckDescription(description),
            createdAt = 0,
            updatedAt = 0,
            syncToWatch = true,
        )
        summaries.value = summaries.value + DeckSummary(deck, cardCount = 0, dueCount = 0, newCount = 0)
        return deck
    }

    override suspend fun updateDeck(deckId: String, name: String, description: String): Deck =
        error("não usado neste teste")

    override suspend fun setSyncToWatch(deckId: String, enabled: Boolean) =
        error("não usado neste teste")

    override suspend fun deleteDeck(deckId: String) = error("não usado neste teste")
}
