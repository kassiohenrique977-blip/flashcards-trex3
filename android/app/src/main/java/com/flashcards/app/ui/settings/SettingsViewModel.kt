package com.flashcards.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<StudySettings> = repository.settings

    fun changeNewCards(delta: Int) = update {
        it.copy(newCardsPerDay = (it.newCardsPerDay + delta).coerceIn(0, StudySettings.MAX_NEW_CARDS_PER_DAY))
    }

    fun changeMaxReviews(delta: Int) = update {
        it.copy(maxReviewsPerDay = (it.maxReviewsPerDay + delta).coerceIn(0, StudySettings.MAX_REVIEWS_PER_DAY))
    }

    /** A hora dá a volta: depois de 23h vem 0h. */
    fun changeDayCutoff(delta: Int) = update {
        it.copy(dayCutoffHour = (it.dayCutoffHour + delta).mod(24))
    }

    private fun update(transform: (StudySettings) -> StudySettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
