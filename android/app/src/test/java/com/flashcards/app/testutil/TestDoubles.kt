package com.flashcards.app.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.db.ReviewEntity
import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator

class FakeClock(var time: Long = 1_000L) : Clock {
    override fun now(): Long = time
}

/** IDs previsíveis ("id-1", "id-2"...) para os testes compararem valores exatos. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

fun inMemoryDatabase(): FlashcardsDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        FlashcardsDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()

fun testReview(
    id: String,
    cardId: String,
    deckId: String,
    reviewedAt: Long = 1_000,
    rating: Rating = Rating.GOOD,
) = ReviewEntity(
    id = id,
    cardId = cardId,
    deckId = deckId,
    rating = rating,
    reviewedAt = reviewedAt,
    previousInterval = 0,
    newInterval = 1,
    previousState = CardState.NEW,
    newState = CardState.LEARNING,
    durationMs = 3_000,
    source = ReviewSource.PHONE,
    deviceId = null,
    sessionId = null,
    algorithm = "test",
)
