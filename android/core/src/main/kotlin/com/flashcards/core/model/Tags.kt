package com.flashcards.core.model

/**
 * Regras de tags: sem espaços internos (viram "_"), sem repetição (ignorando
 * maiúsculas; vale a primeira grafia) e sem tags vazias. Guardadas separadas por espaço.
 */
object Tags {
    private val whitespace = Regex("\\s+")
    private val separators = Regex("[\\s,;]+")

    fun normalize(raw: Iterable<String>): List<String> {
        val unique = LinkedHashMap<String, String>()
        raw.asSequence()
            .map { it.trim().replace(whitespace, "_") }
            .filter { it.isNotEmpty() }
            .forEach { unique.putIfAbsent(it.lowercase(), it) }
        return unique.values.toList()
    }

    /** Lê tags digitadas ou guardadas: "ingles verbos", "ingles, verbos" ou "ingles;verbos". */
    fun parse(text: String): List<String> = normalize(text.split(separators))

    fun format(tags: List<String>): String = normalize(tags).joinToString(" ")
}
