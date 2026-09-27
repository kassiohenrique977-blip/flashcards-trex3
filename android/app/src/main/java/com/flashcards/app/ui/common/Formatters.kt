package com.flashcards.app.ui.common

import com.flashcards.core.model.CardState
import java.util.Locale
import kotlin.math.roundToInt

private val portuguese: Locale = Locale.forLanguageTag("pt-BR")

/** Separador de milhar do português: 1284 → "1.284". */
fun formatCount(value: Int): String = String.format(portuguese, "%,d", value)

fun countLabel(count: Int, singular: String, plural: String): String =
    if (count == 1) "1 $singular" else "${formatCount(count)} $plural"

fun cardCountLabel(count: Int): String = countLabel(count, "cartão", "cartões")

fun dueCountLabel(count: Int): String = "$count para revisar"

fun newCountLabel(count: Int): String = countLabel(count, "novo", "novos")

fun cardStateLabel(state: CardState): String = when (state) {
    CardState.NEW -> "Novo"
    CardState.LEARNING -> "Aprendendo"
    CardState.REVIEW -> "Revisão"
    CardState.RELEARNING -> "Reaprendendo"
}

/** Tempo até a próxima revisão, curto o bastante para caber num botão: "<1 min", "10 min", "4 d", "1,5 m". */
fun formatDelay(ms: Long): String {
    val minutes = ms / 60_000.0
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "${minutes.roundToInt()} min"
        minutes < 24 * 60 -> "${(minutes / 60).roundToInt()} h"
        else -> {
            val days = minutes / (24 * 60)
            when {
                days < 30 -> "${days.roundToInt()} d"
                days < 365 -> "${oneDecimal(days / 30)} m"
                else -> "${oneDecimal(days / 365)} a"
            }
        }
    }
}

/** Quanto tempo atrás: "agora", "há 5 min", "há 2 h", "há 3 d". */
fun formatRelative(now: Long, time: Long): String {
    val minutes = (now - time).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        minutes < 24 * 60 -> "há ${minutes / 60} h"
        else -> "há ${minutes / (24 * 60)} d"
    }
}

/** Duração de uma sessão: "8:32". */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().replace('.', ',')
}
