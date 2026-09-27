package com.flashcards.app.sync

import com.flashcards.core.sync.SyncEvent
import com.flashcards.core.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WatchSyncStatus(
    val running: Boolean = false,
    /** Motivo de o servidor não ter ligado (por exemplo, porta em uso). */
    val startError: String? = null,
    /** Última atividade do relógio, usada também para desligar o servidor ocioso. */
    val lastEventAt: Long? = null,
    val message: String? = null,
    /** Respostas recebidas desde que a sincronização foi ligada. */
    val receivedReviews: Int = 0,
    /** Mudanças enviadas desde que a sincronização foi ligada. */
    val sentChanges: Int = 0,
    val lastCompletedAt: Long? = null,
)

/**
 * Estado da sincronização para a tela "Relógio" e o serviço. Recebe os eventos do
 * servidor (que chegam de threads do Ktor; o StateFlow é seguro para isso).
 */
class WatchSyncMonitor(private val clock: Clock) {

    private val _status = MutableStateFlow(WatchSyncStatus())
    val status: StateFlow<WatchSyncStatus> = _status.asStateFlow()

    fun onStarted() = _status.update {
        WatchSyncStatus(running = true, message = "Aguardando o relógio", lastCompletedAt = it.lastCompletedAt)
    }

    fun onStopped() = _status.update { it.copy(running = false, message = null) }

    fun onFailedToStart(reason: String?) = _status.update {
        it.copy(
            running = false,
            startError = "Não foi possível abrir a porta 8765" + (reason?.let { detail -> " ($detail)" } ?: "") +
                ". Feche outros apps que usem essa porta e tente de novo.",
        )
    }

    fun onEvent(event: SyncEvent) {
        val now = clock.now()
        _status.update { s ->
            when (event) {
                is SyncEvent.Hello -> s.copy(lastEventAt = now, message = "Relógio conectado")
                is SyncEvent.Pushed -> s.copy(
                    lastEventAt = now,
                    receivedReviews = s.receivedReviews + event.accepted,
                    message = "Recebendo respostas do relógio",
                )
                is SyncEvent.Pulled -> s.copy(
                    lastEventAt = now,
                    sentChanges = s.sentChanges + event.changes,
                    message = "Enviando cartões ao relógio",
                )
                is SyncEvent.Acked -> s.copy(lastEventAt = now, lastCompletedAt = now, message = "Sincronização concluída")
                SyncEvent.Unauthorized -> s.copy(
                    lastEventAt = now,
                    message = "O relógio usou um código de pareamento errado",
                )
                is SyncEvent.Failed -> s.copy(lastEventAt = now, message = "Erro ao sincronizar: ${event.message}")
            }
        }
    }
}
