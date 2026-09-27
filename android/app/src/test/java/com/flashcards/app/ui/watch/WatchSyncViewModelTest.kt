package com.flashcards.app.ui.watch

import com.flashcards.app.sync.PairingCodes
import com.flashcards.app.sync.SyncControl
import com.flashcards.app.sync.WatchSyncMonitor
import com.flashcards.app.testutil.FakeClock
import com.flashcards.core.repository.WatchDevice
import com.flashcards.core.repository.WatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSyncViewModelTest {

    private val monitor = WatchSyncMonitor(FakeClock())
    private val pairing = FakePairingCodes()
    private val watches = FakeWatchRepository()
    private val control = FakeSyncControl()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WatchSyncViewModel(monitor, pairing, watches, control)

    @Test
    fun `screen state joins status, code, watches and pending changes`() = runTest {
        watches.devices.value = listOf(WatchDevice("w1", lastAckSeq = 10, lastSyncAt = 500))
        watches.pending.value = 3
        monitor.onStarted()

        val state = viewModel().uiState.first { it.devices.isNotEmpty() }

        assertTrue(state.status.running)
        assertEquals("123456", state.code)
        assertEquals(3, state.pendingChanges)
        assertEquals("w1", state.devices.single().deviceId)
    }

    @Test
    fun `start and stop go to the service`() {
        val viewModel = viewModel()

        viewModel.start()
        viewModel.stop()

        assertEquals(listOf("start", "stop"), control.calls)
    }

    @Test
    fun `new code only after confirmation`() = runTest {
        val viewModel = viewModel()

        viewModel.askNewCode()
        assertTrue(viewModel.confirmNewCode.value)
        viewModel.dismissNewCode()
        assertEquals("123456", pairing.code.value)

        viewModel.askNewCode()
        viewModel.confirmNewCode()

        assertFalse(viewModel.confirmNewCode.value)
        assertEquals("654321", pairing.code.value)
    }

    @Test
    fun `pairing code is shown in two groups`() {
        assertEquals("482 913", formatPairingCode("482913"))
        assertEquals("12", formatPairingCode("12"))
    }
}

private class FakePairingCodes : PairingCodes {
    private val state = MutableStateFlow("123456")
    override val code: StateFlow<String> = state
    override fun regenerate(): String = "654321".also { state.value = it }
}

private class FakeWatchRepository : WatchRepository {
    val devices = MutableStateFlow<List<WatchDevice>>(emptyList())
    val pending = MutableStateFlow(0)
    override fun observeDevices(): Flow<List<WatchDevice>> = devices
    override fun observePendingChanges(): Flow<Int> = pending
}

private class FakeSyncControl : SyncControl {
    val calls = mutableListOf<String>()
    override fun start() {
        calls += "start"
    }
    override fun stop() {
        calls += "stop"
    }
}
