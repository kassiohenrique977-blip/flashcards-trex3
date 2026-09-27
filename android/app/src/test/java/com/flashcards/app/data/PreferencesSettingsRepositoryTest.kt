package com.flashcards.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flashcards.app.data.settings.PreferencesSettingsRepository
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesSettingsRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `defaults when nothing was saved`() {
        assertEquals(StudySettings(), PreferencesSettingsRepository(context).settings.value)
    }

    @Test
    fun `updates are applied and survive a new instance`() = runTest {
        val repository = PreferencesSettingsRepository(context)

        repository.update { it.copy(newCardsPerDay = 35, dayCutoffHour = 2) }

        assertEquals(35, repository.settings.value.newCardsPerDay)
        assertEquals(
            StudySettings(newCardsPerDay = 35, dayCutoffHour = 2),
            PreferencesSettingsRepository(context).settings.value,
        )
    }
}
