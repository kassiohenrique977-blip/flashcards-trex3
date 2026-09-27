package com.flashcards.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcards.app.ui.common.TitleTopBar
import com.flashcards.app.ui.common.appContainer
import com.flashcards.app.ui.common.cardCountLabel
import com.flashcards.app.ui.common.cardStateLabel
import com.flashcards.app.ui.common.countLabel
import com.flashcards.app.ui.common.formatCount
import com.flashcards.core.model.CardState
import com.flashcards.core.stats.DayStats
import com.flashcards.core.stats.Statistics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun StatsScreen() {
    val container = appContainer()
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.statisticsLoader, container.deckRepository, container.settingsRepository, container.clock, container::zone)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Recalcula sempre que a aba aparece: estudos e sincronizações mudam os números.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { TitleTopBar("Estatísticas") }) { padding ->
        if (state.stats == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            StatsContent(
                state = state,
                zone = container.zone(),
                onSelectDeck = viewModel::selectDeck,
                onSelectDay = viewModel::selectDay,
                onToggleTable = viewModel::toggleTable,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Conteúdo sem estado, também usado nas capturas de tela dos testes. */
@Composable
fun StatsContent(
    state: StatsUiState,
    zone: ZoneId,
    onSelectDeck: (String?) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.stats ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DeckFilter(state, onSelect = onSelectDeck)
        // Ao recarregar, o gráfico anterior fica visível, só mais claro (sem piscar).
        Column(
            modifier = Modifier
                .alpha(if (state.refreshing) 0.6f else 1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TodayTiles(stats)
            ReviewsCard(stats, state, zone, onSelect = onSelectDay, onToggleTable = onToggleTable)
            LearnedCard(stats, state.selectedDay, zone, onSelect = onSelectDay)
            StatesCard(stats)
        }
    }
}

/** Um único filtro, acima de tudo o que ele afeta. */
@Composable
private fun DeckFilter(state: StatsUiState, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = state.deckId == null, onClick = { onSelect(null) }, label = { Text("Todos os decks") })
        }
        items(state.decks, key = { it.id }) { deck ->
            FilterChip(selected = state.deckId == deck.id, onClick = { onSelect(deck.id) }, label = { Text(deck.name) })
        }
    }
}

@Composable
private fun TodayTiles(stats: Statistics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Estudados hoje", "${stats.studiedToday}", cardCountLabel(stats.studiedToday).substringAfter(' '), Modifier.weight(1f))
            StatTile("Restantes hoje", "${stats.remainingToday}", "para revisar", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sequência", "${stats.streakDays}", countLabel(stats.streakDays, "dia", "dias").substringAfter(' '), Modifier.weight(1f))
            StatTile(
                "Acertos hoje",
                stats.accuracyToday?.let { "$it%" } ?: "—",
                "${countLabel(stats.correctToday, "acerto", "acertos")} · ${countLabel(stats.againToday, "erro", "erros")}",
                Modifier.weight(1f),
            )
        }
        Text(
            "No total: ${countLabel(stats.totalReviews, "revisão", "revisões")} · " +
                "${countLabel(stats.totalCorrect, "acerto", "acertos")} · ${countLabel(stats.totalAgain, "erro", "erros")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cartão com a superfície própria dos gráficos (a validada pela paleta). */
@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = chartColors()
    Surface(shape = MaterialTheme.shapes.large, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            content()
        }
    }
}

