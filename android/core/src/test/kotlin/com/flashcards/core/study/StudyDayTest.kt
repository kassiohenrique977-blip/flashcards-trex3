package com.flashcards.core.study

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId

class StudyDayTest {

    private val zone = ZoneId.of("America/Sao_Paulo") // UTC-3, sem horário de verão

    @Test
    fun `early morning still belongs to the previous day`() {
        val window = StudyDay.window(millis("2026-09-14T03:00-03:00"), zone, cutoffHour = 4)

        assertEquals(millis("2026-09-13T04:00-03:00"), window.start)
        assertEquals(millis("2026-09-14T04:00-03:00"), window.end)
    }

    @Test
    fun `the cutoff instant starts a new day`() {
        val window = StudyDay.window(millis("2026-09-14T04:00-03:00"), zone, cutoffHour = 4)

        assertEquals(millis("2026-09-14T04:00-03:00"), window.start)
        assertEquals(millis("2026-09-15T04:00-03:00"), window.end)
    }

    @Test
    fun `late evening belongs to the same day`() {
        val window = StudyDay.window(millis("2026-09-14T23:59-03:00"), zone, cutoffHour = 4)

        assertEquals(millis("2026-09-14T04:00-03:00"), window.start)
    }

    @Test
    fun `midnight cutoff follows the calendar day`() {
        val window = StudyDay.window(millis("2026-09-14T00:30-03:00"), zone, cutoffHour = 0)

        assertEquals(millis("2026-09-14T00:00-03:00"), window.start)
        assertEquals(millis("2026-09-15T00:00-03:00"), window.end)
    }

    private fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}
