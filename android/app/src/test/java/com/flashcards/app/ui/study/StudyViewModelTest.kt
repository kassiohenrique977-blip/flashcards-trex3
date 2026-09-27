package com.flashcards.app.ui.study

import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.FakeDeckRepository
import com.flashcards.app.testutil.FakeSettingsRepository
import com.flashcards.app.testutil.FakeStudyRepository
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.Sm2Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    private val now = 1_700_000_000_000L
    private val clock = FakeClock(now)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun reviewCard(id: String) = Card(
        id = id,
        deckId = "deck-1",
        front = "front $id",
        back = "back $id",
        tags = emptyList(),
        createdAt = 0,
        updatedAt = 0,
        scheduling = SchedulingState(state = CardState.REVIEW, dueAt = now, intervalDays = 10, repetitions = 3),
    )

    private suspend fun TestScope.viewModel(study: FakeStudyRepository, writeScope: CoroutineScope = this): StudyViewModel {
        val decks = FakeDeckRepository().apply { createDeck("Inglês") } // deck-1
        return StudyViewModel(
            deckId = "deck-1",
            decks = decks,
            study = study,
            settings = FakeSettingsRepository(),
            scheduler = Sm2Scheduler(),
            clock = clock,
            ids = SequentialIds(),
            zone = ZoneOffset.UTC,
            writeScope = writeScope,
        )
    }

    @Test
    fun `first card is shown with the interval each answer would give`() = runTest {
        val viewModel = viewModel(FakeStudyRepository(listOf(reviewCard("c1"), reviewCard("c2"))))

        val state = viewModel.uiState.value as StudyUiState.Question

        assertEquals("Inglês", state.deckName)
        assertEquals(1, state.position)
        assertEquals(2, state.total)
        assertEquals("front c1", state.front)
        assertFalse(state.revealed)
        // Mesmos números do vetor review_*_on_time: 10 min, 12 d, 25 d, 33 d.
        assertEquals(
            mapOf(Rating.AGAIN to "10 min", Rating.HARD to "12 d", Rating.GOOD to "25 d", Rating.EASY to "1,1 m"),
            state.previews,
        )
    }

    @Test
    fun `answering before revealing the back is ignored`() = runTest {
        val study = FakeStudyRepository(listOf(reviewCard("c1")))
        val viewModel = viewModel(study)

        viewModel.answer(Rating.GOOD)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StudyUiState.Question)
        assertTrue(study.recorded.isEmpty())
    }

    @Test
    fun `answers are recorded in order and the session ends with a summary`() = runTest {
        val study = FakeStudyRepository(listOf(reviewCard("c1"), reviewCard("c2")))
        val viewModel = viewModel(study)

        viewModel.reveal()
        viewModel.answer(Rating.GOOD) // c1 concluído
        viewModel.reveal()
        viewModel.answer(Rating.AGAIN) // c2 volta
        assertEquals("front c2", (viewModel.uiState.value as StudyUiState.Question).front)
        viewModel.reveal()
        viewModel.answer(Rating.GOOD)
        advanceUntilIdle()

        assertEquals(listOf(Rating.GOOD, Rating.AGAIN, Rating.GOOD), study.recorded.map { it.first.rating })
        val finished = viewModel.uiState.value as StudyUiState.Finished
        assertEquals(2, finished.summary.cardsStudied)
        assertEquals(2, finished.summary.correct)
        assertEquals(1, finished.summary.again)
    }

    @Test
    fun `nothing due shows the empty state`() = runTest {
        val viewModel = viewModel(FakeStudyRepository(emptyList()))

        assertEquals(StudyUiState.Empty("Inglês"), viewModel.uiState.value)
    }
}
