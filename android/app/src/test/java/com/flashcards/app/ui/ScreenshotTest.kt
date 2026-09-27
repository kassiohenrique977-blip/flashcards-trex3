package com.flashcards.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.flashcards.app.ui.home.HomeContent
import com.flashcards.app.ui.home.HomeUiState
import com.flashcards.app.ui.stats.StatsContent
import com.flashcards.app.ui.stats.StatsUiState
import com.flashcards.app.ui.study.QuestionContent
import com.flashcards.app.ui.study.StudyUiState
import com.flashcards.app.ui.theme.FlashcardsTheme
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.Rating
import com.flashcards.core.stats.DayStats
import com.flashcards.core.stats.Statistics
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset

/**
 * Renderiza telas com dados de exemplo e grava PNGs em app/build/screenshots, para
 * conferir o layout (sobreposição de rótulos, geometria dos gráficos) sem emulador.
 *
 * A view raiz é desenhada direto num Bitmap: o captureToImage() do Compose espera um
 * redesenho da janela que o Robolectric não entrega.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statsLight() = capture("stats-light") { Stats(showTable = true) }

    @Test
    @Config(qualifiers = "+night")
    fun statsDark() = capture("stats-dark") { Stats(showTable = false) }

    @Test
    @Config(qualifiers = "w400dp-h800dp-xhdpi")
    fun home() = capture("home") {
        HomeContent(
            state = HomeUiState(
                loading = false,
                decks = listOf(
                    summary("1", "Inglês", 128, 12, 20),
                    summary("2", "Programação", 240, 25, 0),
                    summary("3", "História", 80, 8, 1),
                ),
            ),
            onOpenDeck = {},
            onNewDeck = {},
            onImport = {},
        )
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-xhdpi")
    fun studyAnswer() = capture("study-answer") {
        QuestionContent(
            state = StudyUiState.Question(
                deckName = "Inglês",
                position = 12,
                total = 30,
                front = "What does \"although\" mean?",
                back = "embora / apesar",
                revealed = true,
                previews = mapOf(Rating.AGAIN to "10 min", Rating.HARD to "12 d", Rating.GOOD to "25 d", Rating.EASY to "1,1 m"),
            ),
            onReveal = {},
            onAnswer = {},
        )
    }

    @Composable
    private fun Stats(showTable: Boolean) {
        StatsContent(
            state = StatsUiState(
                stats = sampleStats(),
                refreshing = false,
                decks = listOf(deck("1", "Inglês"), deck("2", "Programação"), deck("3", "História")),
                selectedDay = null,
                showTable = showTable,
            ),
            zone = ZoneOffset.UTC,
            onSelectDeck = {},
            onSelectDay = {},
            onToggleTable = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            FlashcardsTheme {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun deck(id: String, name: String) = Deck(id, name, "", 0, 0, syncToWatch = true)

    private fun summary(id: String, name: String, cards: Int, due: Int, new: Int) =
        DeckSummary(deck(id, name), cardCount = cards, dueCount = due, newCount = new)

    private fun sampleStats(): Statistics {
        val day = 86_400_000L
        val start = 1_757_808_000_000L - 29 * day
        // Padrão realista: dias sem estudo, fins de semana mais fracos, alguns dias fortes.
        val reviews = listOf(0, 12, 18, 25, 0, 9, 30, 22, 15, 0, 0, 28, 35, 19, 24, 11, 0, 26, 31, 40, 18, 0, 14, 22, 27, 33, 20, 16, 29, 23)
        val days = reviews.mapIndexed { i, total ->
            val again = total / 8
            val hard = total / 6
            DayStats(start + i * day, total, correct = total - again - hard, hard = hard, again = again)
        }
        var learned = 40
        val learnedByDay = reviews.map { total -> (learned + total / 6).also { learned = it } }
        return Statistics(
            studiedToday = 18,
            reviewsToday = 23,
            // Iguais à última coluna (23 revisões: 18 acertos, 3 difíceis, 2 erros).
            correctToday = 18,
            hardToday = 3,
            againToday = 2,
            remainingToday = 12,
            streakDays = 7,
            totalReviews = 1_284,
            totalCorrect = 1_002,
            totalAgain = 141,
            days = days,
            learnedByDay = learnedByDay,
            cardsByState = mapOf(CardState.NEW to 120, CardState.LEARNING to 8, CardState.RELEARNING to 3, CardState.REVIEW to 64),
        )
    }
}
