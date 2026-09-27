package com.flashcards.core.sync

/**
 * O que o servidor de sincronização precisa do banco. A implementação real (Room) fica
 * no app Android; os testes do servidor usam uma versão em memória.
 */
interface SyncBackend {

    suspend fun hello(deviceId: String): HelloResponse

    /** Grava as respostas de forma idempotente e recalcula os cartões afetados. */
    suspend fun push(request: PushRequest): PushResponse

    /** Mudanças com versão maior que [since], em ordem, até [limit] itens. */
    suspend fun changes(deviceId: String, since: Long, limit: Int): ChangesResponse

    /** O relógio confirmou que tem tudo até [AckRequest.cursor]. */
    suspend fun ack(request: AckRequest)
}

/** O que aconteceu no servidor, para a tela "Relógio" e a notificação. */
sealed interface SyncEvent {
    data class Hello(val deviceId: String) : SyncEvent
    data class Pushed(val deviceId: String, val accepted: Int, val duplicates: Int, val rejected: Int) : SyncEvent
    data class Pulled(val deviceId: String, val changes: Int, val hasMore: Boolean) : SyncEvent
    data class Acked(val deviceId: String, val cursor: Long) : SyncEvent
    data object Unauthorized : SyncEvent
    data class Failed(val endpoint: String, val message: String) : SyncEvent
}
