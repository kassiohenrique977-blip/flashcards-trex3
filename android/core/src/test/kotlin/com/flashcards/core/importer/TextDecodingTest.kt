package com.flashcards.core.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TextDecodingTest {

    @Test
    fun `utf-8 with byte order mark`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Olá".toByteArray(StandardCharsets.UTF_8)

        assertEquals("Olá", TextDecoding.decode(bytes))
    }

    @Test
    fun `plain utf-8`() {
        assertEquals("ação", TextDecoding.decode("ação".toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun `invalid utf-8 falls back to windows-1252`() {
        assertEquals("ação", TextDecoding.decode("ação".toByteArray(Charset.forName("windows-1252"))))
    }

    @Test
    fun `utf-16 little endian with byte order mark`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Olá".toByteArray(StandardCharsets.UTF_16LE)

        assertEquals("Olá", TextDecoding.decode(bytes))
    }
}
