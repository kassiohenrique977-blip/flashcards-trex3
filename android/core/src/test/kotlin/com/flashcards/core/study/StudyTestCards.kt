package com.flashcards.core.study

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator

internal fun card(
    id: String,
    state: CardState = CardState.NEW,
    dueAt: Long = 0,
    intervalDays: Int = if (state == CardState.REVIEW) 5 else 0,
) = Card(
    id = id,
    deckId = "deck",
    front = "frente $id",
    back = "verso $id",
    tags = emptyList(),
    createdAt = 0,
    updatedAt = 0,
    scheduling = SchedulingState(state = state, dueAt = dueAt, intervalDays = intervalDays, repetitions = 2),
)

internal class TestClock(var time: Long) : Clock {
    override fun now(): Long = time
}

internal class TestIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}
