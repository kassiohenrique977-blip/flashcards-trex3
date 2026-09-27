package com.flashcards.core.sync

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.coroutines.cancellation.CancellationException

const val PAIR_TOKEN_HEADER = "X-Pair-Token"
const val MAX_BODY_BYTES = 1_000_000L
const val MAX_REVIEWS_PER_PUSH = 500
const val MAX_CHANGES_PER_PAGE = 200
private const val MAX_DEVICE_ID_LENGTH = 100

/**
 * Rotas do protocolo v1. Toda rota exige o código de pareamento no cabeçalho
 * [PAIR_TOKEN_HEADER] e responde erros como [ErrorResponse].
 */
fun Application.syncModule(
    backend: SyncBackend,
    auth: PairingAuth,
    events: (SyncEvent) -> Unit = {},
) {
    install(ContentNegotiation) { json(ProtocolJson) }

    routing {
        route("/v1") {
            get("/hello") {
                if (!call.authorize(auth, events)) return@get
                val deviceId = call.validDeviceId(call.request.queryParameters["deviceId"]) ?: return@get
                call.handle("hello", events) {
                    events(SyncEvent.Hello(deviceId))
                    backend.hello(deviceId)
                }
            }

            post("/reviews") {
                if (!call.authorize(auth, events)) return@post
                val request = call.receiveOrReject<PushRequest>() ?: return@post
                call.validDeviceId(request.deviceId) ?: return@post
                if (request.reviews.size > MAX_REVIEWS_PER_PUSH) {
                    call.reject(HttpStatusCode.BadRequest, "TOO_MANY_REVIEWS", "Máximo de $MAX_REVIEWS_PER_PUSH por envio.")
                    return@post
                }
                call.handle("reviews", events) {
                    backend.push(request).also {
                        events(SyncEvent.Pushed(request.deviceId, it.accepted.size, it.duplicates.size, it.rejected.size))
                    }
                }
            }

            get("/changes") {
                if (!call.authorize(auth, events)) return@get
                val params = call.request.queryParameters
                val deviceId = call.validDeviceId(params["deviceId"]) ?: return@get
                val since = params["since"]?.toLongOrNull()
                if (since == null || since < 0) {
                    call.reject(HttpStatusCode.BadRequest, "BAD_REQUEST", "Parâmetro since inválido.")
                    return@get
                }
                val limit = (params["limit"]?.toIntOrNull() ?: 30).coerceIn(1, MAX_CHANGES_PER_PAGE)
                call.handle("changes", events) {
                    backend.changes(deviceId, since, limit).also {
                        events(SyncEvent.Pulled(deviceId, it.changes.size, it.hasMore))
                    }
                }
            }

            post("/ack") {
                if (!call.authorize(auth, events)) return@post
                val request = call.receiveOrReject<AckRequest>() ?: return@post
                call.validDeviceId(request.deviceId) ?: return@post
                if (request.cursor < 0) {
                    call.reject(HttpStatusCode.BadRequest, "BAD_REQUEST", "Cursor inválido.")
                    return@post
                }
                call.handle("ack", events) {
                    backend.ack(request)
                    events(SyncEvent.Acked(request.deviceId, request.cursor))
                    AckResponse(ok = true)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.authorize(auth: PairingAuth, events: (SyncEvent) -> Unit): Boolean =
    when (auth.check(request.headers[PAIR_TOKEN_HEADER])) {
        AuthResult.OK -> true
        AuthResult.DENIED -> {
            events(SyncEvent.Unauthorized)
            reject(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Código de pareamento inválido.")
            false
        }
        AuthResult.LOCKED -> {
            events(SyncEvent.Unauthorized)
            reject(HttpStatusCode.TooManyRequests, "LOCKED", "Muitas tentativas. Aguarde alguns minutos.")
            false
        }
    }

private suspend fun ApplicationCall.validDeviceId(deviceId: String?): String? {
    if (deviceId.isNullOrBlank() || deviceId.length > MAX_DEVICE_ID_LENGTH) {
        reject(HttpStatusCode.BadRequest, "BAD_REQUEST", "deviceId inválido.")
        return null
    }
    return deviceId
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? {
    if ((request.contentLength() ?: 0L) > MAX_BODY_BYTES) {
        reject(HttpStatusCode.PayloadTooLarge, "TOO_LARGE", "Requisição grande demais.")
        return null
    }
    return try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reject(HttpStatusCode.BadRequest, "BAD_REQUEST", "JSON inválido.")
        null
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.handle(
    endpoint: String,
    noinline events: (SyncEvent) -> Unit,
    block: () -> T,
) {
    val result = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        events(SyncEvent.Failed(endpoint, e.message ?: e::class.java.simpleName))
        reject(HttpStatusCode.InternalServerError, "SERVER_ERROR", "Erro ao processar a sincronização.")
        return
    }
    respond(result)
}

private suspend fun ApplicationCall.reject(status: HttpStatusCode, error: String, message: String) {
    respond(status, ErrorResponse(error, message))
}
