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
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.data.stock.StockLookupRepository
import vn.delfi.xcloudwms.data.stock.StockRowFilter
import vn.delfi.xcloudwms.domain.model.StockLookupResult

class StockLookupViewModel(
    private val scannerManager: ScannerManager,
    private val stockLookupRepository: StockLookupRepository,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val appPreferences: AppPreferences,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StockLookupUiState())
    val uiState: StateFlow<StockLookupUiState> = mutableUiState.asStateFlow()

    private var lastResult: StockLookupResult? = null
    private var lastQuery: String = ""
    private var currentWarehouseId: String? = null

    /** Bấm nút "Tra cứu" là ý định trực tiếp → tra cứu dù "Tự động Enter / Tab" đang tắt. */
    private var forceLookupNextScan: Boolean = false

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Success -> handleIncomingScan(event.parsed.normalized)
                    is ScanEvent.Error -> {
                        forceLookupNextScan = false
                        mutableUiState.update {
                            it.copy(errorMessage = event.message, errorRetryable = false)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            appPreferences.autoSubmitScanInput.collect { enabled ->
                mutableUiState.update { it.copy(autoSubmitScanInput = enabled) }
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
        forceLookupNextScan = true
        scannerManager.submitManualScan(uiState.value.manualCode)
    }

    /**
     * Nhận mã quét hoàn chỉnh từ pipeline: đổ FULL mã vào ô (ghi đè ký tự lẻ wedge để lọt và
     * mã trước đó) để không dồn rác và để tra cứu liên tục không cần chạm màn. Tự tra cứu khi
     * "Tự động Enter / Tab" bật hoặc khi người dùng vừa bấm nút "Tra cứu".
     */
    private fun handleIncomingScan(rawCode: String) {
        val normalized = rawCode.trim()
        if (normalized.isBlank()) {
            forceLookupNextScan = false
            return
        }
        val shouldLookup = uiState.value.autoSubmitScanInput || forceLookupNextScan
        forceLookupNextScan = false
        mutableUiState.update { it.copy(manualCode = normalized) }
        if (shouldLookup) {
            runLookup(normalized)
        }
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
                            // Lookup xong → trả ô về trống để sẵn sàng quét mã kế tiếp.
                            manualCode = "",
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
                // Lookup xong → trả ô về trống để sẵn sàng quét mã kế tiếp.
                manualCode = "",
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
            appPreferences: AppPreferences,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StockLookupViewModel(
                    scannerManager = scannerManager,
                    stockLookupRepository = stockLookupRepository,
                    sessionRepository = sessionRepository,
                    connectivityObserver = connectivityObserver,
                    appPreferences = appPreferences,
                    logger = logger,
                )
            }
        }
    }
}
