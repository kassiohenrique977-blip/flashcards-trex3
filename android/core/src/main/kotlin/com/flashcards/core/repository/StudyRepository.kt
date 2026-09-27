package com.flashcards.core.repository

import com.flashcards.core.model.Card
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.study.StudyDayWindow
import com.flashcards.core.study.StudySettings

interface StudyRepository {

    /** Cartões da próxima sessão do deck, já na ordem de estudo. */
    suspend fun loadSession(
        deckId: String,
        now: Long,
        day: StudyDayWindow,
        settings: StudySettings,
    ): List<Card>

    /**
     * Grava a revisão e o novo agendamento do cartão numa única transação.
     * Retorna false se a revisão já existia ou se o cartão foi apagado.
     */
    suspend fun recordAnswer(review: Review, scheduling: SchedulingState): Boolean
}
