package com.flashcards.app.data

import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.data.sync.RoomSyncBackend
import com.flashcards.app.testutil.FakeClock
import com.flashcards.app.testutil.FakeSettingsRepository
import com.flashcards.app.testutil.SequentialIds
import com.flashcards.app.testutil.inMemoryDatabase
import com.flashcards.core.model.CardDraft
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.srs.Sm2Scheduler
import com.flashcards.core.sync.ChangesResponse
import com.flashcards.core.sync.HelloResponse
import com.flashcards.core.sync.PAIR_TOKEN_HEADER
import com.flashcards.core.sync.PairingAuth
import com.flashcards.core.sync.ProtocolJson
import com.flashcards.core.sync.PushResponse
import com.flashcards.core.sync.syncModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Sincronização completa por HTTP, com o banco Room de verdade: o que o side service
 * do Zepp App faz, passo a passo, com os mesmos JSON que o relógio envia.
 */
@RunWith(RobolectricTestRunner::class)
class SyncRoundTripTest {

    @Test
    fun `watch downloads, studies offline and sends its answers back`() = testApplication {
        val clock = FakeClock(1_700_000_000_000L)
        val ids = SequentialIds()
        val db = inMemoryDatabase()
        val deckId = RoomDeckRepository(db, clock, ids).createDeck("Inglês").id
        RoomCardRepository(db, clock, ids).addCards(deckId, listOf(CardDraft("Hello", "Olá"), CardDraft("Bye", "Tchau")))
        val backend = RoomSyncBackend(db, Sm2Scheduler(), SchedulerConfig(), FakeSettingsRepository(), { "phone-1" }, clock)
        application { syncModule(backend, PairingAuth({ "123456" }, clock)) }

        suspend fun get(path: String) = client.get(path) { header(PAIR_TOKEN_HEADER, "123456") }
        suspend fun post(path: String, json: String) = client.post(path) {
            header(PAIR_TOKEN_HEADER, "123456")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

        // 1. hello
        val hello = ProtocolJson.decodeFromString<HelloResponse>(get("/v1/hello?deviceId=w1").bodyAsText())
        assertFalse(hello.deviceKnown)

        // 2. primeiro download
        val first = ProtocolJson.decodeFromString<ChangesResponse>(get("/v1/changes?deviceId=w1&since=0&limit=30").bodyAsText())
        assertEquals(3, first.changes.size)
        val cardId = first.changes[1].id

        // 3. o relógio responde FÁCIL offline e envia (mesmo formato de watch/lib/study.js)
        val pushBody = """{"deviceId":"w1","reviews":[{"id":"r1","c":"$cardId","k":"$deckId","r":4,"t":1700000060000,""" +
            """"ms":4000,"sid":"s1","ps":0,"ns":2,"pi":0,"ni":4,"a":"sm2-v1"}]}"""
        val pushed = ProtocolJson.decodeFromString<PushResponse>(post("/v1/reviews", pushBody).bodyAsText())
        assertEquals(listOf("r1"), pushed.accepted)

        // 4. recebe de volta só o cartão recalculado
        val second = ProtocolJson.decodeFromString<ChangesResponse>(
            get("/v1/changes?deviceId=w1&since=${first.nextSince}&limit=30").bodyAsText(),
        )
        val updated = second.changes.single()
        assertEquals(cardId, updated.id)
        assertEquals(2, updated.state)
        assertEquals(4, updated.intervalDays)

        // 5. ack
        assertEquals(HttpStatusCode.OK, post("/v1/ack", """{"deviceId":"w1","cursor":${second.nextSince}}""").status)
        assertTrue(ProtocolJson.decodeFromString<HelloResponse>(get("/v1/hello?deviceId=w1").bodyAsText()).deviceKnown)

        db.close()
    }
}
