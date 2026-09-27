package com.flashcards.core.repository

import com.flashcards.core.model.Card
import com.flashcards.core.model.CardDraft
import kotlinx.coroutines.flow.Flow

interface CardRepository {

    /** Cartões do deck, do mais novo para o mais antigo. */
    fun observeCards(deckId: String): Flow<List<Card>>

    suspend fun getCard(cardId: String): Card?

    /**
     * @throws com.flashcards.core.model.ValidationException se frente ou verso forem inválidos.
     * @throws NoSuchElementException se o deck não existir.
     */
    suspend fun createCard(deckId: String, draft: CardDraft): Card

    /** Troca o conteúdo mantendo o progresso de estudo. */
    suspend fun updateCard(cardId: String, draft: CardDraft): Card

    /** Apaga o cartão e suas revisões. Não faz nada se ele não existir. */
    suspend fun deleteCard(cardId: String)

    /** Insere vários cartões numa única transação, na ordem recebida. Retorna quantos entraram. */
    suspend fun addCards(deckId: String, drafts: List<CardDraft>): Int
}
