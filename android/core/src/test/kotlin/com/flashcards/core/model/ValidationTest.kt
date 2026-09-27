package com.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ValidationTest {

    @Test
    fun `deck name is trimmed`() {
        assertEquals("Inglês", Validation.deckName("  Inglês \n"))
    }

    @Test
    fun `blank deck name is rejected with a user message`() {
        val error = assertThrows(ValidationException::class.java) { Validation.deckName("   ") }
        assertEquals("Dê um nome ao deck.", error.message)
    }

    @Test
    fun `deck name above the limit is rejected`() {
        assertThrows(ValidationException::class.java) {
            Validation.deckName("x".repeat(Validation.DECK_NAME_MAX + 1))
        }
    }

    @Test
    fun `empty description is allowed`() {
        assertEquals("", Validation.deckDescription("  "))
    }

    @Test
    fun `card draft is trimmed and tags normalized`() {
        val clean = Validation.card(CardDraft(" Hello ", "\tOlá ", listOf("ingles", "Ingles")))
        assertEquals(CardDraft("Hello", "Olá", listOf("ingles")), clean)
    }

    @Test
    fun `blank card sides are rejected with specific messages`() {
        val front = assertThrows(ValidationException::class.java) { Validation.card(CardDraft(" ", "b")) }
        val back = assertThrows(ValidationException::class.java) { Validation.card(CardDraft("a", "")) }
        assertEquals("Preencha a frente do cartão.", front.message)
        assertEquals("Preencha o verso do cartão.", back.message)
    }

    @Test
    fun `card text above the limit is rejected`() {
        assertThrows(ValidationException::class.java) {
            Validation.cardBack("x".repeat(Validation.CARD_TEXT_MAX + 1))
        }
    }
}
