package com.flashcards.app.ui.importer

import com.flashcards.app.testutil.FakeCardRepository
import com.flashcards.app.testutil.FakeDeckRepository
import com.flashcards.core.importer.ImportDocument
import com.flashcards.core.importer.ImportIssue
import com.flashcards.core.model.CardDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val decks = FakeDeckRepository()
    private val cards = FakeCardRepository()

    private val csv = """
        front,back,tags
        Hello,Olá,ingles
        Good morning,Bom dia,ingles
        Thanks,Obrigado,ingles
        Only front,,ingles
        hello,Oi,ingles
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(deckId: String? = null) =
        ImportViewModel(deckId, decks, cards, workDispatcher = UnconfinedTestDispatcher(testScheduler))

    private fun document(name: String, text: String) = ImportDocument(name, text.toByteArray())

    @Test
    fun `preview counts valid invalid and repeated rows`() = runTest {
        val viewModel = viewModel()

        viewModel.onDocumentPicked { document("ingles.csv", csv) }

        val state = viewModel.uiState.value
        assertEquals(ImportStep.PREVIEW, state.step)
        assertEquals("ingles", state.newDeckName)
        assertNull(state.targetDeckId)
        val plan = state.plan!!
        assertEquals(listOf("Hello", "Good morning", "Thanks"), plan.cards.map { it.front })
        assertEquals(listOf(ImportIssue(5, "Preencha o verso do cartão.")), plan.issues)
        assertEquals(1, plan.duplicates)
    }

    @Test
    fun `importing into a new deck creates it with the file name`() = runTest {
        val viewModel = viewModel()
        viewModel.onDocumentPicked { document("ingles.csv", csv) }

        viewModel.confirmImport()

        val state = viewModel.uiState.value
        assertEquals(ImportStep.DONE, state.step)
        assertEquals(3, state.importedCount)
        val deck = decks.decks.value.single()
        assertEquals("ingles", deck.name)
        assertEquals(deck.id, state.importedDeckId)
        assertEquals(3, cards.cards.value.count { it.deckId == deck.id })
    }

    @Test
    fun `cards already in the target deck are skipped`() = runTest {
        val deck = decks.createDeck("Inglês")
        cards.createCard(deck.id, CardDraft("Thanks", "Valeu"))
        val viewModel = viewModel(deck.id)

        viewModel.onDocumentPicked { document("ingles.csv", csv) }

        val plan = viewModel.uiState.value.plan!!
        assertEquals(listOf("Hello", "Good morning"), plan.cards.map { it.front })
        assertEquals(2, plan.duplicates)
    }

    @Test
    fun `switching the target deck recomputes duplicates`() = runTest {
        val deck = decks.createDeck("Inglês")
        cards.createCard(deck.id, CardDraft("Thanks", "Valeu"))
        val viewModel = viewModel()
        viewModel.onDocumentPicked { document("ingles.csv", csv) }
        assertEquals(1, viewModel.uiState.value.plan!!.duplicates)

        viewModel.selectTarget(deck.id)

        assertEquals(2, viewModel.uiState.value.plan!!.duplicates)
    }

    @Test
    fun `json brings the deck name and description into the new deck`() = runTest {
        val json = """
            { "deck": "Fundamentos de Redes — EC III", "description": "Revisão", "cards": [
              { "id": 1, "topic": "OSI", "front": "PDU da camada de Rede", "back": "Pacote IP" },
              { "id": 2, "topic": "OSI", "front": "PDU da camada Física", "back": "" } ] }
        """.trimIndent()
        val viewModel = viewModel()

        viewModel.onDocumentPicked { document("flashcards_ec3_redes.json", json) }
        val preview = viewModel.uiState.value
        viewModel.confirmImport()

        assertEquals("Fundamentos de Redes — EC III", preview.newDeckName)
        assertEquals(listOf(ImportIssue(2, "Preencha o verso do cartão.")), preview.plan!!.issues)
        val deck = decks.decks.value.single()
        assertEquals("Fundamentos de Redes — EC III", deck.name)
        assertEquals("Revisão", deck.description)
        assertEquals(listOf("OSI"), cards.cards.value.single().tags)
    }

    @Test
    fun `anki packages get a clear message`() = runTest {
        val viewModel = viewModel()

        viewModel.onDocumentPicked { document("deck.apkg", "binário") }

        val state = viewModel.uiState.value
        assertEquals(ImportStep.PICK, state.step)
        assertTrue(state.error!!.contains("ainda não é suportado"))
    }

    @Test
    fun `file without cards shows an error`() = runTest {
        val viewModel = viewModel()

        viewModel.onDocumentPicked { document("vazio.csv", "front,back\n") }

        assertEquals("O arquivo não tem cartões.", viewModel.uiState.value.error)
    }
}
