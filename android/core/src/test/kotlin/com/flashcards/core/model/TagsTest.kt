package com.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test
    fun `normalize drops blanks and duplicates keeping the first spelling`() {
        assertEquals(
            listOf("Ingles", "verbos"),
            Tags.normalize(listOf(" Ingles ", "", "ingles", "verbos", "  ")),
        )
    }

    @Test
    fun `normalize replaces inner whitespace with underscore`() {
        assertEquals(listOf("phrasal_verbs"), Tags.normalize(listOf("phrasal   verbs")))
    }

    @Test
    fun `parse accepts spaces commas and semicolons`() {
        assertEquals(listOf("a", "b", "c", "d"), Tags.parse("a, b;c  d"))
    }

    @Test
    fun `parse of blank text is empty`() {
        assertEquals(emptyList<String>(), Tags.parse("   "))
    }

    @Test
    fun `format and parse round trip`() {
        val tags = listOf("ingles", "verbos")
        assertEquals(tags, Tags.parse(Tags.format(tags)))
    }
}
