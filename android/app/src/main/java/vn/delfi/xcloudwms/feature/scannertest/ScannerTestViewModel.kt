package vn.delfi.xcloudwms.feature.scannertest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.ScanEvent
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode

class ScannerTestViewModel(
    private val scannerManager: ScannerManager,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ScannerTestUiState())
    val uiState: StateFlow<ScannerTestUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collectLatest { event ->
                when (event) {
                    is ScanEvent.Success -> {
                        val label = "Đã nhận • ${event.raw} • ${event.source.label}"
                        mutableUiState.update {
                            it.copy(
                                latestEvent = label,
                                eventHistory = listOf(label) + it.eventHistory,
                            )
                        }
                        trimHistory()
                    }

                    is ScanEvent.Error -> {
                        val label = "Lỗi • ${event.message}"
                        mutableUiState.update {
                            it.copy(
                                latestEvent = label,
                                eventHistory = listOf(label) + it.eventHistory,
                            )
                        }
                        trimHistory()
                    }
                }
            }
        }
    }

    fun startScanner() {
        scannerManager.start()
        mutableUiState.update {
            it.copy(isActive = true)
        }
    }

    fun stopScanner() {
        scannerManager.stop()
        mutableUiState.update {
            it.copy(isActive = false)
        }
    }

    fun selectMode(mode: ScannerMode) {
        scannerManager.setMode(mode)
        mutableUiState.update {
            it.copy(selectedMode = mode)
        }
    }

    fun updateManualCode(value: String) {
        mutableUiState.update {
            it.copy(manualCode = value)
        }
    }

    fun submitManualScan() {
        logger.debug(
            "ScannerTestViewModel",
            "Submitting manual scan for mode=${uiState.value.selectedMode.name}",
        )
        scannerManager.submitManualScan(uiState.value.manualCode)
        mutableUiState.update {
            it.copy(manualCode = "")
        }
    }

    private fun trimHistory() {
        mutableUiState.update {
            it.copy(eventHistory = it.eventHistory.take(8))
        }
    }

    companion object {
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
