package vn.delfi.xcloudwms.feature.stocklookup

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
import vn.delfi.xcloudwms.core.error.toAppError
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.network.ConnectivityObserver
import vn.delfi.xcloudwms.core.scanner.ScanEvent
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.data.stock.StockLookupRepository
import vn.delfi.xcloudwms.data.stock.StockRowFilter
import vn.delfi.xcloudwms.domain.model.StockLookupResult

class StockLookupViewModel(
    private val scannerManager: ScannerManager,
    private val stockLookupRepository: StockLookupRepository,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StockLookupUiState())
    val uiState: StateFlow<StockLookupUiState> = mutableUiState.asStateFlow()

    private var lastResult: StockLookupResult? = null
    private var lastQuery: String = ""
    private var currentWarehouseId: String? = null

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Success -> runLookup(event.parsed.normalized)
                    is ScanEvent.Error -> mutableUiState.update {
                        it.copy(errorMessage = event.message, errorRetryable = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                currentWarehouseId = session.currentWarehouse?.id
                mutableUiState.update {
                    it.copy(currentWarehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn")
                }
                recomputeRows()
            }
        }

        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                mutableUiState.update { it.copy(isOffline = !online) }
            }
        }
    }

    fun startScanner() {
        scannerManager.setMode(ScannerMode.GENERIC)
        scannerManager.start()
    }

    fun stopScanner() {
        scannerManager.stop()
    }

    fun updateManualCode(value: String) {
        mutableUiState.update { it.copy(manualCode = value) }
    }

    fun submitManual() {
        scannerManager.submitManualScan(uiState.value.manualCode)
        mutableUiState.update { it.copy(manualCode = "") }
    }

    fun retry() {
        if (lastQuery.isNotBlank()) {
            runLookup(lastQuery)
        }
    }

    fun toggleShowAllWarehouses(showAll: Boolean) {
        mutableUiState.update { it.copy(showAllWarehouses = showAll) }
        recomputeRows()
    }

    private fun runLookup(code: String) {
        val normalized = code.trim()
        if (normalized.isBlank()) {
            return
        }
        lastQuery = normalized
        mutableUiState.update {
            it.copy(isLoading = true, query = normalized, errorMessage = null, errorRetryable = false)
        }

        viewModelScope.launch {
            stockLookupRepository.lookup(normalized)
                .onSuccess { result ->
                    lastResult = result
                    applyResult(result)
                    logger.debug(TAG, "Tra cứu '$normalized' → match=${result.match?.kind} rows=${result.rows.size}")
                }
                .onFailure { throwable ->
                    val appError = throwable.toAppError()
                    lastResult = null
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            match = null,
                            summary = null,
                            rows = emptyList(),
                            totalRowCount = 0,
                            hasResult = false,
                            isEmptyResult = false,
                            errorMessage = appError.message,
                            errorRetryable = appError.retryable,
                        )
                    }
                    logger.error(TAG, "Tra cứu '$normalized' lỗi: ${appError.code}")
                }
        }
    }

    private fun applyResult(result: StockLookupResult) {
        val displayed = StockRowFilter.forWarehouse(
            rows = result.rows,
            currentWarehouseId = currentWarehouseId,
            showAll = uiState.value.showAllWarehouses,
        )
        mutableUiState.update {
            it.copy(
                isLoading = false,
                match = result.match,
                summary = result.summary,
                rows = displayed,
                totalRowCount = result.rows.size,
                hasResult = result.match != null,
                isEmptyResult = result.match == null,
                errorMessage = null,
                errorRetryable = false,
            )
        }
    }

    private fun recomputeRows() {
        val result = lastResult ?: return
        val displayed = StockRowFilter.forWarehouse(
            rows = result.rows,
            currentWarehouseId = currentWarehouseId,
            showAll = uiState.value.showAllWarehouses,
        )
        mutableUiState.update { it.copy(rows = displayed, totalRowCount = result.rows.size) }
    }

    companion object {
        private const val TAG = "StockLookupViewModel"

        fun factory(
            scannerManager: ScannerManager,
            stockLookupRepository: StockLookupRepository,
            sessionRepository: SessionRepository,
            connectivityObserver: ConnectivityObserver,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StockLookupViewModel(
                    scannerManager = scannerManager,
                    stockLookupRepository = stockLookupRepository,
                    sessionRepository = sessionRepository,
                    connectivityObserver = connectivityObserver,
                    logger = logger,
                )
            }
        }
    }
}
