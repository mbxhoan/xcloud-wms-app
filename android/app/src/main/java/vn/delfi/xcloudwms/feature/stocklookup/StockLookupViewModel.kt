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
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.data.stock.LookupHistoryStore
import vn.delfi.xcloudwms.data.stock.StockLookupRepository

class StockLookupViewModel(
    private val scannerManager: ScannerManager,
    private val stockLookupRepository: StockLookupRepository,
    private val lookupHistoryStore: LookupHistoryStore,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val appPreferences: AppPreferences,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StockLookupUiState(history = lookupHistoryStore.load()))
    val uiState: StateFlow<StockLookupUiState> = mutableUiState.asStateFlow()

    private var lastQuery: String = ""
    private var scanSubmitMode: ScannerSubmitMode = ScannerSubmitMode.ENTER

    /** Bấm nút "Tra cứu" là ý định trực tiếp → tra cứu dù submit mode không phải ENTER. */
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
            appPreferences.scannerSubmitMode.collect { mode ->
                scanSubmitMode = mode
                mutableUiState.update { it.copy(autoSubmitScanInput = mode == ScannerSubmitMode.ENTER) }
            }
        }

        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                mutableUiState.update {
                    it.copy(currentWarehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn")
                }
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

    /** Bấm vào một mục lịch sử → tra cứu lại mã đó. */
    fun lookupHistory(code: String) {
        runLookup(code)
    }

    fun dismissResult() {
        mutableUiState.update { it.copy(detailResult = null, notFoundCode = null) }
    }

    fun dismissError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Nhận mã quét hoàn chỉnh từ pipeline: đổ FULL mã vào ô (ghi đè ký tự lẻ wedge để lọt và mã
     * trước đó) để không dồn rác và để tra cứu liên tục không cần chạm màn. Tự tra cứu khi submit
     * mode là ENTER hoặc khi người dùng vừa bấm nút "Tra cứu".
     */
    private fun handleIncomingScan(rawCode: String) {
        val normalized = rawCode.trim()
        if (normalized.isBlank()) {
            forceLookupNextScan = false
            return
        }
        val shouldLookup = scanSubmitMode == ScannerSubmitMode.ENTER || forceLookupNextScan
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

    private fun runLookup(code: String) {
        val normalized = code.trim()
        if (normalized.isBlank() || uiState.value.isLoading) {
            return
        }
        lastQuery = normalized
        mutableUiState.update {
            it.copy(
                isLoading = true,
                query = normalized,
                busyCode = normalized,
                errorMessage = null,
                errorRetryable = false,
            )
        }

        viewModelScope.launch {
            stockLookupRepository.lookup(normalized)
                .onSuccess { result ->
                    val history = if (result.match != null) lookupHistoryStore.push(result) else uiState.value.history
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            busyCode = null,
                            // Lookup xong → trả ô về trống để sẵn sàng quét mã kế tiếp.
                            manualCode = "",
                            history = history,
                            detailResult = result.takeIf { r -> r.match != null },
                            notFoundCode = if (result.match == null) normalized else null,
                            errorMessage = null,
                            errorRetryable = false,
                        )
                    }
                    logger.debug(TAG, "Tra cứu '$normalized' → match=${result.match?.kind} rows=${result.rows.size}")
                }
                .onFailure { throwable ->
                    val appError = throwable.toAppError()
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            busyCode = null,
                            manualCode = "",
                            detailResult = null,
                            notFoundCode = null,
                            errorMessage = appError.message,
                            errorRetryable = appError.retryable,
                        )
                    }
                    logger.error(TAG, "Tra cứu '$normalized' lỗi: ${appError.code}")
                }
        }
    }

    companion object {
        private const val TAG = "StockLookupViewModel"

        fun factory(
            scannerManager: ScannerManager,
            stockLookupRepository: StockLookupRepository,
            lookupHistoryStore: LookupHistoryStore,
            sessionRepository: SessionRepository,
            connectivityObserver: ConnectivityObserver,
            appPreferences: AppPreferences,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StockLookupViewModel(
                    scannerManager = scannerManager,
                    stockLookupRepository = stockLookupRepository,
                    lookupHistoryStore = lookupHistoryStore,
                    sessionRepository = sessionRepository,
                    connectivityObserver = connectivityObserver,
                    appPreferences = appPreferences,
                    logger = logger,
                )
            }
        }
    }
}
