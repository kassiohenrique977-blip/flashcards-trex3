package com.flashcards.app.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.flashcards.app.FlashcardsApplication
import com.flashcards.app.MainActivity
import com.flashcards.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mantém o servidor de sincronização ligado enquanto o usuário usa o relógio.
 * Foreground service do tipo dataSync: o Android não congela o app, então o side
 * service do Zepp App consegue falar com ele. Desliga sozinho após [IDLE_TIMEOUT_MS]
 * sem nenhuma atividade do relógio, para não gastar bateria.
 */
class WatchSyncService : Service() {

    private val container get() = (application as FlashcardsApplication).container
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    private val idleCheck = object : Runnable {
        override fun run() {
            val lastActivity = maxOf(startedAt, container.syncMonitor.status.value.lastEventAt ?: 0L)
            if (container.clock.now() - lastActivity >= IDLE_TIMEOUT_MS) {
                stopSelf()
            } else {
                handler.postDelayed(this, IDLE_CHECK_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (startedAt == 0L) {
            startedAt = container.clock.now()
            // Abrir o socket fora da thread principal.
            container.applicationScope.launch(Dispatchers.IO) {
                try {
                    container.syncServer.start()
                    container.syncMonitor.onStarted()
                } catch (e: Exception) {
                    container.syncMonitor.onFailedToStart(e.message)
                    stopSelf()
                }
            }
            handler.postDelayed(idleCheck, IDLE_CHECK_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        val server = container.syncServer
        container.applicationScope.launch(Dispatchers.IO) { server.stop() }
        container.syncMonitor.onStopped()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Sincronização com o relógio")
                .build(),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WatchSyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_watch)
            .setContentTitle("Aguardando o relógio")
            .setContentText("Toque em Sincronizar no T-Rex 3. Desliga sozinho após 10 min parado.")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Parar", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.flashcards.app.sync.STOP"
        private const val CHANNEL_ID = "watch_sync"
        private const val NOTIFICATION_ID = 1
        const val IDLE_TIMEOUT_MS = 10 * 60_000L
        private const val IDLE_CHECK_MS = 30_000L
    }
}
