package com.flashcards.core.importer

data class CsvRow(
    /** Linha (1-based) onde o registro começa no arquivo. */
    val line: Int,
    val fields: List<String>,
)

/**
 * Leitor CSV no estilo RFC 4180: campos entre aspas podem conter separador e quebras
 * de linha, e `""` dentro de aspas vira `"`. Aceita LF, CRLF e CR. Linhas em branco são ignoradas.
 */
object CsvParser {

    private val candidates = charArrayOf(',', ';', '\t')

    /** Escolhe o separador mais frequente na primeira linha, fora de aspas. Padrão: vírgula. */
    fun detectDelimiter(text: String): Char {
        val counts = IntArray(candidates.size)
        var inQuotes = false
        for (c in text.removePrefix(BOM)) {
            if (c == '"') inQuotes = !inQuotes
            if (inQuotes) continue
            if (c == '\n' || c == '\r') {
                if (counts.any { it > 0 }) break else continue
            }
            val index = candidates.indexOf(c)
            if (index >= 0) counts[index]++
        }
        val best = counts.indices.maxBy { counts[it] }
        return if (counts[best] > 0) candidates[best] else ','
    }

    /** @throws ImportException se houver aspas sem fechamento. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<CsvRow> {
        val source = text.removePrefix(BOM)
        val rows = mutableListOf<CsvRow>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var rowStart = 1
        var inQuotes = false
        var quoteStartLine = 0
        var i = 0

        fun endField() {
            fields += field.toString()
            field.setLength(0)
        }

        fun endRow() {
            endField()
            val blank = fields.size == 1 && fields[0].isBlank()
            if (!blank) rows += CsvRow(rowStart, fields.toList())
            fields.clear()
        }

        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            if (inQuotes) {
                when {
                    c == '"' && next == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    c == '\r' -> {
                        field.append('\n')
                        if (next == '\n') i++
                        line++
                    }
                    c == '\n' -> {
                        field.append('\n')
                        line++
                    }
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    // Aspas só abrem um campo no início dele (espaços antes são descartados).
                    '"' -> if (field.isBlank()) {
                        field.setLength(0)
                        inQuotes = true
                        quoteStartLine = line
                    } else {
                        field.append(c)
                    }
                    delimiter -> endField()
                    '\r', '\n' -> {
                        endRow()
                        if (c == '\r' && next == '\n') i++
                        line++
                        rowStart = line
                    }
                    else -> field.append(c)
                }
            }
            i++
        }

        if (inQuotes) throw ImportException("Aspas não fechadas a partir da linha $quoteStartLine.")
        if (field.isNotEmpty() || fields.isNotEmpty()) endRow()
        return rows
    }

    private const val BOM = "﻿"
}
