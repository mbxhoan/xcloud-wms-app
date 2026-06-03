package vn.delfi.xcloudwms.feature.scannertest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.BroadcastScannerConfig
import vn.delfi.xcloudwms.core.scanner.ScanEvent
import vn.delfi.xcloudwms.core.scanner.ScanSource
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode

class ScannerTestViewModel(
    private val scannerManager: ScannerManager,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ScannerTestUiState> = mutableUiState.asStateFlow()
    private var captureSubmitJob: Job? = null

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                val label = when (event) {
                    is ScanEvent.Success ->
                        "Đã nhận • ${event.parsed.normalized} • ${event.parsed.type.label} • ${event.source.label}"

                    is ScanEvent.Error -> "Lỗi • ${event.message}"
                }
                val isKeyboardWedgeEvent = when (event) {
                    is ScanEvent.Success -> event.source == ScanSource.KEYBOARD_WEDGE
                    is ScanEvent.Error -> event.source == ScanSource.KEYBOARD_WEDGE
                }
                if (isKeyboardWedgeEvent) {
                    captureSubmitJob?.cancel()
                }
                mutableUiState.update {
                    it.copy(
                        latestEvent = label,
                        captureInput = if (isKeyboardWedgeEvent) "" else it.captureInput,
                        lastSourceLabel = when (event) {
                            is ScanEvent.Success -> event.source.label
                            is ScanEvent.Error -> event.source.label
                        },
                        lastSymbology = when (event) {
                            is ScanEvent.Success -> event.symbology ?: "—"
                            is ScanEvent.Error -> "—"
                        },
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

    fun updateCaptureInput(value: String) {
        mutableUiState.update { it.copy(captureInput = value) }
        captureSubmitJob?.cancel()
        if (uiState.value.softKeyboardEnabled) {
            return
        }
        if (value.isBlank()) {
            return
        }
        captureSubmitJob = viewModelScope.launch {
            delay(CAPTURE_SETTLE_DELAY_MS)
            submitCaptureInput()
        }
    }

    fun submitCaptureInput() {
        val captured = uiState.value.captureInput.trim()
        if (captured.isBlank()) {
            return
        }
        logger.debug(TAG, "Submitting captured wedge text")
        scannerManager.submitCapturedScan(captured)
        mutableUiState.update { it.copy(captureInput = "") }
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

    fun toggleSoftKeyboard(enabled: Boolean) {
        captureSubmitJob?.cancel()
        mutableUiState.update { it.copy(softKeyboardEnabled = enabled) }
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
            broadcastAction = config.action.ifBlank { DEFAULT_BROADCAST_ACTION },
            broadcastDataKey = config.dataExtraKey.ifBlank { DEFAULT_BROADCAST_DATA_KEY },
            broadcastSymbologyKey = config.symbologyExtraKey.ifBlank { DEFAULT_BROADCAST_SYMBOLOGY_KEY },
        )
    }

    companion object {
        private const val TAG = "ScannerTestViewModel"
        private const val MAX_HISTORY = 8
        private const val CAPTURE_SETTLE_DELAY_MS = 180L
        private const val DEFAULT_BROADCAST_ACTION = "vn.delfi.xcloudwms.SCAN"
        private const val DEFAULT_BROADCAST_DATA_KEY = "data"
        private const val DEFAULT_BROADCAST_SYMBOLOGY_KEY = "symbology"

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
