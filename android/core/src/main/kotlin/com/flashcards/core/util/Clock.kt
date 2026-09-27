package com.flashcards.core.util

/** Fonte de tempo em epoch millis. Injetável para que os testes controlem o relógio. */
fun interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
