package vn.delfi.xcloudwms.feature.inventorycount

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
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.data.ic.InventoryCountErrorMapper
import vn.delfi.xcloudwms.data.ic.InventoryCountRepository
import vn.delfi.xcloudwms.domain.model.IcCountSuccess
import vn.delfi.xcloudwms.domain.model.IcLine
import vn.delfi.xcloudwms.domain.model.IcStatus
import vn.delfi.xcloudwms.domain.model.IcTrackingType

class InventoryCountViewModel(
    private val headerId: String,
    private val scannerManager: ScannerManager,
    private val inventoryCountRepository: InventoryCountRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val appPreferences: AppPreferences,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(InventoryCountUiState())
    val uiState: StateFlow<InventoryCountUiState> = mutableUiState.asStateFlow()

    private var loaded = false
    private var scanSubmitMode: ScannerSubmitMode = ScannerSubmitMode.ENTER
    private var forceProcessNextScan: Boolean = false

    /** Serial đã đếm trong phiên (key = lineId|serial) để chặn quét trùng tại chỗ. */
    private val sessionSerials = HashSet<String>()

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Success -> handleIncomingScan(event.parsed.normalized)
                    is ScanEvent.Error -> {
                        forceProcessNextScan = false
                        setBanner(IcBannerTone.ERROR, event.message)
                    }
                }
            }
        }
        viewModelScope.launch {
            appPreferences.scannerSubmitMode.collect { mode ->
                scanSubmitMode = mode
            }
        }
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                mutableUiState.update { it.copy(isOffline = !online) }
            }
        }
    }

    fun onScreenEntered() {
        scannerManager.start()
        applyScannerMode(uiState.value.activeLine?.trackingType)
        if (!loaded) load(initial = true)
    }

    fun onScreenLeft() {
        scannerManager.stop()
    }

    fun dismissBanner() {
        mutableUiState.update { it.copy(banner = null) }
    }

    fun updateScannedCode(value: String) {
        mutableUiState.update { it.copy(scannedCode = value) }
    }

    fun updateQty(value: String) {
        mutableUiState.update { it.copy(qtyText = value) }
    }

    fun updateLocationQuery(value: String) {
        mutableUiState.update { it.copy(locationQuery = value, selectedLocationId = null) }
    }

    fun selectLocation(locationId: String) {
        val loc = uiState.value.locations.firstOrNull { it.id == locationId } ?: return
        mutableUiState.update { it.copy(selectedLocationId = locationId, locationQuery = loc.code) }
    }

    fun selectLine(lineId: String) {
        val line = uiState.value.lines.firstOrNull { it.id == lineId } ?: return
        mutableUiState.update {
            val loc = line.locationId
            it.copy(
                activeLineId = lineId,
                qtyText = if (line.trackingType == IcTrackingType.SERIAL) "1" else it.qtyText.ifBlank { "1" },
                selectedLocationId = loc ?: it.selectedLocationId,
                locationQuery = line.locationLabel ?: it.locationQuery,
                banner = null,
            )
        }
        applyScannerMode(line.trackingType)
    }

    fun submitScannedCode() {
        val code = uiState.value.scannedCode
        if (code.isBlank()) {
            return
        }
        forceProcessNextScan = true
        scannerManager.submitManualScan(code)
    }

    /** Nút "Đếm SL" cho dòng NONE (không cần quét). */
    fun countActiveNoneQuantity() {
        val state = uiState.value
        val line = state.activeLine ?: run {
            setBanner(IcBannerTone.WARNING, "Chọn dòng cần đếm trước.")
            return
        }
        if (line.trackingType != IcTrackingType.NONE) {
            setBanner(IcBannerTone.WARNING, "Dòng này cần quét serial/lô.")
            return
        }
        val delta = state.qtyText.trim().toDoubleOrNull() ?: 0.0
        if (delta <= 0) {
            setBanner(IcBannerTone.WARNING, "Nhập số lượng hợp lệ.")
            return
        }
        val target = line.countedQty + delta
        runCount(line) { inventoryCountRepository.countNone(line, target) }
    }

    private fun load(initial: Boolean) {
        if (initial) mutableUiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val header = inventoryCountRepository.loadHeader(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, loadError = appError.message) }
                logger.error(TAG, "Tải header IC lỗi: ${appError.code}")
                return@launch
            }

            // Auto start counting nếu phiếu đang DRAFT/CREATED (giống scanner PWA).
            var effectiveHeader = header
            if (header.status.needsStartCounting) {
                mutableUiState.update { it.copy(isStarting = true) }
                val startResult = inventoryCountRepository.startCounting(headerId)
                if (startResult.isSuccess) {
                    effectiveHeader = inventoryCountRepository.loadHeader(headerId).getOrDefault(header)
                } else {
                    val message = startResult.exceptionOrNull()?.toAppError()?.message ?: "Lỗi không xác định."
                    setBanner(IcBannerTone.WARNING, "Chưa thể bắt đầu kiểm kê. $message")
                }
                mutableUiState.update { it.copy(isStarting = false) }
            }

            val lines = inventoryCountRepository.loadLines(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, header = effectiveHeader, loadError = appError.message) }
                return@launch
            }

            val locations = effectiveHeader.warehouseId
                ?.let { wh -> inventoryCountRepository.loadLocations(wh).getOrNull() }
                ?: emptyList()

            loaded = true
            mutableUiState.update { current ->
                val nextActive = current.activeLineId
                    ?.takeIf { id -> lines.any { it.id == id } }
                    ?: lines.firstOrNull()?.id
                val activeLineLoc = lines.firstOrNull { it.id == nextActive }?.locationId
                current.copy(
                    isLoading = false,
                    header = effectiveHeader,
                    lines = lines,
                    locations = locations,
                    loadError = null,
                    activeLineId = nextActive,
                    selectedLocationId = current.selectedLocationId ?: activeLineLoc,
                )
            }
            applyScannerMode(uiState.value.activeLine?.trackingType)
        }
    }

    fun refresh() = reloadDocument(banner = "Đã tải lại phiếu.")

    private fun reloadDocument(banner: String?) {
        viewModelScope.launch {
            val header = inventoryCountRepository.loadHeader(headerId).getOrNull()
            val lines = inventoryCountRepository.loadLines(headerId).getOrNull()
            mutableUiState.update { current ->
                val effectiveLines = lines ?: current.lines
                current.copy(
                    header = header ?: current.header,
                    lines = effectiveLines,
                    activeLineId = current.activeLineId?.takeIf { id -> effectiveLines.any { it.id == id } }
                        ?: effectiveLines.firstOrNull()?.id,
                    banner = banner?.let { IcBanner(IcBannerTone.WARNING, it) } ?: current.banner,
                )
            }
        }
    }

    private fun handleIncomingScan(normalizedCode: String) {
        val shouldProcessImmediately = scanSubmitMode == ScannerSubmitMode.ENTER || forceProcessNextScan
        forceProcessNextScan = false
        mutableUiState.update { it.copy(scannedCode = normalizedCode, banner = null) }
        if (shouldProcessImmediately) {
            onScan(normalizedCode)
        }
    }

    private fun onScan(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        val state = uiState.value
        if (!state.canScan) {
            setBanner(IcBannerTone.WARNING, "Phiếu đang ở trạng thái ${state.header?.status?.label ?: "—"}, không thể đếm.")
            return
        }
        val line = state.activeLine ?: run {
            setBanner(IcBannerTone.WARNING, "Chọn dòng cần đếm trước khi quét.")
            return
        }
        val header = state.header ?: return
        mutableUiState.update { it.copy(scannedCode = "") }

        when (line.trackingType) {
            IcTrackingType.SERIAL -> {
                val key = "${line.id}|${code.lowercase()}"
                if (sessionSerials.contains(key)) {
                    setBanner(IcBannerTone.ERROR, "Serial \"$code\" đã đếm trong phiên này.")
                    return
                }
                val locationId = state.selectedLocationId ?: line.locationId
                runCount(line, onSuccessExtra = { sessionSerials.add(key) }) {
                    inventoryCountRepository.countSerial(header, line, locationId, code)
                }
            }

            IcTrackingType.LOT -> {
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (qty <= 0) {
                    setBanner(IcBannerTone.WARNING, "Nhập số lượng lô hợp lệ trước khi quét.")
                    return
                }
                val locationId = state.selectedLocationId ?: line.locationId
                runCount(line) { inventoryCountRepository.countLot(header, line, locationId, code, qty) }
            }

            IcTrackingType.NONE -> {
                if (!code.equals(line.productCode, ignoreCase = true)) {
                    setBanner(IcBannerTone.ERROR, "Mã quét không khớp sản phẩm của dòng này.")
                    return
                }
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (qty <= 0) {
                    setBanner(IcBannerTone.WARNING, "Nhập số lượng hợp lệ.")
                    return
                }
                val target = line.countedQty + qty
                runCount(line) { inventoryCountRepository.countNone(line, target) }
            }
        }
    }

    private fun runCount(
        line: IcLine,
        onSuccessExtra: () -> Unit = {},
        block: suspend () -> Result<IcCountSuccess>,
    ) {
        if (uiState.value.processingLineId != null) return
        mutableUiState.update { it.copy(processingLineId = line.id, banner = null) }
        viewModelScope.launch {
            block()
                .onSuccess { success ->
                    onSuccessExtra()
                    mutableUiState.update { current ->
                        current.copy(
                            processingLineId = null,
                            lines = current.lines.map { l ->
                                if (l.id == success.lineId && success.appliedQty != 0.0) {
                                    l.copy(countedQty = l.countedQty + success.appliedQty)
                                } else {
                                    l
                                }
                            },
                            banner = IcBanner(IcBannerTone.SUCCESS, success.message),
                        )
                    }
                    backgroundRefreshLines()
                }
                .onFailure { throwable ->
                    handleCountFailure(throwable)
                }
        }
    }

    private fun handleCountFailure(throwable: Throwable) {
        val appError = throwable.toAppError()
        mutableUiState.update { it.copy(processingLineId = null) }
        if (InventoryCountErrorMapper.isStatusConflict(appError.message) || InventoryCountErrorMapper.isStatusConflict(appError.code)) {
            setBanner(IcBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
            reloadDocument(banner = null)
            return
        }
        setBanner(IcBannerTone.ERROR, appError.message)
        logger.error(TAG, "Đếm IC lỗi: ${appError.code}")
    }

    private fun backgroundRefreshLines() {
        viewModelScope.launch {
            inventoryCountRepository.loadLines(headerId)
                .onSuccess { lines ->
                    mutableUiState.update { current ->
                        if (lines.isEmpty()) current else current.copy(lines = lines)
                    }
                }
                .onFailure { logger.error(TAG, "Refresh lines lỗi: ${it.message}") }
        }
    }

    // region finish

    fun openFinishDialog() {
        val state = uiState.value
        if (!state.canFinish) {
            setBanner(IcBannerTone.WARNING, "Phiếu không ở trạng thái có thể kết thúc.")
            return
        }
        mutableUiState.update { it.copy(showFinishDialog = true, finishNote = "") }
    }

    fun updateFinishNote(value: String) {
        mutableUiState.update { it.copy(finishNote = value) }
    }

    fun dismissFinishDialog() {
        mutableUiState.update { it.copy(showFinishDialog = false) }
    }

    fun confirmFinish() {
        val state = uiState.value
        val note = state.finishNote.trim()
        if (note.isEmpty()) {
            setBanner(IcBannerTone.WARNING, "Cần ghi chú khi kết thúc phiếu kiểm kê.")
            return
        }
        if (state.isFinishing) return
        if (state.isOffline) {
            setBanner(IcBannerTone.WARNING, "Cần có mạng để hoàn tất.")
            mutableUiState.update { it.copy(showFinishDialog = false) }
            return
        }
        mutableUiState.update { it.copy(isFinishing = true, showFinishDialog = false, banner = null) }
        viewModelScope.launch {
            val result = inventoryCountRepository.finish(headerId, note)
            if (result.isFailure) {
                val appError = result.exceptionOrNull()?.toAppError()
                mutableUiState.update { it.copy(isFinishing = false) }
                if (appError != null && InventoryCountErrorMapper.isStatusConflict(appError.message)) {
                    setBanner(IcBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
                    reloadDocument(banner = null)
                    return@launch
                }
                setBanner(IcBannerTone.ERROR, "Không thể kết thúc kiểm kê. ${appError?.message ?: ""}")
                logger.error(TAG, "Finish IC lỗi: ${appError?.code}")
                return@launch
            }
            val header = inventoryCountRepository.loadHeader(headerId).getOrNull()
            mutableUiState.update {
                it.copy(
                    isFinishing = false,
                    header = header ?: it.header,
                    finished = true,
                    banner = IcBanner(
                        IcBannerTone.SUCCESS,
                        "Đã kết thúc kiểm kê. Cân bằng/duyệt điều chỉnh thực hiện trên webapp.",
                    ),
                )
            }
        }
    }

    // endregion

    private fun applyScannerMode(trackingType: IcTrackingType?) {
        when (trackingType) {
            IcTrackingType.SERIAL -> {
                scannerManager.setMode(ScannerMode.SERIAL)
                scannerManager.setContinuousSerial(true)
            }

            IcTrackingType.LOT -> {
                scannerManager.setMode(ScannerMode.LOT)
                scannerManager.setContinuousSerial(false)
            }

            else -> {
                scannerManager.setMode(ScannerMode.PRODUCT)
                scannerManager.setContinuousSerial(false)
            }
        }
    }

    private fun setBanner(tone: IcBannerTone, message: String) {
        mutableUiState.update { it.copy(banner = IcBanner(tone, message)) }
    }

    companion object {
        private const val TAG = "InventoryCountVM"

        fun factory(
            headerId: String,
            scannerManager: ScannerManager,
            inventoryCountRepository: InventoryCountRepository,
            connectivityObserver: ConnectivityObserver,
            appPreferences: AppPreferences,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InventoryCountViewModel(
                    headerId = headerId,
                    scannerManager = scannerManager,
                    inventoryCountRepository = inventoryCountRepository,
                    connectivityObserver = connectivityObserver,
                    appPreferences = appPreferences,
                    logger = logger,
                )
            }
        }
    }
}
