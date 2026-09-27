package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Tags

/**
 * Importa CSV com as colunas frente, verso e tags (opcional).
 *
 * Se a primeira linha tiver nomes de coluna reconhecidos (front/back/tags ou
 * frente/verso/tags), eles definem a ordem; senão as colunas são posicionais.
 */
class CsvCardImporter : CardImporter {

    override val format: ImportFormat = ImportFormat.CSV

    override fun parse(bytes: ByteArray): ImportedFile = ImportedFile(parseText(TextDecoding.decode(bytes)))

    fun parseText(text: String): List<ParsedCard> {
        val rows = CsvParser.parse(text)
        if (rows.isEmpty()) return emptyList()

        val header = Columns.fromHeader(rows.first().fields)
        val columns = header ?: Columns.POSITIONAL
        val data = if (header != null) rows.drop(1) else rows

        return data.map { row ->
            ParsedCard(
                line = row.line,
                draft = CardDraft(
                    front = row.fields.getOrElse(columns.front) { "" },
                    back = row.fields.getOrElse(columns.back) { "" },
                    tags = columns.tags?.let { row.fields.getOrNull(it) }?.let(Tags::parse).orEmpty(),
                ),
            )
        }
    }

    private data class Columns(val front: Int, val back: Int, val tags: Int?) {
        companion object {
            val POSITIONAL = Columns(front = 0, back = 1, tags = 2)

            private val frontNames = setOf("front", "frente", "pergunta", "question")
            private val backNames = setOf("back", "verso", "resposta", "answer")
            private val tagNames = setOf("tags", "tag", "etiquetas")

            fun fromHeader(fields: List<String>): Columns? {
                val names = fields.map { it.trim().lowercase() }
                val front = names.indexOfFirst { it in frontNames }
                val back = names.indexOfFirst { it in backNames }
                if (front < 0 || back < 0) return null
                return Columns(front, back, names.indexOfFirst { it in tagNames }.takeIf { it >= 0 })
            }
        }
    }
}
