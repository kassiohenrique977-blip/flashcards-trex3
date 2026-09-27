package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCardImporterTest {

    private val importer = JsonCardImporter()

    private fun parse(json: String) = importer.parse(json.toByteArray())

    @Test
    fun `deck object brings name, description and topic as tag`() {
        val file = parse(
            """
            {
              "deck": "Fundamentos de Redes — EC III",
              "description": "Revisão de redes",
              "cardCount": 2,
              "cards": [
                { "id": 1, "topic": "OSI", "front": "PDU da camada de Rede", "back": "Pacote IP" },
                { "id": 2, "topic": "Classes IP", "front": "Classe A", "back": "1 a 126", "tags": ["provas"] }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Fundamentos de Redes — EC III", file.deckName)
        assertEquals("Revisão de redes", file.description)
        assertEquals(
            listOf(
                ParsedCard(1, CardDraft("PDU da camada de Rede", "Pacote IP", listOf("OSI"))),
                ParsedCard(2, CardDraft("Classe A", "1 a 126", listOf("provas", "Classes_IP"))),
            ),
            file.cards,
        )
    }

    @Test
    fun `plain list of cards with portuguese keys and text tags`() {
        val file = parse("""[ { "frente": "Casa", "verso": "House", "tags": "ingles substantivos" } ]""")

        assertNull(file.deckName)
        assertEquals(CardDraft("Casa", "House", listOf("ingles", "substantivos")), file.cards.single().draft)
    }

    @Test
    fun `numbers are read as text and missing sides stay empty for the planner`() {
        val file = parse("""{ "cards": [ { "front": 192, "back": null }, "não é objeto" ] }""")

        assertEquals(CardDraft("192", ""), file.cards[0].draft)
        assertEquals(CardDraft("", ""), file.cards[1].draft)
        assertEquals(2, file.cards[1].line)
    }

    @Test
    fun `broken JSON and missing cards list are clear errors`() {
        val broken = assertThrows(ImportException::class.java) { parse("""{ "cards": [ """) }
        val noCards = assertThrows(ImportException::class.java) { parse("""{ "deck": "x" }""") }

        assertTrue(broken.message!!.contains("JSON inválido"))
        assertTrue(noCards.message!!.contains("cards"))
    }

    @Test
    fun `json files use this importer`() {
        assertTrue(CardImporters.forFileName("flashcards_ec3_redes.json") is JsonCardImporter)
    }
}
