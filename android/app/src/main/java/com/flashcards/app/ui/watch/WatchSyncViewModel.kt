package com.flashcards.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.app.sync.PairingCodes
import com.flashcards.app.sync.SyncControl
import com.flashcards.app.sync.WatchSyncMonitor
import com.flashcards.app.sync.WatchSyncStatus
import com.flashcards.core.repository.WatchDevice
import com.flashcards.core.repository.WatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WatchSyncUiState(
    val status: WatchSyncStatus = WatchSyncStatus(),
    val code: String = "",
    val devices: List<WatchDevice> = emptyList(),
    val pendingChanges: Int = 0,
)

class WatchSyncViewModel(
    monitor: WatchSyncMonitor,
    private val pairing: PairingCodes,
    watches: WatchRepository,
    private val control: SyncControl,
) : ViewModel() {

    val uiState: StateFlow<WatchSyncUiState> = combine(
        monitor.status,
        pairing.code,
        watches.observeDevices(),
        watches.observePendingChanges(),
    ) { status, code, devices, pending ->
        WatchSyncUiState(status, code, devices, pending)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WatchSyncUiState(status = monitor.status.value, code = pairing.code.value),
    )

    private val _confirmNewCode = MutableStateFlow(false)
    val confirmNewCode: StateFlow<Boolean> = _confirmNewCode.asStateFlow()

    fun start() = control.start()

    fun stop() = control.stop()

    fun askNewCode() {
        _confirmNewCode.value = true
    }

    fun dismissNewCode() {
        _confirmNewCode.value = false
    }

    fun confirmNewCode() {
        _confirmNewCode.value = false
        pairing.regenerate()
    }
}

/** "482913" → "482 913", mais fácil de ler e digitar. */
fun formatPairingCode(code: String): String =
    if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
