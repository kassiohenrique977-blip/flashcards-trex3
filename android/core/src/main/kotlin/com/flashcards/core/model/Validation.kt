package com.flashcards.core.model

/** Erro de validação com mensagem pronta para mostrar ao usuário. */
class ValidationException(message: String) : IllegalArgumentException(message)

object Validation {
    const val DECK_NAME_MAX = 80
    const val DECK_DESCRIPTION_MAX = 500
    const val CARD_TEXT_MAX = 2_000

    fun deckName(name: String): String {
        val value = name.trim()
        if (value.isEmpty()) throw ValidationException("Dê um nome ao deck.")
        if (value.length > DECK_NAME_MAX) {
            throw ValidationException("O nome do deck pode ter até $DECK_NAME_MAX caracteres.")
        }
        return value
    }

    fun deckDescription(description: String): String {
        val value = description.trim()
        if (value.length > DECK_DESCRIPTION_MAX) {
            throw ValidationException("A descrição pode ter até $DECK_DESCRIPTION_MAX caracteres.")
        }
        return value
    }

    fun cardFront(text: String): String = cardText(
        text,
        empty = "Preencha a frente do cartão.",
        tooLong = "A frente do cartão pode ter até $CARD_TEXT_MAX caracteres.",
    )

    fun cardBack(text: String): String = cardText(
        text,
        empty = "Preencha o verso do cartão.",
        tooLong = "O verso do cartão pode ter até $CARD_TEXT_MAX caracteres.",
    )

    /** Valida e normaliza um rascunho de cartão. */
    fun card(draft: CardDraft): CardDraft = CardDraft(
        front = cardFront(draft.front),
        back = cardBack(draft.back),
        tags = Tags.normalize(draft.tags),
    )

    private fun cardText(text: String, empty: String, tooLong: String): String {
        val value = text.trim()
        if (value.isEmpty()) throw ValidationException(empty)
        if (value.length > CARD_TEXT_MAX) throw ValidationException(tooLong)
        return value
    }
}
