package com.flashcards.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.app.ui.common.formatDelay
import com.flashcards.core.model.Card
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.srs.Scheduler
import com.flashcards.core.study.SessionSummary
import com.flashcards.core.study.StudyDay
import com.flashcards.core.study.StudySession
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

sealed interface StudyUiState {
    val deckName: String

    data class Loading(override val deckName: String = "") : StudyUiState

    /** Não há nada para estudar agora. */
    data class Empty(override val deckName: String) : StudyUiState

    data class Question(
        override val deckName: String,
        val position: Int,
        val total: Int,
        val front: String,
        val back: String,
        val revealed: Boolean,
        /** Quando o cartão volta para cada resposta: "10 min", "4 d"... */
        val previews: Map<Rating, String>,
    ) : StudyUiState

    data class Finished(override val deckName: String, val summary: SessionSummary) : StudyUiState
}

/**
 * Sessão de estudo no celular. As respostas são gravadas em [writeScope] (escopo do
 * processo), em ordem, para não se perderem se o usuário sair logo depois de responder.
 */
class StudyViewModel(
    private val deckId: String,
    private val decks: DeckRepository,
    private val study: StudyRepository,
    private val settings: SettingsRepository,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val zone: ZoneId,
    private val writeScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StudyUiState>(StudyUiState.Loading())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var session: StudySession? = null
    private var deckName = ""
    private val writeLock = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    fun reveal() {
        if (_uiState.value is StudyUiState.Question) publish(revealed = true)
    }

    fun answer(rating: Rating) {
        val current = session ?: return
        val question = _uiState.value as? StudyUiState.Question ?: return
        if (!question.revealed) return
        val result = current.answer(rating)
        writeScope.launch {
            writeLock.withLock { study.recordAnswer(result.review, result.card.scheduling) }
        }
        publish(revealed = false)
    }

    private suspend fun load() {
        deckName = decks.getDeck(deckId)?.name.orEmpty()
        val now = clock.now()
        val current = settings.settings.value
        val day = StudyDay.window(now, zone, current.dayCutoffHour)
        val cards = study.loadSession(deckId, now, day, current)
        session = StudySession(cards, scheduler, clock, ids, ReviewSource.PHONE)
        publish(revealed = false)
    }

    private fun publish(revealed: Boolean) {
        val current = session ?: return
        val card = current.current
        _uiState.value = when {
            current.total == 0 -> StudyUiState.Empty(deckName)
            card == null -> StudyUiState.Finished(deckName, current.summary())
            else -> StudyUiState.Question(
                deckName = deckName,
                position = current.position,
                total = current.total,
                front = card.front,
                back = card.back,
                revealed = revealed,
                previews = previews(card),
            )
        }
    }

    private fun previews(card: Card): Map<Rating, String> {
        val now = clock.now()
        return Rating.entries.associateWith { rating ->
            formatDelay(scheduler.schedule(card.scheduling, rating, now).dueAt - now)
        }
    }
}
