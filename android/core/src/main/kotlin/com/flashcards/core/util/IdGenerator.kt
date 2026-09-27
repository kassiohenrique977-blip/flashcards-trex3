package com.flashcards.core.util

import java.util.UUID

/**
 * Gera IDs globalmente únicos. Celular e relógio criam IDs sem se coordenar
 * (por exemplo, o ID de cada revisão), por isso usamos UUID.
 */
fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
