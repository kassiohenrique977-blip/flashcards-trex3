package com.flashcards.app.ui.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import com.flashcards.core.importer.ImportFormat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.BackTopBar
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.cardCountLabel
import com.flashcards.app.ui.common.countLabel
import com.flashcards.core.importer.ImportPlan

private const val MAX_ISSUES_SHOWN = 10

@Composable
fun ImportScreen(deckId: String?, onBack: () -> Unit, onOpenDeck: (String) -> Unit) {
    val container = appContainer()
    val viewModel: ImportViewModel = viewModel {
        ImportViewModel(deckId, container.deckRepository, container.cardRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reader = container.documentReader
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onDocumentPicked { reader.read(uri) }
    }
    // Arquivo aberto de outro app ("Abrir com" / "Compartilhar"): já entra na prévia.
    val incoming by container.incomingFiles.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val uri = container.incomingFiles.consume() ?: return@LaunchedEffect
        viewModel.onDocumentPicked { reader.read(Uri.parse(uri)) }
    }
    // Aceita qualquer tipo: vários gerenciadores de arquivo não marcam CSV como text/csv.
    val pickFile = { picker.launch(arrayOf("*/*")) }

    Scaffold(topBar = { BackTopBar(title = "Importar cartões", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                ImportStep.PICK -> PickStep(state, onPick = pickFile)
                ImportStep.PREVIEW -> PreviewStep(
                    state = state,
                    onPickAnother = pickFile,
                    onSelectTarget = viewModel::selectTarget,
                    onNewDeckNameChange = viewModel::onNewDeckNameChange,
                    onConfirm = viewModel::confirmImport,
                )
                ImportStep.DONE -> DoneStep(
                    state = state,
                    onOpenDeck = { state.importedDeckId?.let(onOpenDeck) },
                    onImportAnother = viewModel::reset,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PickStep(state: ImportUiState, onPick: () -> Unit) {
    Text(
        "Escolha um arquivo CSV (colunas frente, verso e tags) ou JSON (lista \"cards\" com " +
            "front e back). No CSV, a primeira linha pode ser um cabeçalho.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "front,back,tags\n\"Hello\",\"Olá\",\"ingles\"\n\"Good morning\",\"Bom dia\",\"ingles\"",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
    Text(
        "Separadores aceitos: vírgula, ponto e vírgula ou TAB. Cartões repetidos são ignorados.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ErrorText(state.error)
    if (state.loading) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    } else {
        Button(
            onClick = onPick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Escolher arquivo")
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportUiState,
    onPickAnother: () -> Unit,
    onSelectTarget: (String?) -> Unit,
    onNewDeckNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val plan = state.plan
    Text(state.fileName.orEmpty(), style = MaterialTheme.typography.titleMedium)
    if (plan == null) {
        CircularProgressIndicator()
        return
    }
    PlanSummary(plan, itemLabel = if (state.format == ImportFormat.JSON) "Cartão" else "Linha")

    Text("Destino", style = MaterialTheme.typography.titleMedium)
    Column {
        state.decks.forEach { deck ->
            TargetOption(
                label = deck.name,
                selected = state.targetDeckId == deck.id,
                onSelect = { onSelectTarget(deck.id) },
            )
        }
        TargetOption(label = "Novo deck", selected = state.targetDeckId == null, onSelect = { onSelectTarget(null) })
    }
    if (state.targetDeckId == null) {
        OutlinedTextField(
            value = state.newDeckName,
            onValueChange = onNewDeckNameChange,
            label = { Text("Nome do novo deck") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ErrorText(state.error)
    Button(
        onClick = onConfirm,
        enabled = plan.cards.isNotEmpty() && !state.loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Importar ${cardCountLabel(plan.cards.size)}")
    }
    TextButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) {
        Text("Escolher outro arquivo")
    }
}

@Composable
private fun PlanSummary(plan: ImportPlan, itemLabel: String) {
    Text(
        "${cardCountLabel(plan.cards.size)} para importar",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    if (plan.duplicates > 0) {
        Text(
            "${countLabel(plan.duplicates, "repetido será ignorado", "repetidos serão ignorados")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (plan.issues.isNotEmpty()) {
        Text(
            countLabel(plan.issues.size, "linha com problema:", "linhas com problema:"),
            color = MaterialTheme.colorScheme.error,
        )
        plan.issues.take(MAX_ISSUES_SHOWN).forEach { issue ->
            Text(
                "$itemLabel ${issue.line}: ${issue.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val hidden = plan.issues.size - MAX_ISSUES_SHOWN
        if (hidden > 0) {
            Text("… e mais $hidden", style = MaterialTheme.typography.bodySmall)
        }
    }
    plan.cards.take(3).forEach { card ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(card.front, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    card.back,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DoneStep(state: ImportUiState, onOpenDeck: () -> Unit, onImportAnother: () -> Unit) {
    Text(
        "${cardCountLabel(state.importedCount)} importados",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text("No deck “${state.importedDeckName.orEmpty()}”.", style = MaterialTheme.typography.bodyLarge)
    Button(
        onClick = onOpenDeck,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Abrir deck")
    }
    OutlinedButton(onClick = onImportAnother, modifier = Modifier.fillMaxWidth()) {
        Text("Importar outro arquivo")
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}
