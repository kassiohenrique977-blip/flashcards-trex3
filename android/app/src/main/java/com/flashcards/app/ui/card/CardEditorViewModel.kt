package com.flashcards.app.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Tags
import com.flashcards.core.model.ValidationException
import com.flashcards.core.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardEditorUiState(
    val loading: Boolean = false,
    val isEdit: Boolean = false,
    val front: String = "",
    val back: String = "",
    /** Tags como o usuário digitou ("ingles, verbos"). */
    val tags: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    /** Cartões salvos com "Salvar e adicionar outro" nesta tela. */
    val savedCount: Int = 0,
    /** A tela deve fechar. */
    val finished: Boolean = false,
    val confirmDelete: Boolean = false,
)

/** Cria um cartão quando [cardId] é nulo; senão edita o existente. */
class CardEditorViewModel(
    private val deckId: String,
    private val cardId: String?,
    private val cards: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardEditorUiState(loading = cardId != null, isEdit = cardId != null))
    val uiState: StateFlow<CardEditorUiState> = _uiState.asStateFlow()

    init {
        if (cardId != null) {
            viewModelScope.launch {
                val card = cards.getCard(cardId)
                _uiState.update {
                    if (card == null) {
                        it.copy(loading = false, finished = true)
                    } else {
                        it.copy(loading = false, front = card.front, back = card.back, tags = Tags.format(card.tags))
                    }
                }
            }
        }
    }

    fun onFrontChange(value: String) = _uiState.update { it.copy(front = value, error = null) }

    fun onBackChange(value: String) = _uiState.update { it.copy(back = value, error = null) }

    fun onTagsChange(value: String) = _uiState.update { it.copy(tags = value, error = null) }

    /** @param addAnother ao criar, limpa frente e verso (mantendo as tags) para o próximo cartão. */
    fun save(addAnother: Boolean = false) {
        val current = _uiState.value
        if (current.saving || current.loading) return
        _uiState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val draft = CardDraft(current.front, current.back, Tags.parse(current.tags))
            try {
                if (cardId == null) cards.createCard(deckId, draft) else cards.updateCard(cardId, draft)
                _uiState.update {
                    if (addAnother && cardId == null) {
                        it.copy(front = "", back = "", saving = false, savedCount = it.savedCount + 1)
                    } else {
                        it.copy(saving = false, finished = true)
                    }
                }
            } catch (e: ValidationException) {
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun askDelete() = _uiState.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _uiState.update { it.copy(confirmDelete = false) }

    fun delete() {
        val id = cardId ?: return
        _uiState.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            cards.deleteCard(id)
            _uiState.update { it.copy(finished = true) }
        }
    }
}
