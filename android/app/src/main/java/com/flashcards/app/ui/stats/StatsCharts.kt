package com.flashcards.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.app.ui.common.formatCount
import com.flashcards.core.stats.DayStats

// Especificação das marcas (skill de visualização): colunas de até 24 dp com topo
// arredondado de 4 dp e base reta; linha de 2 dp; ponto final de 8 dp com anel de 2 dp
// na cor da superfície; 2 dp de superfície separando segmentos; grade em linha fina sólida.

private val AXIS_BAND = 20.dp
private val Y_LABELS = 32.dp
private val TOP_PAD = 8.dp
private val GAP = 2.dp
private val MAX_BAR = 24.dp
private val END_RADIUS = 4.dp

private data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun y(value: Int, max: Int) = bottom - height * value / max
}

private fun DrawScope.plotArea() = Plot(
    left = Y_LABELS.toPx(),
    top = TOP_PAD.toPx(),
    right = size.width,
    bottom = size.height - AXIS_BAND.toPx(),
)

/** Grade horizontal em 0, metade e topo, com os valores à esquerda. */
private fun DrawScope.valueGrid(plot: Plot, max: Int, colors: ChartColors, measurer: TextMeasurer) {
    val style = TextStyle(color = colors.muted, fontSize = 11.sp)
    listOf(0, max / 2, max).distinct().forEach { tick ->
        val y = plot.y(tick, max)
        drawLine(
            color = if (tick == 0) colors.baseline else colors.grid,
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = 1.dp.toPx(),
        )
        val layout = measurer.measure(formatCount(tick), style)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
    }
}

/** Datas do primeiro e do último dia, embaixo do eixo. */
private fun DrawScope.dayAxis(plot: Plot, first: String, last: String, colors: ChartColors, measurer: TextMeasurer) {
    val style = TextStyle(color = colors.muted, fontSize = 11.sp)
    val top = plot.bottom + 4.dp.toPx()
    drawText(measurer.measure(first, style), topLeft = Offset(plot.left, top))
    val lastLayout = measurer.measure(last, style)
    drawText(lastLayout, topLeft = Offset(plot.right - lastLayout.size.width, top))
}

private fun indexAt(x: Float, left: Float, width: Float, count: Int): Int =
    (((x - left) / width) * count).toInt().coerceIn(0, count - 1)

private fun topRoundedPath(rect: Rect, radius: Float) = Path().apply {
    addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = CornerRadius(radius),
            topRight = CornerRadius(radius),
            bottomRight = CornerRadius.Zero,
            bottomLeft = CornerRadius.Zero,
        ),
    )
}

/**
 * Revisões por dia, empilhadas por resultado (acertos, difíceis, erros, de baixo para cima).
 * Tocar numa coluna escolhe o dia; o toque vale para a faixa inteira do dia, não só a coluna.
 */
@Composable
fun ReviewsPerDayChart(
    days: List<DayStats>,
    selected: Int?,
    firstLabel: String,
    lastLabel: String,
    colors: ChartColors,
    description: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val left = Y_LABELS.toPx()
                    onSelect(indexAt(offset.x, left, size.width - left, days.size))
                }
            },
    ) {
        if (days.isEmpty()) return@Canvas
        val plot = plotArea()
        val max = niceCeil(days.maxOf { it.reviews })
        valueGrid(plot, max, colors, measurer)

        val slot = plot.width / days.size
        val barWidth = minOf(slot - GAP.toPx(), MAX_BAR.toPx()).coerceAtLeast(1f)
        days.forEachIndexed { i, day ->
            val x = plot.left + i * slot + (slot - barWidth) / 2
            if (i == selected) {
                drawRect(colors.muted.copy(alpha = 0.15f), Offset(plot.left + i * slot, plot.top), Size(slot, plot.height))
            }
            if (day.reviews == 0) return@forEachIndexed
            val segments = listOf(day.correct to colors.good, day.hard to colors.warning, day.again to colors.critical)
                .filter { it.first > 0 }
            var base = plot.bottom
            segments.forEachIndexed { s, (value, color) ->
                val top = base - plot.height * value / max
                val rect = Rect(x, top, x + barWidth, base)
                if (s == segments.lastIndex) {
                    drawPath(topRoundedPath(rect, END_RADIUS.toPx()), color)
                } else {
                    drawRect(color, rect.topLeft, rect.size)
                    // Espaço na cor da superfície entre segmentos, em vez de borda.
                    drawRect(colors.surface, Offset(x, top - GAP.toPx() / 2), Size(barWidth, GAP.toPx()))
                }
                base = top
            }
        }
        dayAxis(plot, firstLabel, lastLabel, colors, measurer)
    }
}

