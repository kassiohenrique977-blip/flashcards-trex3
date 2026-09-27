package com.flashcards.core.sync

import com.flashcards.core.util.Clock
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRoutesTest {

    private val backend = FakeSyncBackend()
    private val events = mutableListOf<SyncEvent>()
    private val auth = PairingAuth({ CODE }, Clock { 0L }, maxFailures = 3)

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { syncModule(backend, auth) { events += it } }
        block()
    }

    private suspend fun ApplicationTestBuilder.getAuthed(path: String): HttpResponse =
        client.get(path) { header(PAIR_TOKEN_HEADER, CODE) }

    private suspend fun ApplicationTestBuilder.postAuthed(path: String, json: String): HttpResponse =
        client.post(path) {
            header(PAIR_TOKEN_HEADER, CODE)
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private suspend inline fun <reified T> HttpResponse.decode(): T = ProtocolJson.decodeFromString(bodyAsText())

    @Test
    fun `hello without the pairing code is refused`() = routesTest {
        val response = client.get("/v1/hello?deviceId=w1")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("UNAUTHORIZED", response.decode<ErrorResponse>().error)
        assertEquals(listOf<SyncEvent>(SyncEvent.Unauthorized), events)
    }

    @Test
    fun `hello with the code returns the protocol version`() = routesTest {
        val response = getAuthed("/v1/hello?deviceId=w1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(PROTOCOL_VERSION, response.decode<HelloResponse>().protocol)
        assertEquals(listOf<SyncEvent>(SyncEvent.Hello("w1")), events)
    }

    @Test
    fun `repeated wrong codes lock the server`() = routesTest {
        repeat(3) { client.get("/v1/hello?deviceId=w1") { header(PAIR_TOKEN_HEADER, "000000") } }

        val response = getAuthed("/v1/hello?deviceId=w1")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("LOCKED", response.decode<ErrorResponse>().error)
    }

    @Test
    fun `missing deviceId is a bad request`() = routesTest {
        assertEquals(HttpStatusCode.BadRequest, getAuthed("/v1/hello").status)
        assertEquals(HttpStatusCode.BadRequest, getAuthed("/v1/hello?deviceId=" + "x".repeat(101)).status)
    }

    @Test
    fun `push passes the reviews to the backend`() = routesTest {
        val response = postAuthed(
            "/v1/reviews",
            """{"deviceId":"w1","reviews":[{"id":"r1","c":"c1","k":"d1","r":3,"t":5,"ps":0,"ns":1}]}""",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("r1"), response.decode<PushResponse>().accepted)
        assertEquals("c1", backend.pushed.single().reviews.single().cardId)
        assertEquals(SyncEvent.Pushed("w1", accepted = 1, duplicates = 0, rejected = 0), events.last())
    }

    @Test
    fun `invalid JSON is a bad request and never reaches the backend`() = routesTest {
        val response = postAuthed("/v1/reviews", """{"deviceId":"w1","reviews":[{"id":1}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(backend.pushed.isEmpty())
    }

    @Test
    fun `too many reviews in one push are refused`() = routesTest {
        val reviews = (1..MAX_REVIEWS_PER_PUSH + 1).joinToString(",") {
            """{"id":"r$it","c":"c","k":"d","r":3,"t":1}"""
        }

        val response = postAuthed("/v1/reviews", """{"deviceId":"w1","reviews":[$reviews]}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("TOO_MANY_REVIEWS", response.decode<ErrorResponse>().error)
    }

    @Test
    fun `changes reads since and clamps the page size`() = routesTest {
        val response = getAuthed("/v1/changes?deviceId=w1&since=7&limit=100000")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Triple("w1", 7L, MAX_CHANGES_PER_PAGE), backend.pulls.single())
    }

    @Test
    fun `changes without a valid since is a bad request`() = routesTest {
        assertEquals(HttpStatusCode.BadRequest, getAuthed("/v1/changes?deviceId=w1").status)
        assertEquals(HttpStatusCode.BadRequest, getAuthed("/v1/changes?deviceId=w1&since=-1").status)
    }

    @Test
    fun `ack is stored and confirmed`() = routesTest {
        val response = postAuthed("/v1/ack", """{"deviceId":"w1","cursor":42}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AckResponse(ok = true), response.decode<AckResponse>())
        assertEquals(AckRequest("w1", 42), backend.acks.single())
    }

    @Test
    fun `backend failure becomes a server error without leaking details`() = routesTest {
        backend.failWith = IllegalStateException("disco cheio")

        val response = getAuthed("/v1/changes?deviceId=w1&since=0")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("SERVER_ERROR", response.decode<ErrorResponse>().error)
        assertEquals(SyncEvent.Failed("changes", "disco cheio"), events.last())
    }

    private companion object {
        const val CODE = "123456"
    }
}

/** Backend em memória que só registra o que recebeu. */
class FakeSyncBackend : SyncBackend {
    val pushed = mutableListOf<PushRequest>()
    val pulls = mutableListOf<Triple<String, Long, Int>>()
    val acks = mutableListOf<AckRequest>()
    var failWith: Exception? = null

    private val settings = WireSettings(
        newCardsPerDay = 20,
        maxReviewsPerDay = 200,
        dayCutoffHour = 4,
        scheduler = WireSchedulerConfig(
            learningStepsMinutes = listOf(1.0, 10.0),
            relearningStepsMinutes = listOf(10.0),
            graduatingIntervalDays = 1,
            easyIntervalDays = 4,
            startingEase = 2.5,
            minimumEase = 1.3,
            easyBonus = 1.3,
            hardMultiplier = 1.2,
            lapseIntervalMultiplier = 0.0,
            minimumLapseIntervalDays = 1,
            maximumIntervalDays = 36_500,
        ),
    )

    override suspend fun hello(deviceId: String) = HelloResponse(PROTOCOL_VERSION, "phone", 0, deviceKnown = false)

    override suspend fun push(request: PushRequest): PushResponse {
        failWith?.let { throw it }
        pushed += request
        return PushResponse(accepted = request.reviews.map { it.id }, duplicates = emptyList(), rejected = emptyList())
    }

    override suspend fun changes(deviceId: String, since: Long, limit: Int): ChangesResponse {
        failWith?.let { throw it }
        pulls += Triple(deviceId, since, limit)
        return ChangesResponse(emptyList(), nextSince = since, hasMore = false, settings = settings)
    }

    override suspend fun ack(request: AckRequest) {
        failWith?.let { throw it }
        acks += request
    }
}
