package com.flashcards.core.repository

import kotlinx.coroutines.flow.Flow

/** Um relógio que já sincronizou com este celular. */
data class WatchDevice(
    val deviceId: String,
    /** Até que versão do feed o relógio confirmou ter os dados. */
    val lastAckSeq: Long,
    val lastSyncAt: Long,
)

interface WatchRepository {

    /** Relógios conhecidos, do sincronizado mais recentemente para o mais antigo. */
    fun observeDevices(): Flow<List<WatchDevice>>

    /** Alterações que o relógio ainda não confirmou. */
    fun observePendingChanges(): Flow<Int>
}
