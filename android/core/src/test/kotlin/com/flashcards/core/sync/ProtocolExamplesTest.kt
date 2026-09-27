package com.flashcards.core.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Confere os DTOs Kotlin contra shared/protocol-examples.json, o mesmo arquivo usado
 * pelos testes do relógio. Mensagens que o celular envia precisam sair idênticas
 * (mesmas chaves curtas, sem nulos); as que ele recebe precisam ser lidas sem perda.
 */
class ProtocolExamplesTest {

    private val examples: JsonObject = ProtocolJson.parseToJsonElement(
        File(requireNotNull(System.getProperty("protocol.examples"))).readText(),
    ).jsonObject

    private fun example(vararg path: String): JsonElement =
        path.fold(examples as JsonElement) { element, key -> element.jsonObject.getValue(key) }

    /** Decodifica e recodifica: o JSON precisa sair igual ao exemplo. */
    private fun <T> assertRoundTrip(serializer: KSerializer<T>, vararg path: String) {
        val json = example(*path)
        val decoded = ProtocolJson.decodeFromJsonElement(serializer, json)
        assertEquals(json, ProtocolJson.encodeToJsonElement(serializer, decoded))
    }

    @Test
    fun `messages sent by the phone match the examples exactly`() {
        assertRoundTrip(HelloResponse.serializer(), "hello", "response")
        assertRoundTrip(PushResponse.serializer(), "push", "response")
        assertRoundTrip(ChangesResponse.serializer(), "changes", "response")
        assertRoundTrip(AckResponse.serializer(), "ack", "response")
        assertRoundTrip(ErrorResponse.serializer(), "error")
    }

    @Test
    fun `review sent by the watch is read with every field`() {
        val request = ProtocolJson.decodeFromJsonElement(PushRequest.serializer(), example("push", "request"))
        val review = request.reviews.single()

        assertEquals("card-1", review.cardId)
        assertEquals("deck-1", review.deckId)
        assertEquals(3, review.rating)
        assertEquals(1_700_000_004_000L, review.reviewedAt)
        assertEquals(4_000L, review.durationMs)
        assertEquals(0, review.previousState)
        assertEquals(1, review.newState)
        assertEquals("sm2-v1", review.algorithm)
    }

    @Test
    fun `deleted entries carry only what the watch needs`() {
        val changes = ProtocolJson.decodeFromJsonElement(ChangesResponse.serializer(), example("changes", "response"))

        val deletedCard = changes.changes.single { it.id == "card-3" }
        assertEquals(WireChange(type = WireChange.CARD, id = "card-3", deleted = true, deckId = "deck-1"), deletedCard)
        assertEquals(
            """{"t":"d","id":"deck-2","del":true}""",
            ProtocolJson.encodeToString(WireChange.serializer(), WireChange(type = WireChange.DECK, id = "deck-2", deleted = true)),
        )
    }

    @Test
    fun `ack request from the watch is read`() {
        val ack = ProtocolJson.decodeFromJsonElement(AckRequest.serializer(), example("ack", "request"))

        assertEquals(42L, ack.cursor)
    }
}
