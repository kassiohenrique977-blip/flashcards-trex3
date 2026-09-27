package com.flashcards.core.study

import com.flashcards.core.model.CardState
import com.flashcards.core.srs.TimeUnits.DAY_MS
import com.flashcards.core.srs.TimeUnits.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlannerTest {

    private val now = 1_700_000_000_000L
    private val dayEnd = now + 6 * 60 * MINUTE_MS

    private val cards = listOf(
        card("new-2", CardState.NEW, dueAt = 2),
        card("review-tomorrow", CardState.REVIEW, dueAt = dayEnd + 1),
        card("learning-later", CardState.LEARNING, dueAt = now + 60 * MINUTE_MS),
        card("review-late", CardState.REVIEW, dueAt = now - 3 * DAY_MS),
        card("new-1", CardState.NEW, dueAt = 1),
        card("relearning-due", CardState.RELEARNING, dueAt = now - MINUTE_MS),
        card("review-tonight", CardState.REVIEW, dueAt = now + 2 * 60 * MINUTE_MS),
        card("learning-soon", CardState.LEARNING, dueAt = now + 5 * MINUTE_MS),
        card("new-3", CardState.NEW, dueAt = 3),
    )

    @Test
    fun `queue is learning then reviews then new cards`() {
        val plan = StudyPlanner.plan(cards, now, dayEnd, StudySettings(newCardsPerDay = 2))

        assertEquals(
            listOf("relearning-due", "learning-soon", "review-late", "review-tonight", "new-1", "new-2"),
            plan.map { it.id },
        )
    }

    @Test
    fun `new cards already introduced today count against the daily limit`() {
        val plan = StudyPlanner.plan(cards, now, dayEnd, StudySettings(newCardsPerDay = 2), newIntroducedToday = 1)

        assertEquals(listOf("new-1"), plan.filter { it.scheduling.state == CardState.NEW }.map { it.id })
    }

    @Test
    fun `review limit keeps the most overdue first`() {
        val plan = StudyPlanner.plan(
            cards,
            now,
            dayEnd,
            StudySettings(newCardsPerDay = 0, maxReviewsPerDay = 5),
            reviewsDoneToday = 4,
        )

        assertEquals(listOf("relearning-due", "learning-soon", "review-late"), plan.map { it.id })
    }

    @Test
    fun `nothing to study gives an empty queue`() {
        val plan = StudyPlanner.plan(
            listOf(card("review-tomorrow", CardState.REVIEW, dueAt = dayEnd + 1)),
            now,
            dayEnd,
            StudySettings(),
        )

        assertEquals(emptyList<String>(), plan.map { it.id })
    }
}
