package vn.delfi.xcloudwms.feature.goodsreceipt

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
import vn.delfi.xcloudwms.data.gr.GoodsReceiptRepository
import vn.delfi.xcloudwms.data.session.SessionRepository

class GoodsReceiptListViewModel(
    private val scannerManager: ScannerManager,
    private val goodsReceiptRepository: GoodsReceiptRepository,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GoodsReceiptListUiState())
    val uiState: StateFlow<GoodsReceiptListUiState> = mutableUiState.asStateFlow()

    private var warehouseId: String? = null
    private var loadedForWarehouse: String? = null
    private var isScreenActive: Boolean = false

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                if (!isScreenActive) return@collect
                when (event) {
                    is ScanEvent.Success -> onScan(event.parsed.normalized)
                    is ScanEvent.Error -> mutableUiState.update { it.copy(errorMessage = event.message) }
                }
            }
        }

        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                val wh = session.currentWarehouse?.id
                warehouseId = wh
                mutableUiState.update {
                    it.copy(
                        warehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn",
                        hasWarehouse = wh != null,
                    )
                }
                if (wh != null && wh != loadedForWarehouse) {
                    load()
                }
            }
        }

        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                mutableUiState.update { it.copy(isOffline = !online) }
            }
        }
    }

    fun onScreenEntered() {
        isScreenActive = true
        scannerManager.setMode(ScannerMode.DOCUMENT)
        scannerManager.start()
        if (loadedForWarehouse == null) load()
    }

    fun onScreenLeft() {
        isScreenActive = false
        scannerManager.stop()
    }

    fun updateQuery(value: String) {
        mutableUiState.update { it.copy(query = value) }
    }

    fun submitManualSearch() {
        onScan(uiState.value.query)
    }

    fun consumeOpenEvent() {
        mutableUiState.update { it.copy(pendingOpenHeaderId = null) }
    }

    fun refresh() = load()

    private fun load() {
        val wh = warehouseId
        if (wh.isNullOrBlank()) {
            mutableUiState.update {
                it.copy(isLoading = false, errorMessage = "Chưa chọn kho làm việc. Vui lòng chọn kho.")
            }
            return
        }
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            goodsReceiptRepository.loadAssignedHeaders(wh)
                .onSuccess { headers ->
                    loadedForWarehouse = wh
                    mutableUiState.update { it.copy(isLoading = false, headers = headers, errorMessage = null) }
                }
                .onFailure { throwable ->
                    val appError = throwable.toAppError()
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = "Không thể tải danh sách phiếu nhập. ${appError.message}")
                    }
                    logger.error(TAG, "Tải danh sách GR lỗi: ${appError.code}")
                }
        }
    }

    private fun onScan(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        mutableUiState.update { it.copy(query = code) }
        val matches = uiState.value.headers.filter { header ->
            header.code?.equals(code, ignoreCase = true) == true ||
                header.id == code ||
                header.referenceNumber?.equals(code, ignoreCase = true) == true
        }
        if (matches.size == 1) {
            mutableUiState.update { it.copy(pendingOpenHeaderId = matches.first().id) }
        }
    }

    companion object {
        private const val TAG = "GoodsReceiptListVM"

        fun factory(
            scannerManager: ScannerManager,
            goodsReceiptRepository: GoodsReceiptRepository,
            sessionRepository: SessionRepository,
            connectivityObserver: ConnectivityObserver,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoodsReceiptListViewModel(
                    scannerManager = scannerManager,
                    goodsReceiptRepository = goodsReceiptRepository,
                    sessionRepository = sessionRepository,
                    connectivityObserver = connectivityObserver,
                    logger = logger,
                )
            }
        }
    }
}
