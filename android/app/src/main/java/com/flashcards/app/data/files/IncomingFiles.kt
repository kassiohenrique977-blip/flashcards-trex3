package com.flashcards.app.data.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Arquivo que chegou de outro app ("Abrir com" / "Compartilhar") e espera a tela de
 * importação. Guarda o URI como texto; só uma tela o consome.
 */
class IncomingFiles {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(uri: String) {
        _pending.value = uri
    }

    /** Devolve o arquivo pendente (ou null) e o tira da fila. */
    fun consume(): String? = _pending.getAndUpdate { null }
}
