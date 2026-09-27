package com.flashcards.app.data.repository

import androidx.room.withTransaction
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.core.model.Card
import com.flashcards.core.model.EntityType
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.study.StudyDayWindow
import com.flashcards.core.study.StudyPlanner
import com.flashcards.core.study.StudySettings
import com.flashcards.core.util.Clock

class RoomStudyRepository(
    private val db: FlashcardsDatabase,
    clock: Clock,
) : StudyRepository {

    private val cardDao = db.cardDao()
    private val reviewDao = db.reviewDao()
    private val changes = ChangeTracker(db.syncStateDao(), clock)

    override suspend fun loadSession(
        deckId: String,
        now: Long,
        day: StudyDayWindow,
        settings: StudySettings,
    ): List<Card> {
        val cards = cardDao.getByDeck(deckId).map { it.toDomain() }
        return StudyPlanner.plan(
            cards = cards,
            now = now,
            dayEnd = day.end,
            settings = settings,
            newIntroducedToday = reviewDao.countNewIntroducedSince(deckId, day.start),
            reviewsDoneToday = reviewDao.countReviewsSince(deckId, day.start),
        )
    }

    override suspend fun recordAnswer(review: Review, scheduling: SchedulingState): Boolean =
        db.withTransaction {
            val card = cardDao.get(review.cardId) ?: return@withTransaction false
            if (reviewDao.insert(review.toEntity()) == -1L) return@withTransaction false
            cardDao.update(card.withScheduling(scheduling))
            // O relógio precisa receber o novo agendamento na próxima sincronização.
            changes.record(EntityType.CARD, card.id, parentId = card.deckId)
            true
        }
}
