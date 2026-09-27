package com.flashcards.app.ui.navigation

import kotlinx.serialization.Serializable

// Rotas type-safe do Navigation Compose. A Home também é a lista de decks.

@Serializable
data object HomeRoute

@Serializable
data class DeckDetailRoute(val deckId: String)

/** [cardId] nulo cria um cartão novo; preenchido edita um existente. */
@Serializable
data class CardEditorRoute(val deckId: String, val cardId: String? = null)

/** [deckId] nulo importa para um deck novo (ou escolhido na tela). */
@Serializable
data class ImportRoute(val deckId: String? = null)

@Serializable
data class StudyRoute(val deckId: String)

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

@Serializable
data object WatchSyncRoute
