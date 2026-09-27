package com.flashcards.app.sync

import com.flashcards.app.testutil.FakeClock
import com.flashcards.core.sync.SyncEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSyncMonitorTest {

    private val clock = FakeClock(1_000)
    private val monitor = WatchSyncMonitor(clock)

    @Test
    fun `starting waits for the watch`() {
        monitor.onStarted()

        assertTrue(monitor.status.value.running)
        assertEquals("Aguardando o relógio", monitor.status.value.message)
    }

    @Test
    fun `a full sync is counted and timestamped`() {
        monitor.onStarted()
        monitor.onEvent(SyncEvent.Hello("w1"))
        monitor.onEvent(SyncEvent.Pushed("w1", accepted = 12, duplicates = 1, rejected = 0))
        monitor.onEvent(SyncEvent.Pulled("w1", changes = 20, hasMore = true))
        monitor.onEvent(SyncEvent.Pulled("w1", changes = 10, hasMore = false))
        clock.time = 5_000
        monitor.onEvent(SyncEvent.Acked("w1", cursor = 30))

        with(monitor.status.value) {
            assertEquals(12, receivedReviews)
            assertEquals(30, sentChanges)
            assertEquals(5_000L, lastCompletedAt)
            assertEquals(5_000L, lastEventAt)
            assertEquals("Sincronização concluída", message)
        }
    }

    @Test
    fun `stopping keeps the last completed sync and restarting resets the counters`() {
        monitor.onStarted()
        monitor.onEvent(SyncEvent.Pushed("w1", 3, 0, 0))
        monitor.onEvent(SyncEvent.Acked("w1", 1))

        monitor.onStopped()
        assertFalse(monitor.status.value.running)
        assertEquals(1_000L, monitor.status.value.lastCompletedAt)

        monitor.onStarted()
        assertEquals(0, monitor.status.value.receivedReviews)
        assertEquals(1_000L, monitor.status.value.lastCompletedAt)
    }

    @Test
    fun `wrong pairing code is reported`() {
        monitor.onEvent(SyncEvent.Unauthorized)

        assertEquals("O relógio usou um código de pareamento errado", monitor.status.value.message)
    }

    @Test
    fun `port in use explains what to do and a new start clears it`() {
        monitor.onFailedToStart("Address already in use")

        assertFalse(monitor.status.value.running)
        assertTrue(monitor.status.value.startError!!.contains("8765"))

        monitor.onStarted()
        assertNull(monitor.status.value.startError)
    }
}
