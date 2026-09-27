package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CsvCardImporterTest {

    private val importer = CsvCardImporter()

    @Test
    fun `example from the specification`() {
        val cards = importer.parseText(
            "front,back,tags\n" +
                "\"Hello\",\"Olá\",\"ingles\"\n" +
                "\"Good morning\",\"Bom dia\",\"ingles\"\n",
        )

        assertEquals(
            listOf(
                ParsedCard(2, CardDraft("Hello", "Olá", listOf("ingles"))),
                ParsedCard(3, CardDraft("Good morning", "Bom dia", listOf("ingles"))),
            ),
            cards,
        )
    }

    @Test
    fun `portuguese header with semicolons`() {
        val card = importer.parseText("frente;verso;tags\nCasa;House;ingles substantivos").single()

        assertEquals(CardDraft("Casa", "House", listOf("ingles", "substantivos")), card.draft)
    }

    @Test
    fun `columns follow the header order`() {
        val card = importer.parseText("tags,back,front\nx,Olá,Hello").single()

        assertEquals(CardDraft("Hello", "Olá", listOf("x")), card.draft)
    }

    @Test
    fun `without a header columns are positional`() {
        val cards = importer.parseText("Hello,Olá\nBye,Tchau,ingles")

        assertEquals(
            listOf(
                ParsedCard(1, CardDraft("Hello", "Olá")),
                ParsedCard(2, CardDraft("Bye", "Tchau", listOf("ingles"))),
            ),
            cards,
        )
    }

    @Test
    fun `missing back becomes empty so the planner can report the line`() {
        val card = importer.parseText("front,back\nsó a frente").single()

        assertEquals(CardDraft("só a frente", ""), card.draft)
    }

    @Test
    fun `windows-1252 files keep their accents`() {
        val bytes = "front,back\nOlá,ação".toByteArray(Charset.forName("windows-1252"))

        assertEquals(CardDraft("Olá", "ação"), importer.parse(bytes).cards.single().draft)
    }

    @Test
    fun `empty file gives no cards`() {
        assertTrue(importer.parseText("").isEmpty())
        assertTrue(importer.parseText("front,back\n").isEmpty())
    }

    @Test
    fun `format is picked from the file extension`() {
        assertEquals(ImportFormat.CSV, ImportFormat.fromFileName("Inglês.CSV"))
        assertEquals(ImportFormat.APKG, ImportFormat.fromFileName("deck.apkg"))
        assertEquals(null, ImportFormat.fromFileName("sem-extensao"))
        assertTrue(CardImporters.forFileName("deck.apkg") == null)
    }
}
