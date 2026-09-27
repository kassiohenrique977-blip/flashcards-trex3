package com.flashcards.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.BuildConfig
import com.flashcards.app.ui.common.StepperRow
import com.flashcards.app.ui.common.TitleTopBar
import com.flashcards.app.ui.common.appContainer

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(topBar = { TitleTopBar("Configurações") }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Estudo")
            StepperRow(
                label = "Cartões novos por dia",
                value = "${settings.newCardsPerDay}",
                supporting = "Cartões nunca vistos que entram a cada dia",
                onDecrease = { viewModel.changeNewCards(-5) },
                onIncrease = { viewModel.changeNewCards(+5) },
            )
            StepperRow(
                label = "Revisões por dia",
                value = "${settings.maxReviewsPerDay}",
                supporting = "Limite de cartões já aprendidos por dia",
                onDecrease = { viewModel.changeMaxReviews(-50) },
                onIncrease = { viewModel.changeMaxReviews(+50) },
            )
            StepperRow(
                label = "Novo dia começa às",
                value = "${settings.dayCutoffHour}h",
                supporting = "Estudar de madrugada conta para o dia anterior",
                onDecrease = { viewModel.changeDayCutoff(-1) },
                onIncrease = { viewModel.changeDayCutoff(+1) },
            )
            Text(
                "Os limites valem no celular e no relógio a partir da próxima sincronização.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            SectionTitle("Sobre")
            Text("Flashcards ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Algoritmo de repetição: ${container.scheduler.id}. Todos os dados ficam neste " +
                    "celular e no relógio; nada é enviado para a internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
