package com.flashcards.app.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.DeckFormDialog
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.cardCountLabel
import com.flashcards.app.ui.common.dueCountLabel
import com.flashcards.app.ui.common.minuteTicker
import com.flashcards.app.ui.common.newCountLabel
import com.flashcards.app.ui.theme.FlashcardsTheme
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary

/** Tela inicial: é também a lista de decks. */
@Composable
fun HomeScreen(onOpenDeck: (String) -> Unit, onImport: () -> Unit) {
    val container = appContainer()
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.deckRepository, minuteTicker(container.clock))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onOpenDeck = onOpenDeck,
        onNewDeck = viewModel::openNewDeckDialog,
        onImport = onImport,
    )

    dialog?.let { current ->
        DeckFormDialog(
            title = "Novo deck",
            confirmLabel = "Criar",
            name = current.name,
            description = current.description,
            error = current.error,
            saving = current.saving,
            onNameChange = viewModel::onNewDeckNameChange,
            onDescriptionChange = viewModel::onNewDeckDescriptionChange,
            onConfirm = viewModel::confirmNewDeck,
            onDismiss = viewModel::dismissNewDeckDialog,
        )
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenDeck: (String) -> Unit,
    onNewDeck: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Meus decks",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onImport) { Text("Importar CSV") }
                    }
                }
                if (state.decks.isEmpty()) {
                    item {
                        Text(
                            text = "Você ainda não tem decks. Toque em “+ Novo deck” ou importe um arquivo CSV.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.decks, key = { it.deck.id }) { summary ->
                    DeckRow(summary = summary, onClick = { onOpenDeck(summary.deck.id) })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewDeck,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text("+ Novo deck")
        }
    }
}

@Composable
private fun DeckRow(summary: DeckSummary, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.deck.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = cardCountLabel(summary.cardCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${dueCountLabel(summary.dueCount)} · ${newCountLabel(summary.newCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    fun summary(id: String, name: String, cards: Int, due: Int, new: Int) = DeckSummary(
        deck = Deck(id, name, "", 0, 0, syncToWatch = true),
        cardCount = cards,
        dueCount = due,
        newCount = new,
    )
    FlashcardsTheme {
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
}
