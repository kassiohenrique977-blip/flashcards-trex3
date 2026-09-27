package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft

/** Formatos conhecidos. Os não implementados geram uma mensagem clara em vez de uma leitura errada. */
enum class ImportFormat(
    val label: String,
    val extensions: Set<String>,
    val implemented: Boolean,
) {
    CSV("CSV", setOf("csv", "tsv", "txt"), implemented = true),
    JSON("JSON", setOf("json"), implemented = true),
    APKG("Anki (.apkg)", setOf("apkg", "colpkg"), implemented = false);

    companion object {
        fun fromFileName(name: String): ImportFormat? {
            val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { extension in it.extensions }
        }
    }
}

/** Arquivo escolhido pelo usuário, já lido para a memória. */
class ImportDocument(val name: String, val bytes: ByteArray)

/**
 * Um cartão lido do arquivo, ainda sem validação. [line] é a posição do registro:
 * a linha no CSV, o número do item no JSON.
 */
data class ParsedCard(val line: Int, val draft: CardDraft)

/** O que saiu do arquivo: os cartões e, quando o formato traz, nome e descrição do deck. */
data class ImportedFile(
    val cards: List<ParsedCard>,
    val deckName: String? = null,
    val description: String? = null,
)

/** O arquivo inteiro não pôde ser lido (formato inválido, aspas não fechadas...). */
class ImportException(message: String) : Exception(message)

/**
 * Converte um arquivo em cartões. Para suportar um novo formato (por exemplo, .apkg),
 * basta uma nova implementação registrada em [CardImporters]; validação, duplicados e
 * gravação são comuns a todos os formatos.
 */
interface CardImporter {
    val format: ImportFormat

    /** @throws ImportException se o arquivo não puder ser lido. */
    fun parse(bytes: ByteArray): ImportedFile
}

object CardImporters {

    private val available: Map<ImportFormat, CardImporter> =
        listOf(CsvCardImporter(), JsonCardImporter()).associateBy { it.format }

    fun forFormat(format: ImportFormat): CardImporter? = available[format]

    fun forFileName(name: String): CardImporter? = ImportFormat.fromFileName(name)?.let(::forFormat)
}
