package com.flashcards.core.sync

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Deck
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.study.StudySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WireMappingTest {

    @Test
    fun `card goes to the watch with its schedule and without tags or dates`() {
        val card = Card(
            id = "c1",
            deckId = "d1",
            front = "Hello",
            back = "Olá",
            tags = listOf("ingles"),
            createdAt = 1,
            updatedAt = 2,
            scheduling = SchedulingState(
                state = CardState.REVIEW,
                dueAt = 99,
                intervalDays = 4,
                easeFactor = 2.65,
                repetitions = 3,
                lapses = 1,
                learningStep = 0,
                lastReviewedAt = 50,
            ),
        )

        assertEquals(
            """{"t":"c","id":"c1","k":"d1","f":"Hello","b":"Olá","s":2,"d":99,"iv":4,"ef":2.65,"r":3,"l":1,"ls":0,"lr":50}""",
            ProtocolJson.encodeToString(WireChange.serializer(), card.toWireChange()),
        )
    }

    @Test
    fun `deck and deletions are minimal`() {
        val deck = Deck("d1", "Inglês", "descrição fica no celular", 0, 0, syncToWatch = true)

        assertEquals("""{"t":"d","id":"d1","n":"Inglês"}""", ProtocolJson.encodeToString(WireChange.serializer(), deck.toWireChange()))
        assertEquals("""{"t":"c","id":"c1","del":true,"k":"d1"}""", ProtocolJson.encodeToString(WireChange.serializer(), deletedCardChange("c1", "d1")))
    }

    @Test
    fun `watch review becomes a review from the watch with the phone deck`() {
        val wire = WireReview(
            id = "r1", cardId = "c1", deckId = "outro", rating = 4, reviewedAt = 1000,
            durationMs = 3000, sessionId = "s1", previousState = 0, newState = 2, previousInterval = 0, newInterval = 4,
        )

        val review = wire.toReview(deckId = "d1", deviceId = "watch-1")

        assertEquals(Rating.EASY, review.rating)
        assertEquals("d1", review.deckId)
        assertEquals(CardState.NEW, review.previousState)
        assertEquals(CardState.REVIEW, review.newState)
        assertEquals(ReviewSource.WATCH, review.source)
        assertEquals("watch-1", review.deviceId)
    }

    @Test
    fun `invalid codes or time are refused`() {
        val valid = WireReview(id = "r1", cardId = "c1", deckId = "d1", rating = 3, reviewedAt = 1000)

        assertThrows(IllegalArgumentException::class.java) { valid.copy(rating = 7).toReview("d1", "w") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(newState = 9).toReview("d1", "w") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(reviewedAt = 0).toReview("d1", "w") }
    }

    @Test
    fun `settings carry the scheduler so both sides compute the same intervals`() {
        val wire = StudySettings(newCardsPerDay = 15).toWire(SchedulerConfig())

        assertEquals(15, wire.newCardsPerDay)
        assertEquals(listOf(1.0, 10.0), wire.scheduler.learningStepsMinutes)
        assertEquals(36_500, wire.scheduler.maximumIntervalDays)
    }

    @Test
    fun `size estimate grows with the text`() {
        val short = WireChange(type = WireChange.CARD, id = "c", front = "a", back = "b")
        val long = short.copy(front = "x".repeat(1_000))

        assertEquals(122, short.estimatedChars())
        assertEquals(1_121, long.estimatedChars())
    }
}
