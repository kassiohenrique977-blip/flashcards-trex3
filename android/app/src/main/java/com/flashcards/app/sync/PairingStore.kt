package com.flashcards.app.sync

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

/**
 * Código de pareamento (6 dígitos) que o usuário digita no Settings App do Zepp App,
 * e um ID estável deste celular. Os dois ficam em SharedPreferences.
 */
class PairingStore(context: Context, private val random: SecureRandom = SecureRandom()) : PairingCodes {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _code = MutableStateFlow(prefs.getString(KEY_CODE, null) ?: generateAndSave())

    override val code: StateFlow<String> = _code.asStateFlow()

    val serverId: String = prefs.getString(KEY_SERVER_ID, null)
        ?: ("phone-" + UUID.randomUUID()).also { id -> prefs.edit { putString(KEY_SERVER_ID, id) } }

    /** Troca o código; o relógio para de sincronizar até o novo ser digitado no Zepp App. */
    override fun regenerate(): String {
        val newCode = generateAndSave()
        _code.value = newCode
        return newCode
    }

    private fun generateAndSave(): String {
        val newCode = newCode(random)
        prefs.edit { putString(KEY_CODE, newCode) }
        return newCode
    }

    companion object {
        private const val FILE = "pairing"
        private const val KEY_CODE = "code"
        private const val KEY_SERVER_ID = "server_id"

        fun newCode(random: SecureRandom): String = random.nextInt(1_000_000).toString().padStart(6, '0')
    }
}