/**
 * Linha única: cartões aprendidos, acumulado. Faixa suave embaixo da linha, ponto no
 * fim com o valor escrito ao lado. Tocar mostra a linha vertical no dia escolhido.
 */
@Composable
fun LearnedLineChart(
    values: List<Int>,
    selected: Int?,
    firstLabel: String,
    lastLabel: String,
    colors: ChartColors,
    description: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    val left = Y_LABELS.toPx()
                    onSelect(indexAt(offset.x, left, size.width - left, values.size))
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val plot = plotArea()
        val max = niceCeil(values.max())
        valueGrid(plot, max, colors, measurer)
        val series = colors.series.first()
        // Pontos no centro da faixa de cada dia, como as colunas do gráfico de cima.
        val step = plot.width / values.size
        fun point(i: Int) = Offset(plot.left + step * (i + 0.5f), plot.y(values[i], max))

        val line = Path().apply {
            values.indices.forEach { i -> if (i == 0) moveTo(point(i).x, point(i).y) else lineTo(point(i).x, point(i).y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, plot.bottom)
            lineTo(point(0).x, plot.bottom)
            close()
        }
        drawPath(area, series.copy(alpha = 0.10f))
        drawPath(line, series, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (selected != null && selected in values.indices) {
            val x = point(selected).x
            drawLine(colors.muted, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
            drawMarker(point(selected), series, colors.surface)
        }

        val end = point(values.lastIndex)
        drawMarker(end, series, colors.surface)
        val label = measurer.measure(values.last().toString(), TextStyle(color = colors.textPrimary, fontSize = 12.sp))
        drawText(
            label,
            topLeft = Offset(
                (end.x - label.size.width - 8.dp.toPx()).coerceAtLeast(plot.left),
                (end.y - label.size.height - 6.dp.toPx()).coerceAtLeast(0f),
            ),
        )
        dayAxis(plot, firstLabel, lastLabel, colors, measurer)
    }
}

private fun DrawScope.drawMarker(center: Offset, color: Color, surface: Color) {
    drawCircle(surface, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

/** Parte do todo: uma barra horizontal fina, um segmento por estado, separados por 2 dp. */
@Composable
fun StateBar(
    counts: List<Int>,
    colors: ChartColors,
    description: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics { contentDescription = description },
    ) {
        val total = counts.sum()
        if (total == 0) {
            drawRect(colors.grid)
            return@Canvas
        }
        val visible = counts.withIndex().filter { it.value > 0 }
        val gaps = GAP.toPx() * (visible.size - 1)
        var x = 0f
        visible.forEachIndexed { position, (slot, value) ->
            val width = (size.width - gaps) * value / total
            val rect = Rect(x, 0f, x + width, size.height)
            val color = colors.series[slot]
            if (position == visible.lastIndex) {
                // Extremidade dos dados arredondada; início reto.
                drawPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = rect,
                                topLeft = CornerRadius.Zero,
                                topRight = CornerRadius(END_RADIUS.toPx()),
                                bottomRight = CornerRadius(END_RADIUS.toPx()),
                                bottomLeft = CornerRadius.Zero,
                            ),
                        )
                    },
                    color,
                )
            } else {
                drawRect(color, rect.topLeft, rect.size)
            }
            x += width + GAP.toPx()
        }
    }
}
