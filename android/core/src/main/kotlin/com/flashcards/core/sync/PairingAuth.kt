package com.flashcards.core.sync

import com.flashcards.core.util.Clock
import java.security.MessageDigest

enum class AuthResult { OK, DENIED, LOCKED }

/**
 * Confere o código de pareamento enviado pelo side service.
 *
 * O servidor escuta só em 127.0.0.1, mas qualquer app do celular alcança esse endereço.
 * Por isso, depois de [maxFailures] códigos errados em [windowMs], tudo é recusado até a
 * janela passar: com 6 dígitos, isso torna inviável adivinhar o código.
 */
class PairingAuth(
    private val expectedCode: () -> String,
    private val clock: Clock,
    private val maxFailures: Int = 10,
    private val windowMs: Long = 5 * 60_000L,
) {
    private val failures = ArrayDeque<Long>()

    @Synchronized
    fun check(provided: String?): AuthResult {
        val now = clock.now()
        while (failures.isNotEmpty() && now - failures.first() >= windowMs) failures.removeFirst()
        if (failures.size >= maxFailures) return AuthResult.LOCKED

        val expected = expectedCode()
        val candidate = provided?.trim().orEmpty()
        // Comparação em tempo constante: não revela quantos dígitos estavam certos.
        val ok = expected.isNotEmpty() &&
            MessageDigest.isEqual(expected.toByteArray(), candidate.toByteArray())
        if (!ok) failures.addLast(now)
        return if (ok) AuthResult.OK else AuthResult.DENIED
    }
}
