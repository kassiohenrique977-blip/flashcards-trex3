package com.flashcards.app.testutil

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardDraft
import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import com.flashcards.core.model.Review
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.model.Validation
import com.flashcards.core.repository.CardRepository
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.study.StudyDayWindow
import com.flashcards.core.study.StudySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Repositórios em memória para testar ViewModels sem banco. Validam como os reais. */

class FakeDeckRepository : DeckRepository {

    val decks = MutableStateFlow<List<Deck>>(emptyList())
    private var next = 0

    override fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>> =
        decks.map { list -> list.map { DeckSummary(it, cardCount = 0, dueCount = 0, newCount = 0) } }

    override fun observeDeck(deckId: String): Flow<Deck?> =
        decks.map { list -> list.firstOrNull { it.id == deckId } }

    override suspend fun getDeck(deckId: String): Deck? = decks.value.firstOrNull { it.id == deckId }

    override suspend fun createDeck(name: String, description: String): Deck {
        val deck = Deck(
            id = "deck-${++next}",
            name = Validation.deckName(name),
            description = Validation.deckDescription(description),
            createdAt = 0,
            updatedAt = 0,
            syncToWatch = true,
        )
        decks.update { it + deck }
        return deck
    }

    override suspend fun updateDeck(deckId: String, name: String, description: String): Deck {
        val updated = requireNotNull(getDeck(deckId)).copy(
            name = Validation.deckName(name),
            description = Validation.deckDescription(description),
        )
        decks.update { list -> list.map { if (it.id == deckId) updated else it } }
        return updated
    }

    override suspend fun setSyncToWatch(deckId: String, enabled: Boolean) {
        decks.update { list -> list.map { if (it.id == deckId) it.copy(syncToWatch = enabled) else it } }
    }

    override suspend fun deleteDeck(deckId: String) {
        decks.update { list -> list.filterNot { it.id == deckId } }
    }
}

class FakeCardRepository : CardRepository {

    val cards = MutableStateFlow<List<Card>>(emptyList())
    private var next = 0

    override fun observeCards(deckId: String): Flow<List<Card>> =
        cards.map { list -> list.filter { it.deckId == deckId } }

    override suspend fun getCard(cardId: String): Card? = cards.value.firstOrNull { it.id == cardId }

    override suspend fun createCard(deckId: String, draft: CardDraft): Card {
        val clean = Validation.card(draft)
        val card = Card(
            id = "card-${++next}",
            deckId = deckId,
            front = clean.front,
            back = clean.back,
            tags = clean.tags,
            createdAt = 0,
            updatedAt = 0,
            scheduling = SchedulingState(),
        )
        cards.update { it + card }
        return card
    }

    override suspend fun updateCard(cardId: String, draft: CardDraft): Card {
        val clean = Validation.card(draft)
        val updated = requireNotNull(getCard(cardId)).copy(front = clean.front, back = clean.back, tags = clean.tags)
        cards.update { list -> list.map { if (it.id == cardId) updated else it } }
        return updated
    }

    override suspend fun deleteCard(cardId: String) {
        cards.update { list -> list.filterNot { it.id == cardId } }
    }

    override suspend fun addCards(deckId: String, drafts: List<CardDraft>): Int {
        val clean = drafts.map(Validation::card)
        clean.forEach { createCard(deckId, it) }
        return clean.size
    }
}

class FakeStudyRepository(private val sessionCards: List<Card>) : StudyRepository {

    val recorded = mutableListOf<Pair<Review, SchedulingState>>()

    override suspend fun loadSession(
        deckId: String,
        now: Long,
        day: StudyDayWindow,
        settings: StudySettings,
    ): List<Card> = sessionCards

    override suspend fun recordAnswer(review: Review, scheduling: SchedulingState): Boolean {
        recorded += review to scheduling
        return true
    }
}

class FakeSettingsRepository(initial: StudySettings = StudySettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<StudySettings> = state.asStateFlow()

    override suspend fun update(transform: (StudySettings) -> StudySettings) {
        state.update(transform)
    }
}
