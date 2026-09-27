package com.flashcards.app.ui.common

import com.flashcards.core.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emite a hora atual agora e a cada [periodMs], para que contagens de "vencidos" não fiquem velhas. */
fun minuteTicker(clock: Clock, periodMs: Long = 60_000L): Flow<Long> = flow {
    while (true) {
        emit(clock.now())
        delay(periodMs)
    }
}
