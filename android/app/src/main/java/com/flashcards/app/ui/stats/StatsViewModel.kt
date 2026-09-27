package com.flashcards.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.model.Deck
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.stats.Statistics
import com.flashcards.core.stats.StatisticsLoader
import com.flashcards.core.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class StatsUiState(
    /** Nulo só antes do primeiro carregamento; depois o valor anterior fica na tela enquanto recarrega. */
    val stats: Statistics? = null,
    val refreshing: Boolean = true,
    val decks: List<Deck> = emptyList(),
    /** Nulo = todos os decks. */
    val deckId: String? = null,
    /** Dia tocado nos gráficos (índice em [Statistics.days]); nulo = hoje. */
    val selectedDay: Int? = null,
    val showTable: Boolean = false,
)

class StatsViewModel(
    private val loader: StatisticsLoader,
    decks: DeckRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            decks.observeDeckSummaries(now = 0).collect { summaries ->
                val available = summaries.map { it.deck }
                _uiState.update { state ->
                    // Deck filtrado foi apagado: volta para "todos".
                    val deckId = state.deckId?.takeIf { id -> available.any { it.id == id } }
                    state.copy(decks = available, deckId = deckId)
                }
            }
        }
    }

    fun refresh() {
        val deckId = _uiState.value.deckId
        loading?.cancel()
        _uiState.update { it.copy(refreshing = true) }
        loading = viewModelScope.launch {
            val stats = loader.load(deckId, clock.now(), zone(), settings.settings.value)
            _uiState.update { it.copy(stats = stats, refreshing = false) }
        }
    }

    fun selectDeck(deckId: String?) {
        if (deckId == _uiState.value.deckId) return
        _uiState.update { it.copy(deckId = deckId, selectedDay = null) }
        refresh()
    }

    /** Tocar de novo no mesmo dia volta para hoje. */
    fun selectDay(index: Int) {
        _uiState.update { it.copy(selectedDay = if (it.selectedDay == index) null else index) }
    }

    fun toggleTable() {
        _uiState.update { it.copy(showTable = !it.showTable) }
    }
}
