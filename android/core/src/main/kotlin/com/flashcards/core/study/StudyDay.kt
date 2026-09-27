package com.flashcards.core.study

import java.time.Instant
import java.time.ZoneId

/** Intervalo [start, end) de um dia de estudo, em epoch millis. */
data class StudyDayWindow(val start: Long, val end: Long)

object StudyDay {

    /**
     * Dia de estudo que contém [now]. O dia vira às [cutoffHour] horas locais, então
     * estudar à 1h da manhã ainda conta para o dia anterior.
     */
    fun window(now: Long, zone: ZoneId, cutoffHour: Int = 4): StudyDayWindow {
        require(cutoffHour in 0..23) { "Hora de virada do dia inválida." }
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var start = local.toLocalDate().atTime(cutoffHour, 0).atZone(zone)
        if (start.isAfter(local)) start = start.minusDays(1)
        return StudyDayWindow(
            start = start.toInstant().toEpochMilli(),
            end = start.plusDays(1).toInstant().toEpochMilli(),
        )
    }

    /**
     * Os últimos [count] dias de estudo, do mais antigo até o de hoje. As contas são feitas
     * no calendário local, então dias com mudança de horário de verão têm 23 ou 25 horas.
     */
    fun recentWindows(now: Long, zone: ZoneId, cutoffHour: Int, count: Int): List<StudyDayWindow> {
        val todayStart = Instant.ofEpochMilli(window(now, zone, cutoffHour).start).atZone(zone)
        return (count - 1 downTo 0).map { back ->
            val start = todayStart.minusDays(back.toLong())
            StudyDayWindow(start.toInstant().toEpochMilli(), start.plusDays(1).toInstant().toEpochMilli())
        }
    }
}
