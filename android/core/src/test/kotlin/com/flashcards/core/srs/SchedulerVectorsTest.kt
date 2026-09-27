package com.flashcards.core.srs

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.SchedulingState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Confere o [Sm2Scheduler] contra `shared/srs-test-vectors.json`, o mesmo arquivo usado pelo relógio. */
class SchedulerVectorsTest {

    private val root: JsonObject = Json.parseToJsonElement(
        File(requireNotNull(System.getProperty("srs.vectors")) { "srs.vectors não definido" }).readText(),
    ).jsonObject

    private val vectorConfig = configFrom(root.getValue("config").jsonObject)
    private val scheduler = Sm2Scheduler(vectorConfig)

    @Test
    fun `default config is the one used by the vectors`() {
        assertEquals(vectorConfig, SchedulerConfig())
    }

    @Test
    fun `algorithm id matches the vectors`() {
        assertEquals(root.getValue("algorithm").jsonPrimitive.content, scheduler.id)
    }

    @Test
    fun `every vector case matches`() {
        val cases = root.getValue("cases").jsonArray
        assertTrue("sem casos no arquivo de vetores", cases.isNotEmpty())

        val failures = cases.mapNotNull { element ->
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val now = case.getValue("now").jsonPrimitive.long
            val rating = Rating.valueOf(case.getValue("rating").jsonPrimitive.content)
            val before = stateFrom(case.getValue("before").jsonObject)
            val expected = stateFrom(case.getValue("after").jsonObject).copy(lastReviewedAt = now)

            val actual = scheduler.schedule(before, rating, now)
            if (actual == expected) null else "$name\n  esperado: $expected\n  obtido:   $actual"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun stateFrom(json: JsonObject) = SchedulingState(
        state = CardState.valueOf(json.getValue("state").jsonPrimitive.content),
        dueAt = json.getValue("dueAt").jsonPrimitive.long,
        intervalDays = json.getValue("intervalDays").jsonPrimitive.int,
        easeFactor = json.getValue("easeFactor").jsonPrimitive.double,
        repetitions = json.getValue("repetitions").jsonPrimitive.int,
        lapses = json.getValue("lapses").jsonPrimitive.int,
        learningStep = json.getValue("learningStep").jsonPrimitive.int,
    )

    private fun configFrom(json: JsonObject) = SchedulerConfig(
        learningStepsMinutes = json.getValue("learningStepsMinutes").jsonArray.map { it.jsonPrimitive.double },
        relearningStepsMinutes = json.getValue("relearningStepsMinutes").jsonArray.map { it.jsonPrimitive.double },
        graduatingIntervalDays = json.getValue("graduatingIntervalDays").jsonPrimitive.int,
        easyIntervalDays = json.getValue("easyIntervalDays").jsonPrimitive.int,
        startingEase = json.getValue("startingEase").jsonPrimitive.double,
        minimumEase = json.getValue("minimumEase").jsonPrimitive.double,
        easyBonus = json.getValue("easyBonus").jsonPrimitive.double,
        hardMultiplier = json.getValue("hardMultiplier").jsonPrimitive.double,
        lapseIntervalMultiplier = json.getValue("lapseIntervalMultiplier").jsonPrimitive.double,
        minimumLapseIntervalDays = json.getValue("minimumLapseIntervalDays").jsonPrimitive.int,
        maximumIntervalDays = json.getValue("maximumIntervalDays").jsonPrimitive.int,
    )
}
