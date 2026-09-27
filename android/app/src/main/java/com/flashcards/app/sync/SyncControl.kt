package com.flashcards.app.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

/** Liga e desliga o servidor de sincronização (o foreground service). */
interface SyncControl {
    fun start()
    fun stop()
}

class AndroidSyncControl(private val context: Context) : SyncControl {

    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, WatchSyncService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, WatchSyncService::class.java))
    }
}

/** Código de pareamento visto pela tela; implementado por [PairingStore]. */
interface PairingCodes {
    val code: StateFlow<String>
    fun regenerate(): String
}
