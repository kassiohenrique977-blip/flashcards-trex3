package com.flashcards.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.ValidationException
import com.flashcards.core.repository.DeckRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val decks: List<DeckSummary> = emptyList(),
)

data class NewDeckDialogState(
    val name: String = "",
    val description: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

/**
 * @param nowTicks hora atual, reemitida periodicamente para recalcular os cartões vencidos.
 */
class HomeViewModel(
    private val decks: DeckRepository,
    nowTicks: Flow<Long>,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = nowTicks
        .flatMapLatest { now -> decks.observeDeckSummaries(now) }
        .map { HomeUiState(loading = false, decks = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _dialog = MutableStateFlow<NewDeckDialogState?>(null)
    val dialog: StateFlow<NewDeckDialogState?> = _dialog.asStateFlow()

    fun openNewDeckDialog() {
        _dialog.value = NewDeckDialogState()
    }

    fun dismissNewDeckDialog() {
        _dialog.value = null
    }

    fun onNewDeckNameChange(name: String) {
        _dialog.update { it?.copy(name = name, error = null) }
    }

    fun onNewDeckDescriptionChange(description: String) {
        _dialog.update { it?.copy(description = description, error = null) }
    }

    fun confirmNewDeck() {
        val current = _dialog.value ?: return
        if (current.saving) return
        _dialog.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                decks.createDeck(current.name, current.description)
                _dialog.value = null
            } catch (e: ValidationException) {
                _dialog.update { it?.copy(saving = false, error = e.message) }
            }
        }
    }
}
