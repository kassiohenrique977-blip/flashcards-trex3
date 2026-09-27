package com.flashcards.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    @Test
    fun `plain rows with header`() {
        val rows = CsvParser.parse("front,back,tags\nHello,Olá,ingles\n")

        assertEquals(listOf("front", "back", "tags"), rows[0].fields)
        assertEquals(listOf("Hello", "Olá", "ingles"), rows[1].fields)
        assertEquals(2, rows.size)
    }

    @Test
    fun `quoted fields keep commas and escaped quotes`() {
        val rows = CsvParser.parse("\"What does \"\"although\"\" mean?\",\"embora, apesar\"")

        assertEquals(listOf("What does \"although\" mean?", "embora, apesar"), rows.single().fields)
    }

    @Test
    fun `line breaks inside quotes stay in the field and line numbers follow the file`() {
        val rows = CsvParser.parse("a,\"linha 1\r\nlinha 2\"\r\nb,c\r\n")

        assertEquals(listOf("a", "linha 1\nlinha 2"), rows[0].fields)
        assertEquals(1, rows[0].line)
        assertEquals(listOf("b", "c"), rows[1].fields)
        assertEquals(3, rows[1].line)
    }

    @Test
    fun `blank lines are skipped but still counted`() {
        val rows = CsvParser.parse("a,b\n\n   \nc,d")

        assertEquals(listOf(1, 4), rows.map { it.line })
    }

    @Test
    fun `semicolon and tab separators are detected`() {
        assertEquals(';', CsvParser.detectDelimiter("frente;verso;tags\na;b;c"))
        assertEquals('\t', CsvParser.detectDelimiter("frente\tverso\na\tb"))
        assertEquals(',', CsvParser.detectDelimiter("sem separador"))
    }

    @Test
    fun `separators inside quotes do not count for detection`() {
        assertEquals(';', CsvParser.detectDelimiter("\"a,b,c\";d"))
    }

    @Test
    fun `byte order mark is ignored`() {
        val rows = CsvParser.parse("﻿front,back")

        assertEquals("front", rows.single().fields.first())
    }

    @Test
    fun `spaces before an opening quote are dropped`() {
        val rows = CsvParser.parse("\"a\", \"b, c\"")

        assertEquals(listOf("a", "b, c"), rows.single().fields)
    }

    @Test
    fun `empty fields are kept`() {
        assertEquals(listOf("a", "", ""), CsvParser.parse("a,,").single().fields)
    }

    @Test
    fun `unterminated quote reports where it started`() {
        val error = assertThrows(ImportException::class.java) { CsvParser.parse("a,b\nc,\"d\ne,f") }

        assertTrue(error.message!!.contains("linha 2"))
    }
}
