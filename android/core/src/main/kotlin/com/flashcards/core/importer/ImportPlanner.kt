package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Validation
import com.flashcards.core.model.ValidationException

data class ImportIssue(val line: Int, val message: String)

data class ImportPlan(
    /** Cartões válidos e normalizados, na ordem do arquivo. */
    val cards: List<CardDraft>,
    /** Linhas rejeitadas, com o motivo. */
    val issues: List<ImportIssue>,
    /** Cartões ignorados porque a frente já existe no deck ou apareceu antes no arquivo. */
    val duplicates: Int,
)

/** Decide o que entra numa importação. Comum a todos os formatos. */
object ImportPlanner {

    private val whitespace = Regex("\\s+")

    fun plan(
        parsed: List<ParsedCard>,
        existingFronts: Collection<String> = emptyList(),
        skipDuplicates: Boolean = true,
    ): ImportPlan {
        val seen = existingFronts.mapTo(HashSet(), ::duplicateKey)
        val cards = mutableListOf<CardDraft>()
        val issues = mutableListOf<ImportIssue>()
        var duplicates = 0

        for (item in parsed) {
            val clean = try {
                Validation.card(item.draft)
            } catch (e: ValidationException) {
                issues += ImportIssue(item.line, e.message ?: "Linha inválida.")
                continue
            }
            if (skipDuplicates && !seen.add(duplicateKey(clean.front))) {
                duplicates++
                continue
            }
            cards += clean
        }
        return ImportPlan(cards, issues, duplicates)
    }

    /** Duas frentes são iguais se só diferem em maiúsculas ou espaços. */
    fun duplicateKey(front: String): String = front.trim().lowercase().replace(whitespace, " ")
}
