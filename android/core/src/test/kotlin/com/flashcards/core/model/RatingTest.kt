package com.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {

    @Test
    fun `codes follow the watch protocol order 1 to 4`() {
        assertEquals(listOf(1, 2, 3, 4), Rating.entries.map { it.code })
    }

    @Test
    fun `fromCode round trips every rating`() {
        Rating.entries.forEach { assertEquals(it, Rating.fromCode(it.code)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromCode rejects unknown codes`() {
        Rating.fromCode(5)
    }

    @Test
    fun `only GOOD and EASY count as correct`() {
        assertFalse(Rating.AGAIN.isCorrect)
        assertFalse(Rating.HARD.isCorrect)
        assertTrue(Rating.GOOD.isCorrect)
        assertTrue(Rating.EASY.isCorrect)
    }
}
