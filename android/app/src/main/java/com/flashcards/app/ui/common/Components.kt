package com.flashcards.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcards.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar")
            }
        },
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, actions = actions)
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Formulário de nome e descrição, usado para criar e para renomear decks. */
@Composable
fun DeckFormDialog(
    title: String,
    confirmLabel: String,
    name: String,
    description: String,
    error: String?,
    saving: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nome") },
                    singleLine = true,
                    isError = error != null,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Descrição (opcional)") },
                    minLines = 2,
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Linha com rótulo e botões − / + para ajustar um número. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalIconButton(onClick = onDecrease) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = value,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton(onClick = onIncrease) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
