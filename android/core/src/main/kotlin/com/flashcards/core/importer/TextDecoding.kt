package com.flashcards.core.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Decodifica arquivos de texto sem que o usuário precise escolher a codificação:
 * respeita BOM de UTF-8/UTF-16 e, se o conteúdo não for UTF-8 válido, usa Windows-1252
 * (o padrão do Excel em português no Windows).
 */
object TextDecoding {

    private val windows1252: Charset = Charset.forName("windows-1252")

    fun decode(bytes: ByteArray): String {
        if (bytes.startsWith(0xEF, 0xBB, 0xBF)) return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        if (bytes.startsWith(0xFF, 0xFE)) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        if (bytes.startsWith(0xFE, 0xFF)) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, windows1252)
        }
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
