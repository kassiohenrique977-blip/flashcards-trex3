package com.flashcards.app.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.importer.CardImporter
import com.flashcards.core.importer.CardImporters
import com.flashcards.core.importer.ImportDocument
import com.flashcards.core.importer.ImportException
import com.flashcards.core.importer.ImportFormat
import com.flashcards.core.importer.ImportPlan
import com.flashcards.core.importer.ImportPlanner
import com.flashcards.core.importer.ParsedCard
import com.flashcards.core.model.Deck
import com.flashcards.core.model.Validation
import com.flashcards.core.model.ValidationException
import com.flashcards.core.repository.CardRepository
import com.flashcards.core.repository.DeckRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportStep { PICK, PREVIEW, DONE }

data class ImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val loading: Boolean = false,
    val fileName: String? = null,
    val plan: ImportPlan? = null,
    /** Decks existentes que podem receber os cartões. */
    val decks: List<Deck> = emptyList(),
    /** Deck de destino; nulo = criar um deck novo com [newDeckName]. */
    val targetDeckId: String? = null,
    val newDeckName: String = "",
    /** Vem do arquivo quando o formato traz (JSON). */
    val newDeckDescription: String = "",
    val format: ImportFormat? = null,
    val error: String? = null,
    val importedCount: Int = 0,
    val importedDeckId: String? = null,
    val importedDeckName: String? = null,
)

/**
 * Escolher arquivo → pré-visualizar (válidos, repetidos, linhas com erro) → importar.
 * O formato vem da extensão; o que não é CSV conhecido também é tentado como CSV.
 */
class ImportViewModel(
    initialDeckId: String?,
    private val decks: DeckRepository,
    private val cards: CardRepository,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState(targetDeckId = initialDeckId))
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var parsed: List<ParsedCard> = emptyList()

    init {
        viewModelScope.launch {
            val available = decks.observeDeckSummaries(now = 0).first().map { it.deck }
            _uiState.update { it.copy(decks = available) }
        }
    }

    /** @param load lê o arquivo escolhido (fica fora do ViewModel para não depender de Uri). */
    fun onDocumentPicked(load: suspend () -> ImportDocument) {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val document = load()
                val importer = importerFor(document.name)
                val file = withContext(workDispatcher) { importer.parse(document.bytes) }
                parsed = file.cards
                if (parsed.isEmpty()) throw ImportException("O arquivo não tem cartões.")
                // Nome do deck: o que vier no arquivo; senão, o nome do arquivo.
                val suggestedName = (file.deckName ?: document.name.substringBeforeLast('.').trim())
                    .ifEmpty { "Importado" }
                    .take(Validation.DECK_NAME_MAX)
                _uiState.update {
                    it.copy(
                        step = ImportStep.PREVIEW,
                        fileName = document.name,
                        format = importer.format,
                        newDeckName = suggestedName,
                        newDeckDescription = file.description.orEmpty().take(Validation.DECK_DESCRIPTION_MAX),
                    )
                }
                replan()
                _uiState.update { it.copy(loading = false) }
            } catch (e: ImportException) {
                parsed = emptyList()
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun selectTarget(deckId: String?) {
        _uiState.update { it.copy(targetDeckId = deckId, error = null) }
        viewModelScope.launch { replan() }
    }

    fun onNewDeckNameChange(name: String) {
        _uiState.update { it.copy(newDeckName = name, error = null) }
    }

    fun confirmImport() {
        val current = _uiState.value
        val plan = current.plan ?: return
        if (current.loading || plan.cards.isEmpty()) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val deck = current.targetDeckId?.let { decks.getDeck(it) }
                    ?: decks.createDeck(current.newDeckName, current.newDeckDescription)
                val count = cards.addCards(deck.id, plan.cards)
                _uiState.update {
                    it.copy(
                        step = ImportStep.DONE,
                        loading = false,
                        importedCount = count,
                        importedDeckId = deck.id,
                        importedDeckName = deck.name,
                    )
                }
            } catch (e: ValidationException) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Volta ao início para importar outro arquivo. */
    fun reset() {
        parsed = emptyList()
        viewModelScope.launch {
            val available = decks.observeDeckSummaries(now = 0).first().map { it.deck }
            _uiState.update { ImportUiState(decks = available, targetDeckId = it.targetDeckId) }
        }
    }

    private suspend fun replan() {
        val target = _uiState.value.targetDeckId
        val existingFronts = if (target == null) emptyList() else cards.observeCards(target).first().map { it.front }
        val plan = withContext(workDispatcher) { ImportPlanner.plan(parsed, existingFronts) }
        _uiState.update { it.copy(plan = plan) }
    }

    private fun importerFor(fileName: String): CardImporter {
        val format = ImportFormat.fromFileName(fileName)
        if (format != null && !format.implemented) {
            throw ImportException("Importar ${format.label} ainda não é suportado. Exporte os cartões como CSV.")
        }
        return CardImporters.forFileName(fileName) ?: requireNotNull(CardImporters.forFormat(ImportFormat.CSV))
    }
}