@Composable
private fun ReviewsCard(
    stats: Statistics,
    state: StatsUiState,
    zone: ZoneId,
    onSelect: (Int) -> Unit,
    onToggleTable: () -> Unit,
) {
    val colors = chartColors()
    val days = stats.days
    val index = state.selectedDay?.takeIf { it in days.indices } ?: days.lastIndex
    val day = days[index]
    ChartCard("Revisões por dia", "Últimos ${days.size} dias · toque numa coluna para ver o dia") {
        // O valor vem primeiro, o rótulo depois.
        Text(
            "${dayLabel(day, index == days.lastIndex, zone)}: ${countLabel(day.reviews, "revisão", "revisões")} · " +
                "${day.correct} ✓ · ${day.hard} ≈ · ${day.again} ✗",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        ReviewsPerDayChart(
            days = days,
            selected = state.selectedDay,
            firstLabel = dayLabel(days.first(), false, zone),
            lastLabel = "hoje",
            colors = colors,
            description = "Revisões por dia nos últimos ${days.size} dias. Total: ${days.sumOf { it.reviews }}.",
            onSelect = onSelect,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(colors.good, "✓ Acertos", colors)
            LegendItem(colors.warning, "≈ Difíceis", colors)
            LegendItem(colors.critical, "✗ Erros", colors)
        }
        TextButton(onClick = onToggleTable) { Text(if (state.showTable) "Ocultar tabela" else "Ver como tabela") }
        if (state.showTable) ReviewsTable(days, zone, colors)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, colors: ChartColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        // O texto usa a cor de texto; quem identifica a série é a amostra ao lado.
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

/** Versão em tabela do gráfico (acessível sem depender de cor ou toque). */
@Composable
private fun ReviewsTable(days: List<DayStats>, zone: ZoneId, colors: ChartColors) {
    val studied = days.withIndex().filter { it.value.reviews > 0 }.reversed()
    if (studied.isEmpty()) {
        Text("Nenhuma revisão nestes dias.", color = colors.textSecondary)
        return
    }
    Column {
        TableRow(listOf("Dia", "Revisões", "✓", "≈", "✗"), colors.textSecondary, bold = true)
        HorizontalDivider(color = colors.grid)
        studied.forEach { (i, day) ->
            TableRow(
                listOf(dayLabel(day, i == days.lastIndex, zone), "${day.reviews}", "${day.correct}", "${day.hard}", "${day.again}"),
                colors.textPrimary,
            )
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, color: Color, bold: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (i == 0) 1.4f else 1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
            )
        }
    }
}

@Composable
private fun LearnedCard(stats: Statistics, selectedDay: Int?, zone: ZoneId, onSelect: (Int) -> Unit) {
    val colors = chartColors()
    val values = stats.learnedByDay
    val index = selectedDay?.takeIf { it in values.indices } ?: values.lastIndex
    ChartCard("Evolução", "Cartões aprendidos, acumulado") {
        Text(
            "${dayLabel(stats.days[index], index == values.lastIndex, zone)}: ${cardCountLabel(values[index])}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        LearnedLineChart(
            values = values,
            selected = selectedDay,
            firstLabel = dayLabel(stats.days.first(), false, zone),
            lastLabel = "hoje",
            colors = colors,
            description = "Cartões aprendidos: ${values.first()} há ${values.size - 1} dias, ${values.last()} hoje.",
            onSelect = onSelect,
        )
    }
}

@Composable
private fun StatesCard(stats: Statistics) {
    val colors = chartColors()
    val states = listOf(CardState.NEW, CardState.LEARNING, CardState.RELEARNING, CardState.REVIEW)
    val counts = states.map { stats.cardsByState.getValue(it) }
    val total = stats.totalCards
    ChartCard("Cartões por estado", cardCountLabel(total)) {
        StateBar(
            counts = counts,
            colors = colors,
            description = states.zip(counts).joinToString { (s, c) -> "${cardStateLabel(s)}: $c" },
        )
        // Legenda com os números: identifica cada cor sem depender só dela.
        states.forEachIndexed { i, state ->
            val count = counts[i]
            val percent = if (total == 0) 0 else (count * 100.0 / total).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(colors.series[i], RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(cardStateLabel(state), color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text("${formatCount(count)} · $percent%", color = colors.textSecondary)
            }
        }
    }
}

private fun dayLabel(day: DayStats, isToday: Boolean, zone: ZoneId): String =
    if (isToday) "Hoje" else dayFormat.format(Instant.ofEpochMilli(day.dayStart).atZone(zone))
