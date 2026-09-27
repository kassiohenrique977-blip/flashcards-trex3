package com.flashcards.app.ui.card

import com.flashcards.app.testutil.FakeCardRepository
import com.flashcards.core.model.CardDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardEditorViewModelTest {

    private val cards = FakeCardRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank front shows the validation message`() = runTest {
        val viewModel = CardEditorViewModel("deck-1", null, cards)

        viewModel.onBackChange("Olá")
        viewModel.save()

        assertEquals("Preencha a frente do cartão.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.finished)
        assertTrue(cards.cards.value.isEmpty())
    }

    @Test
    fun `saving a new card closes the editor`() = runTest {
        val viewModel = CardEditorViewModel("deck-1", null, cards)

        viewModel.onFrontChange("Hello")
        viewModel.onBackChange("Olá")
        viewModel.onTagsChange("ingles, saudação")
        viewModel.save()

        assertTrue(viewModel.uiState.value.finished)
        val saved = cards.cards.value.single()
        assertEquals(listOf("ingles", "saudação"), saved.tags)
        assertEquals("deck-1", saved.deckId)
    }

    @Test
    fun `save and add another clears the sides but keeps the tags`() = runTest {
        val viewModel = CardEditorViewModel("deck-1", null, cards)

        viewModel.onFrontChange("Hello")
        viewModel.onBackChange("Olá")
        viewModel.onTagsChange("ingles")
        viewModel.save(addAnother = true)

        with(viewModel.uiState.value) {
            assertEquals("", front)
            assertEquals("", back)
            assertEquals("ingles", tags)
            assertEquals(1, savedCount)
            assertFalse(finished)
        }
    }

    @Test
    fun `edit mode loads and updates the existing card`() = runTest {
        val existing = cards.createCard("deck-1", CardDraft("Hello", "Olá", listOf("ingles")))
        val viewModel = CardEditorViewModel("deck-1", existing.id, cards)

        assertEquals("Hello", viewModel.uiState.value.front)
        assertEquals("ingles", viewModel.uiState.value.tags)
        assertTrue(viewModel.uiState.value.isEdit)

        viewModel.onBackChange("Oi")
        viewModel.save()

        assertEquals("Oi", cards.getCard(existing.id)!!.back)
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `delete removes the card and closes the editor`() = runTest {
        val existing = cards.createCard("deck-1", CardDraft("Hello", "Olá"))
        val viewModel = CardEditorViewModel("deck-1", existing.id, cards)

        viewModel.askDelete()
        viewModel.delete()

        assertTrue(cards.cards.value.isEmpty())
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `editing a card that no longer exists closes the editor`() = runTest {
        val viewModel = CardEditorViewModel("deck-1", "sumiu", cards)

        assertTrue(viewModel.uiState.value.finished)
    }
}
