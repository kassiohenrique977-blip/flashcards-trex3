package com.flashcards.app.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Deck
import com.flashcards.core.model.ValidationException
import com.flashcards.core.repository.CardRepository
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckStats(
    val total: Int = 0,
    val new: Int = 0,
    /** LEARNING e RELEARNING. */
    val learning: Int = 0,
    val review: Int = 0,
    /** Já estudados e vencidos agora. */
    val due: Int = 0,
)

data class DeckDetailUiState(
    val loading: Boolean = true,
    val deck: Deck? = null,
    val cards: List<Card> = emptyList(),
    val stats: DeckStats = DeckStats(),
) {
    /** O deck não existe (foi apagado): a tela deve fechar. */
    val missing: Boolean
        get() = !loading && deck == null
}

data class RenameDialogState(
    val name: String,
    val description: String,
    val error: String? = null,
    val saving: Boolean = false,
)

class DeckDetailViewModel(
    private val deckId: String,
    private val decks: DeckRepository,
    cards: CardRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DeckDetailUiState> =
        combine(decks.observeDeck(deckId), cards.observeCards(deckId)) { deck, list ->
            DeckDetailUiState(loading = false, deck = deck, cards = list, stats = statsOf(list, clock.now()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckDetailUiState())

    private val _rename = MutableStateFlow<RenameDialogState?>(null)
    val rename: StateFlow<RenameDialogState?> = _rename.asStateFlow()

    private val _confirmDelete = MutableStateFlow(false)
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    fun setSyncToWatch(enabled: Boolean) {
        viewModelScope.launch { decks.setSyncToWatch(deckId, enabled) }
    }

    fun startRename() {
        val deck = uiState.value.deck ?: return
        _rename.value = RenameDialogState(deck.name, deck.description)
    }

    fun onRenameNameChange(name: String) {
        _rename.update { it?.copy(name = name, error = null) }
    }

    fun onRenameDescriptionChange(description: String) {
        _rename.update { it?.copy(description = description, error = null) }
    }

    fun dismissRename() {
        _rename.value = null
    }

    fun confirmRename() {
        val current = _rename.value ?: return
        if (current.saving) return
        _rename.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                decks.updateDeck(deckId, current.name, current.description)
                _rename.value = null
            } catch (e: ValidationException) {
                _rename.update { it?.copy(saving = false, error = e.message) }
            }
        }
    }

    fun askDelete() {
        _confirmDelete.value = true
    }

    fun dismissDelete() {
        _confirmDelete.value = false
    }

    /** Depois de apagar, [DeckDetailUiState.missing] fica verdadeiro e a tela fecha. */
    fun delete() {
        _confirmDelete.value = false
        viewModelScope.launch { decks.deleteDeck(deckId) }
    }
}

internal fun statsOf(cards: List<Card>, now: Long) = DeckStats(
    total = cards.size,
    new = cards.count { it.scheduling.state == CardState.NEW },
    learning = cards.count { it.scheduling.state.inSteps },
    review = cards.count { it.scheduling.state == CardState.REVIEW },
    due = cards.count { it.scheduling.state != CardState.NEW && it.scheduling.dueAt <= now },
)
