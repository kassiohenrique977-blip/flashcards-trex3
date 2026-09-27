package com.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardStateTest {

    @Test
    fun `fromCode round trips every state`() {
        CardState.entries.forEach { assertEquals(it, CardState.fromCode(it.code)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromCode rejects unknown codes`() {
        CardState.fromCode(-1)
    }
}
