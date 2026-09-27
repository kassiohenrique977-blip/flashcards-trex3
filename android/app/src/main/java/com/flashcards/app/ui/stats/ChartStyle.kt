package com.flashcards.app.ui.stats

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Cores dos gráficos: a paleta de referência da skill de visualização, validada com
 * scripts/validate_palette.js nos dois modos. Os passos do modo escuro são próprios,
 * não uma inversão do claro.
 */
@Immutable
data class ChartColors(
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val grid: Color,
    val baseline: Color,
    /** Status: resultado bom/ruim. Sempre acompanhados de símbolo e rótulo. */
    val good: Color,
    val warning: Color,
    val critical: Color,
    /** Categóricas, em ordem fixa (nunca reordenar). */
    val series: List<Color>,
)

private val Light = ChartColors(
    surface = Color(0xFFFCFCFB),
    textPrimary = Color(0xFF0B0B0B),
    textSecondary = Color(0xFF52514E),
    muted = Color(0xFF898781),
    grid = Color(0xFFE1E0D9),
    baseline = Color(0xFFC3C2B7),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    critical = Color(0xFFD03B3B),
    series = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100)),
)

private val Dark = ChartColors(
    surface = Color(0xFF1A1A19),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC3C2B7),
    muted = Color(0xFF898781),
    grid = Color(0xFF2C2C2A),
    baseline = Color(0xFF383835),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    critical = Color(0xFFD03B3B),
    series = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500)),
)

@Composable
fun chartColors(): ChartColors = if (isSystemInDarkTheme()) Dark else Light

/** Topo "redondo" do eixo: 1, 2 ou 5 × 10^n, nunca menor que o valor. */
fun niceCeil(value: Int): Int {
    if (value <= 1) return 1
    val magnitude = 10.0.pow(floor(log10(value.toDouble())))
    for (step in listOf(1, 2, 5, 10)) {
        val candidate = (step * magnitude).toInt()
        if (candidate >= value) return candidate
    }
    return value
}
