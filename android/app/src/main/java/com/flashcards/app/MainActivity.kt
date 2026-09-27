package com.flashcards.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.flashcards.app.ui.navigation.FlashcardsNavHost
import com.flashcards.app.ui.theme.FlashcardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recriação (girar a tela) não reimporta o mesmo arquivo.
        if (savedInstanceState == null) receiveFile(intent)
        setContent {
            FlashcardsTheme {
                FlashcardsNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveFile(intent)
    }

    /** CSV ou JSON aberto ou compartilhado a partir de outro app. */
    private fun receiveFile(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        (application as FlashcardsApplication).container.incomingFiles.offer(uri.toString())
    }
}
