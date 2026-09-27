package com.flashcards.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext

/** Configurações de estudo em SharedPreferences: poucos valores, lidos uma vez por processo. */
class PreferencesSettingsRepository(context: Context) : SettingsRepository {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<StudySettings> = state.asStateFlow()

    override suspend fun update(transform: (StudySettings) -> StudySettings) {
        state.updateAndGet(transform)
        withContext(Dispatchers.IO) {
            // Grava sempre o valor mais recente, mesmo que outra atualização tenha vindo depois.
            val latest = state.value
            prefs.edit {
                putInt(KEY_NEW_CARDS, latest.newCardsPerDay)
                putInt(KEY_MAX_REVIEWS, latest.maxReviewsPerDay)
                putInt(KEY_DAY_CUTOFF, latest.dayCutoffHour)
            }
        }
    }

    private fun read(): StudySettings {
        val defaults = StudySettings()
        return runCatching {
            StudySettings(
                newCardsPerDay = prefs.getInt(KEY_NEW_CARDS, defaults.newCardsPerDay),
                maxReviewsPerDay = prefs.getInt(KEY_MAX_REVIEWS, defaults.maxReviewsPerDay),
                dayCutoffHour = prefs.getInt(KEY_DAY_CUTOFF, defaults.dayCutoffHour),
            )
        }.getOrDefault(defaults)
    }

    private companion object {
        const val FILE = "study_settings"
        const val KEY_NEW_CARDS = "new_cards_per_day"
        const val KEY_MAX_REVIEWS = "max_reviews_per_day"
        const val KEY_DAY_CUTOFF = "day_cutoff_hour"
    }
}
