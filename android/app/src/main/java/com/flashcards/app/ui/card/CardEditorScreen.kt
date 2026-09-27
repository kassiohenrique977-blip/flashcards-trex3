package com.flashcards.app.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.BackTopBar
import com.flashcards.app.ui.common.ConfirmDialog
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.countLabel

@Composable
fun CardEditorScreen(deckId: String, cardId: String?, onDone: () -> Unit) {
    val container = appContainer()
    val viewModel: CardEditorViewModel = viewModel {
        CardEditorViewModel(deckId, cardId, container.cardRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = if (state.isEdit) "Editar cartão" else "Novo cartão",
                onBack = onDone,
                actions = {
                    if (state.isEdit) TextButton(onClick = viewModel::askDelete) { Text("Excluir") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.front,
                onValueChange = viewModel::onFrontChange,
                label = { Text("Frente") },
                placeholder = { Text("What does \"although\" mean?") },
                minLines = 3,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.back,
                onValueChange = viewModel::onBackChange,
                label = { Text("Verso") },
                placeholder = { Text("embora / apesar") },
                minLines = 3,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tags,
                onValueChange = viewModel::onTagsChange,
                label = { Text("Tags") },
                supportingText = { Text("Separe por espaço ou vírgula") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            val error = state.error
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (state.savedCount > 0) {
                Text(
                    "${countLabel(state.savedCount, "cartão salvo", "cartões salvos")} ✓",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving && !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Salvar")
            }
            if (!state.isEdit) {
                OutlinedButton(
                    onClick = { viewModel.save(addAnother = true) },
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Salvar e adicionar outro")
                }
            }
        }
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Excluir cartão?",
            message = "O cartão e o histórico de revisões dele serão apagados.",
            confirmLabel = "Excluir",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}
