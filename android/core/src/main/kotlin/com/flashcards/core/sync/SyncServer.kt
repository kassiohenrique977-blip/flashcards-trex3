package com.flashcards.core.sync

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

/**
 * Servidor HTTP que o side service do Zepp App chama. Escuta só em 127.0.0.1, então
 * nada fica exposto na rede Wi-Fi, e só enquanto a sincronização está ligada.
 */
class SyncServer(
    private val backend: SyncBackend,
    private val auth: PairingAuth,
    private val events: (SyncEvent) -> Unit = {},
) {
    private var server: EmbeddedServer<*, *>? = null

    val isRunning: Boolean
        @Synchronized get() = server != null

    /** @throws java.net.BindException se a porta já estiver em uso. */
    @Synchronized
    fun start(port: Int = DEFAULT_PORT) {
        if (server != null) return
        server = embeddedServer(CIO, port = port, host = LOOPBACK) {
            syncModule(backend, auth, events)
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val LOOPBACK = "127.0.0.1"
    }
}
