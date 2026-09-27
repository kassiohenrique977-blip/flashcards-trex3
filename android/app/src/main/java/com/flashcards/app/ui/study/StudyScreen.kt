package com.flashcards.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.BackTopBar
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.cardCountLabel
import com.flashcards.app.ui.common.countLabel
import com.flashcards.app.ui.common.formatDuration
import com.flashcards.app.ui.theme.RatingAgain
import com.flashcards.app.ui.theme.RatingEasy
import com.flashcards.app.ui.theme.RatingGood
import com.flashcards.app.ui.theme.RatingHard
import com.flashcards.core.model.Rating
import com.flashcards.core.study.SessionSummary

@Composable
fun StudyScreen(deckId: String, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: StudyViewModel = viewModel {
        StudyViewModel(
            deckId = deckId,
            decks = container.deckRepository,
            study = container.studyRepository,
            settings = container.settingsRepository,
            scheduler = container.scheduler,
            clock = container.clock,
            ids = container.idGenerator,
            zone = container.zone(),
            writeScope = container.applicationScope,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BackTopBar(
                title = state.deckName,
                onBack = onBack,
                actions = {
                    val question = state as? StudyUiState.Question
                    if (question != null) {
                        Text(
                            "${question.position} / ${question.total}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val current = state) {
                is StudyUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is StudyUiState.Empty -> Centered(
                    title = "Nada para estudar agora",
                    message = "Volte mais tarde ou adicione cartões novos.",
                    onBack = onBack,
                )
                is StudyUiState.Question -> QuestionContent(
                    state = current,
                    onReveal = viewModel::reveal,
                    onAnswer = viewModel::answer,
                )
                is StudyUiState.Finished -> FinishedContent(current.summary, onBack)
            }
        }
    }
}

@Composable
internal fun QuestionContent(
    state: StudyUiState.Question,
    onReveal: () -> Unit,
    onAnswer: (Rating) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ElevatedCard(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // O Box centraliza textos curtos; o Column rola quando o texto não cabe.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (state.revealed) {
                        HorizontalDivider(Modifier.padding(vertical = 24.dp))
                        Text(
                            state.back,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!state.revealed) {
            Button(
                onClick = onReveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text("Mostrar resposta", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Errei", state.previews.getValue(Rating.AGAIN), RatingAgain, Color.White, Modifier.weight(1f)) {
                    onAnswer(Rating.AGAIN)
                }
                RatingButton("Difícil", state.previews.getValue(Rating.HARD), RatingHard, Color.Black, Modifier.weight(1f)) {
                    onAnswer(Rating.HARD)
                }
                RatingButton("Bom", state.previews.getValue(Rating.GOOD), RatingGood, Color.White, Modifier.weight(1f)) {
                    onAnswer(Rating.GOOD)
                }
                RatingButton("Fácil", state.previews.getValue(Rating.EASY), RatingEasy, Color.White, Modifier.weight(1f)) {
                    onAnswer(Rating.EASY)
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    label: String,
    hint: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(hint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun FinishedContent(summary: SessionSummary, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sessão concluída", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        SummaryLine(cardCountLabel(summary.cardsStudied))
        SummaryLine(countLabel(summary.correct, "acerto", "acertos"))
        SummaryLine(countLabel(summary.hard, "difícil", "difíceis"))
        SummaryLine(countLabel(summary.again, "erro", "erros"))
        SummaryLine("Tempo: ${formatDuration(summary.durationMs)}")
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack) { Text("Voltar ao deck") }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Centered(title: String, message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Voltar") }
    }
}
