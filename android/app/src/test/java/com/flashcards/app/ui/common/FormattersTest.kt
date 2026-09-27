package com.flashcards.app.ui.common

import com.flashcards.core.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `card count uses singular only for one`() {
        assertEquals("0 cartões", cardCountLabel(0))
        assertEquals("1 cartão", cardCountLabel(1))
        assertEquals("128 cartões", cardCountLabel(128))
    }

    @Test
    fun `new count uses singular only for one`() {
        assertEquals("1 novo", newCountLabel(1))
        assertEquals("20 novos", newCountLabel(20))
    }

    @Test
    fun `due count label`() {
        assertEquals("12 para revisar", dueCountLabel(12))
    }

    @Test
    fun `delays fit on a button`() {
        assertEquals("<1 min", formatDelay(30_000))
        assertEquals("1 min", formatDelay(60_000))
        assertEquals("6 min", formatDelay(330_000))
        assertEquals("10 min", formatDelay(600_000))
        assertEquals("3 h", formatDelay(3 * 3_600_000L))
        assertEquals("1 d", formatDelay(86_400_000L))
        assertEquals("25 d", formatDelay(25 * 86_400_000L))
        assertEquals("1,5 m", formatDelay(45 * 86_400_000L))
        assertEquals("2 m", formatDelay(60 * 86_400_000L))
        assertEquals("1,1 a", formatDelay(400 * 86_400_000L))
    }

    @Test
    fun `session duration as minutes and seconds`() {
        assertEquals("8:32", formatDuration(512_000))
        assertEquals("0:05", formatDuration(5_400))
        assertEquals("75:00", formatDuration(4_500_000))
    }

    @Test
    fun `large numbers use the portuguese thousands separator`() {
        assertEquals("999", formatCount(999))
        assertEquals("1.284", formatCount(1_284))
        assertEquals("1.284 revisões", countLabel(1_284, "revisão", "revisões"))
    }

    @Test
    fun `relative time for the last sync`() {
        val now = 10 * 86_400_000L
        assertEquals("agora", formatRelative(now, now - 30_000))
        assertEquals("há 5 min", formatRelative(now, now - 5 * 60_000))
        assertEquals("há 2 h", formatRelative(now, now - 2 * 3_600_000))
        assertEquals("há 3 d", formatRelative(now, now - 3 * 86_400_000L))
        assertEquals("agora", formatRelative(now, now + 60_000))
    }

    @Test
    fun `state labels in portuguese`() {
        assertEquals("Novo", cardStateLabel(CardState.NEW))
        assertEquals("Reaprendendo", cardStateLabel(CardState.RELEARNING))
    }
}
