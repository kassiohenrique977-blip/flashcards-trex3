package com.flashcards.core.repository

import com.flashcards.core.model.Deck
import com.flashcards.core.model.DeckSummary
import kotlinx.coroutines.flow.Flow

interface DeckRepository {

    /** Decks em ordem alfabética, com contagens de cartões vencidos em [now]. */
    fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>>

    fun observeDeck(deckId: String): Flow<Deck?>

    suspend fun getDeck(deckId: String): Deck?

    /** @throws com.flashcards.core.model.ValidationException se o nome for inválido. */
    suspend fun createDeck(name: String, description: String = ""): Deck

    /** @throws NoSuchElementException se o deck não existir. */
    suspend fun updateDeck(deckId: String, name: String, description: String): Deck

    /** Liga ou desliga o envio do deck para o relógio. */
    suspend fun setSyncToWatch(deckId: String, enabled: Boolean)

    /** Apaga o deck com seus cartões e revisões. Não faz nada se ele não existir. */
    suspend fun deleteDeck(deckId: String)
}
