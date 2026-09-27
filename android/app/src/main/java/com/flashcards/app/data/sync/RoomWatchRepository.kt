package com.flashcards.app.data.sync

import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.core.repository.WatchDevice
import com.flashcards.core.repository.WatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWatchRepository(db: FlashcardsDatabase) : WatchRepository {

    private val watchDeviceDao = db.watchDeviceDao()
    private val syncStateDao = db.syncStateDao()

    override fun observeDevices(): Flow<List<WatchDevice>> =
        watchDeviceDao.observeAll().map { rows ->
            rows.map { WatchDevice(it.deviceId, it.lastAckSeq, it.lastSyncAt) }
        }

    override fun observePendingChanges(): Flow<Int> = syncStateDao.observePendingCount()
}
