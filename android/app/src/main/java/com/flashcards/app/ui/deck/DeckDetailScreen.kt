package com.flashcards.app.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.BackTopBar
import com.flashcards.app.ui.common.ConfirmDialog
import com.flashcards.app.ui.common.DeckFormDialog
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.cardStateLabel
import com.flashcards.core.model.Card
import com.flashcards.core.model.Deck

@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onImport: () -> Unit,
) {
    val container = appContainer()
    val viewModel: DeckDetailViewModel = viewModel {
        DeckDetailViewModel(deckId, container.deckRepository, container.cardRepository, container.clock)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rename by viewModel.rename.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()

    LaunchedEffect(state.missing) {
        if (state.missing) onBack()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = state.deck?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::startRename) { Text("Renomear") }
                    TextButton(onClick = viewModel::askDelete) { Text("Excluir") }
                },
            )
        },
    ) { padding ->
        val deck = state.deck
        if (deck == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DeckDetailContent(
                state = state,
                deck = deck,
                onStudy = onStudy,
                onAddCard = onAddCard,
                onEditCard = onEditCard,
                onImport = onImport,
                onSyncToWatchChange = viewModel::setSyncToWatch,
                modifier = Modifier.padding(padding),
            )
        }
    }

    rename?.let { current ->
        DeckFormDialog(
            title = "Renomear deck",
            confirmLabel = "Salvar",
            name = current.name,
            description = current.description,
            error = current.error,
            saving = current.saving,
            onNameChange = viewModel::onRenameNameChange,
            onDescriptionChange = viewModel::onRenameDescriptionChange,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename,
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir deck?",
            message = "O deck “${state.deck?.name.orEmpty()}”, com todos os cartões e revisões, " +
                "será apagado deste celular e do relógio. Isso não pode ser desfeito.",
            confirmLabel = "Excluir",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun DeckDetailContent(
    state: DeckDetailUiState,
    deck: Deck,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onImport: () -> Unit,
    onSyncToWatchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (deck.description.isNotBlank()) {
            item {
                Text(
                    deck.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { StatsRow(state.stats) }
        item {
            Button(
                onClick = onStudy,
                enabled = state.stats.total > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Estudar", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddCard, modifier = Modifier.weight(1f)) { Text("+ Cartão") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Importar CSV") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enviar para o relógio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (deck.syncToWatch) {
                            "Vai para o T-Rex 3 na próxima sincronização"
                        } else {
                            "Fica só no celular"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = deck.syncToWatch, onCheckedChange = onSyncToWatchChange)
            }
        }
        item {
            Text("Cartões (${state.cards.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (state.cards.isEmpty()) {
            item {
                Text(
                    "Nenhum cartão ainda. Adicione um ou importe um arquivo CSV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.cards, key = { it.id }) { card ->
            CardRow(card = card, onClick = { onEditCard(card.id) })
        }
    }
}

@Composable
private fun StatsRow(stats: DeckStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBox("Vencidos", stats.due, Modifier.weight(1f))
        StatBox("Novos", stats.new, Modifier.weight(1f))
        StatBox("Aprendendo", stats.learning, Modifier.weight(1f))
        StatBox("Revisão", stats.review, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                card.front,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.back,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = if (card.tags.isEmpty()) "" else " · " + card.tags.joinToString(" ")
            Text(
                cardStateLabel(card.scheduling.state) + tags,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
