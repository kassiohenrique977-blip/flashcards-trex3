package com.flashcards.core.repository

import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val settings: StateFlow<StudySettings>

    /** Aplica [transform] ao valor atual de forma atômica e persiste o resultado. */
    suspend fun update(transform: (StudySettings) -> StudySettings)
}
