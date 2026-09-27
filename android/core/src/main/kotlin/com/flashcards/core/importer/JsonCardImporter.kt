package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Tags
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Importa JSON em qualquer um destes formatos:
 *
 * ```
 * { "deck": "Nome", "description": "...", "cards": [ { "front": "...", "back": "...", "tags": ["a"], "topic": "..." } ] }
 * [ { "front": "...", "back": "..." } ]
 * ```
 *
 * `frente`/`verso` também valem; `tags` pode ser lista ou texto; `topic` vira uma tag.
 * Outros campos (ids, contagens) são ignorados.
 */
class JsonCardImporter : CardImporter {

    override val format: ImportFormat = ImportFormat.JSON

    override fun parse(bytes: ByteArray): ImportedFile {
        val root = try {
            Json.parseToJsonElement(TextDecoding.decode(bytes))
        } catch (_: SerializationException) {
            throw ImportException("JSON inválido. Confira se o arquivo não está cortado.")
        }
        return when (root) {
            is JsonArray -> ImportedFile(cardsFrom(root))
            is JsonObject -> {
                val cards = root["cards"] as? JsonArray
                    ?: throw ImportException("O JSON precisa ter uma lista \"cards\".")
                ImportedFile(
                    cards = cardsFrom(cards),
                    deckName = root.text("deck", "name", "nome")?.trim()?.takeIf { it.isNotEmpty() },
                    description = root.text("description", "descricao")?.trim(),
                )
            }
            else -> throw ImportException("O JSON precisa ser uma lista de cartões ou um objeto com \"cards\".")
        }
    }

    private fun cardsFrom(array: JsonArray): List<ParsedCard> = array.mapIndexed { index, element ->
        val card = element as? JsonObject
        ParsedCard(
            line = index + 1,
            draft = CardDraft(
                front = card?.text("front", "frente").orEmpty(),
                back = card?.text("back", "verso").orEmpty(),
                tags = card?.let(::tagsOf).orEmpty(),
            ),
        )
    }

    private fun tagsOf(card: JsonObject): List<String> {
        val tags = when (val value = card["tags"]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> value.contentOrNull?.let(Tags::parse).orEmpty()
            else -> emptyList()
        }
        val topic = card.text("topic", "tema")
        return Tags.normalize(if (topic.isNullOrBlank()) tags else tags + topic)
    }

    /** Primeiro campo existente entre [keys], como texto (números também valem). */
    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content }
}
