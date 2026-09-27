package com.flashcards.app.ui.deck

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardState
import com.flashcards.core.model.SchedulingState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckStatsTest {

    private fun card(state: CardState, dueAt: Long) = Card(
        id = "$state-$dueAt",
        deckId = "d",
        front = "f",
        back = "b",
        tags = emptyList(),
        createdAt = 0,
        updatedAt = 0,
        scheduling = SchedulingState(state = state, dueAt = dueAt),
    )

    @Test
    fun `stats group cards by state and count what is due now`() {
        val stats = statsOf(
            listOf(
                card(CardState.NEW, 0),
                card(CardState.LEARNING, 50),
                card(CardState.RELEARNING, 500),
                card(CardState.REVIEW, 100),
                card(CardState.REVIEW, 900),
            ),
            now = 100,
        )

        assertEquals(DeckStats(total = 5, new = 1, learning = 2, review = 2, due = 2), stats)
    }
}
