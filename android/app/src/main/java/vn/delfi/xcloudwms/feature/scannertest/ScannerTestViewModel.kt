package vn.delfi.xcloudwms.feature.scannertest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.BroadcastScannerConfig
import vn.delfi.xcloudwms.core.scanner.ScanEvent
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode

class ScannerTestViewModel(
    private val scannerManager: ScannerManager,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ScannerTestUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                val label = when (event) {
                    is ScanEvent.Success ->
                        "Đã nhận • ${event.parsed.normalized} • ${event.parsed.type.label} • ${event.source.label}"

                    is ScanEvent.Error -> "Lỗi • ${event.message}"
                }
                mutableUiState.update {
                    it.copy(
                        latestEvent = label,
                        eventHistory = (listOf(label) + it.eventHistory).take(MAX_HISTORY),
                    )
                }
            }
        }

        viewModelScope.launch {
            scannerManager.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        isActive = state.isActive,
                        selectedMode = state.mode,
                        continuousSerial = state.continuousSerial,
                        currentAdapters = if (state.activeSources.isEmpty()) {
                            "Chưa bật"
                        } else {
                            state.activeSources.joinToString { source -> source.label }
                        },
                        lastRawScan = state.lastRaw ?: "—",
                        lastParsedType = state.lastType?.label ?: "—",
                    )
                }
            }
        }
    }

    fun startScanner() {
        scannerManager.start()
    }

    fun stopScanner() {
        scannerManager.stop()
    }

    fun selectMode(mode: ScannerMode) {
        scannerManager.setMode(mode)
    }

    fun updateManualCode(value: String) {
        mutableUiState.update { it.copy(manualCode = value) }
    }

    fun submitManualScan() {
        logger.debug(TAG, "Submitting manual scan mode=${uiState.value.selectedMode.name}")
        scannerManager.submitManualScan(uiState.value.manualCode)
        mutableUiState.update { it.copy(manualCode = "") }
    }

    fun toggleContinuousSerial(enabled: Boolean) {
        scannerManager.setContinuousSerial(enabled)
    }

    fun testFeedback() {
        scannerManager.testFeedback()
    }

    fun updateBroadcastEnabled(enabled: Boolean) {
        mutableUiState.update { it.copy(broadcastEnabled = enabled) }
    }

    fun updateBroadcastAction(value: String) {
        mutableUiState.update { it.copy(broadcastAction = value) }
    }

    fun updateBroadcastDataKey(value: String) {
        mutableUiState.update { it.copy(broadcastDataKey = value) }
    }

    fun updateBroadcastSymbologyKey(value: String) {
        mutableUiState.update { it.copy(broadcastSymbologyKey = value) }
    }

    fun saveBroadcastConfig() {
        val state = uiState.value
        scannerManager.setBroadcastConfig(
            BroadcastScannerConfig(
                action = state.broadcastAction.trim(),
                dataExtraKey = state.broadcastDataKey.trim(),
                symbologyExtraKey = state.broadcastSymbologyKey.trim(),
                enabled = state.broadcastEnabled,
            ),
        )
        logger.info(TAG, "Đã lưu cấu hình broadcast")
    }

    private fun initialState(): ScannerTestUiState {
        val config = scannerManager.state.value.broadcastConfig
        return ScannerTestUiState(
            broadcastEnabled = config.enabled,
            broadcastAction = config.action,
            broadcastDataKey = config.dataExtraKey,
            broadcastSymbologyKey = config.symbologyExtraKey,
        )
    }

    companion object {
        private const val TAG = "ScannerTestViewModel"
        private const val MAX_HISTORY = 8

        fun factory(
            scannerManager: ScannerManager,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScannerTestViewModel(
                    scannerManager = scannerManager,
                    logger = logger,
                )
            }
        }
    }
}
