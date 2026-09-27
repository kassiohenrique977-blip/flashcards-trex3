package com.flashcards.app.ui.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.ConfirmDialog
import com.flashcards.app.ui.common.TitleTopBar
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.countLabel
import com.flashcards.app.ui.common.formatRelative
import com.flashcards.app.ui.theme.RatingAgain
import com.flashcards.app.ui.theme.RatingGood

/**
 * Aba "Relógio". Abrir a tela liga a sincronização (foreground service); ela desliga
 * sozinha depois de 10 minutos sem atividade do relógio.
 */
@Composable
fun WatchSyncScreen() {
    val container = appContainer()
    val viewModel: WatchSyncViewModel = viewModel {
        WatchSyncViewModel(container.syncMonitor, container.pairingStore, container.watchRepository, container.syncControl)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmNewCode by viewModel.confirmNewCode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A notificação do serviço precisa desta permissão no Android 13+. Sem ela o serviço
    // funciona do mesmo jeito, só não aparece na barra.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }
    LaunchedEffect(Unit) {
        if (state.status.running) return@LaunchedEffect
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    Scaffold(topBar = { TitleTopBar("Relógio") }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, onStart = viewModel::start, onStop = viewModel::stop)
            CodeCard(state.code, onNewCode = viewModel::askNewCode)
            StepsCard()
            WatchesCard(state, now = container.clock.now())
        }
    }

    if (confirmNewCode) {
        ConfirmDialog(
            title = "Gerar novo código?",
            message = "O relógio só volta a sincronizar depois que você digitar o novo código no Zepp App.",
            confirmLabel = "Gerar",
            onConfirm = viewModel::confirmNewCode,
            onDismiss = viewModel::dismissNewCode,
        )
    }
}

@Composable
private fun StatusCard(state: WatchSyncUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val status = state.status
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (status.running) RatingGood else RatingAgain, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.running) "Sincronização ligada" else "Sincronização desligada",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            val message = status.startError ?: status.message
            if (message != null) {
                Text(
                    message,
                    color = if (status.startError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status.receivedReviews > 0 || status.sentChanges > 0) {
                Text(
                    "Recebidas: ${countLabel(status.receivedReviews, "resposta", "respostas")} · " +
                        "enviadas: ${countLabel(status.sentChanges, "alteração", "alterações")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (status.running) {
                OutlinedButton(onClick = onStop) { Text("Desligar") }
            } else {
                Button(onClick = onStart) { Text("Ligar sincronização") }
            }
        }
    }
}

@Composable
private fun CodeCard(code: String, onNewCode: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Código de pareamento", style = MaterialTheme.typography.titleMedium)
            Text(
                formatPairingCode(code),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TextButton(onClick = onNewCode) { Text("Gerar novo código") }
        }
    }
}

@Composable
private fun StepsCard() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Como sincronizar", style = MaterialTheme.typography.titleMedium)
            Text("1. Instale o Flashcards no T-Rex 3 (veja o README).")
            Text("2. No Zepp App: Perfil › Amazfit T-Rex 3 › Flashcards › Configurações. Digite o código acima (só na primeira vez).")
            Text("3. Com esta tela aberta, toque em SINCRONIZAR no relógio. O Zepp App precisa estar aberto ou em segundo plano.")
        }
    }
}

@Composable
private fun WatchesCard(state: WatchSyncUiState, now: Long) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Relógios", style = MaterialTheme.typography.titleMedium)
            if (state.devices.isEmpty()) {
                Text(
                    "Nenhum relógio sincronizou ainda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.devices.forEach { device ->
                Text("T-Rex 3 · última sincronização ${formatRelative(now, device.lastSyncAt)}")
            }
            Text(
                if (state.pendingChanges == 0) {
                    "O relógio está em dia com o celular."
                } else {
                    "${countLabel(state.pendingChanges, "alteração aguardando", "alterações aguardando")} o relógio."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
