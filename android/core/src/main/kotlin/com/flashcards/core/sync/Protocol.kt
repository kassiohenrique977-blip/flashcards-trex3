package com.flashcards.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mensagens do protocolo v1 (docs/PROTOCOL.md). As chaves são curtas porque tudo passa
// pelo Bluetooth até o relógio; o formato é o mesmo de watch/lib/*.js e está fixado em
// shared/protocol-examples.json, conferido pelos testes Kotlin e JS.

const val PROTOCOL_VERSION = 1

/** Campos nulos e `del = false` não são enviados: menos bytes no BLE. */
val ProtocolJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

@Serializable
data class HelloResponse(
    val protocol: Int,
    val serverId: String,
    val serverTime: Long,
    /** O celular já conhece este relógio (sincronizou antes). */
    val deviceKnown: Boolean,
)

/** Resposta registrada no relógio, no formato compacto da outbox. */
@Serializable
data class WireReview(
    val id: String,
    @SerialName("c") val cardId: String,
    @SerialName("k") val deckId: String,
    /** 1 = AGAIN ... 4 = EASY */
    @SerialName("r") val rating: Int,
    @SerialName("t") val reviewedAt: Long,
    @SerialName("ms") val durationMs: Long = 0,
    @SerialName("sid") val sessionId: String? = null,
    @SerialName("ps") val previousState: Int = 0,
    @SerialName("ns") val newState: Int = 0,
    @SerialName("pi") val previousInterval: Int = 0,
    @SerialName("ni") val newInterval: Int = 0,
    @SerialName("a") val algorithm: String = "sm2-v1",
)

@Serializable
data class PushRequest(
    val deviceId: String,
    val reviews: List<WireReview>,
)

@Serializable
data class RejectedReview(
    val id: String,
    val reason: String,
)

@Serializable
data class PushResponse(
    /** Gravadas agora. */
    val accepted: List<String>,
    /** Já estavam gravadas (reenvio depois de uma queda). */
    val duplicates: List<String>,
    /** Nunca serão aceitas (cartão apagado, dados inválidos); o relógio deve descartá-las. */
    val rejected: List<RejectedReview>,
)

/**
 * Uma mudança para o relógio: deck (`t = "d"`) ou cartão (`t = "c"`).
 * Com `del = true`, só `t`, `id` e (para cartões) `k` são enviados.
 */
@Serializable
data class WireChange(
    @SerialName("t") val type: String,
    val id: String,
    @SerialName("del") val deleted: Boolean = false,
    @SerialName("n") val name: String? = null,
    @SerialName("k") val deckId: String? = null,
    @SerialName("f") val front: String? = null,
    @SerialName("b") val back: String? = null,
    @SerialName("s") val state: Int? = null,
    @SerialName("d") val dueAt: Long? = null,
    @SerialName("iv") val intervalDays: Int? = null,
    @SerialName("ef") val easeFactor: Double? = null,
    @SerialName("r") val repetitions: Int? = null,
    @SerialName("l") val lapses: Int? = null,
    @SerialName("ls") val learningStep: Int? = null,
    @SerialName("lr") val lastReviewedAt: Long? = null,
) {
    companion object {
        const val DECK = "d"
        const val CARD = "c"
    }
}

@Serializable
data class WireSchedulerConfig(
    val learningStepsMinutes: List<Double>,
    val relearningStepsMinutes: List<Double>,
    val graduatingIntervalDays: Int,
    val easyIntervalDays: Int,
    val startingEase: Double,
    val minimumEase: Double,
    val easyBonus: Double,
    val hardMultiplier: Double,
    val lapseIntervalMultiplier: Double,
    val minimumLapseIntervalDays: Int,
    val maximumIntervalDays: Int,
)

@Serializable
data class WireSettings(
    val newCardsPerDay: Int,
    val maxReviewsPerDay: Int,
    val dayCutoffHour: Int,
    val scheduler: WireSchedulerConfig,
)

@Serializable
data class ChangesResponse(
    val changes: List<WireChange>,
    /** Cursor a enviar no próximo pedido (a maior versão desta página). */
    val nextSince: Long,
    val hasMore: Boolean,
    val settings: WireSettings,
)

@Serializable
data class AckRequest(
    val deviceId: String,
    val cursor: Long,
)

@Serializable
data class AckResponse(val ok: Boolean)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)
