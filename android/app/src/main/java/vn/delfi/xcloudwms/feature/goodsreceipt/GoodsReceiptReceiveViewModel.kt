package vn.delfi.xcloudwms.feature.goodsreceipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import vn.delfi.xcloudwms.data.gr.GoodsReceiptErrorMapper
import vn.delfi.xcloudwms.data.gr.GoodsReceiptRepository
import vn.delfi.xcloudwms.domain.model.GrLine
import vn.delfi.xcloudwms.domain.model.GrReceiveSuccess
import vn.delfi.xcloudwms.domain.model.GrStatus
import vn.delfi.xcloudwms.domain.model.GrTrackingType

class GoodsReceiptReceiveViewModel(
    private val headerId: String,
    private val scannerManager: ScannerManager,
    private val goodsReceiptRepository: GoodsReceiptRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GoodsReceiptReceiveUiState())
    val uiState: StateFlow<GoodsReceiptReceiveUiState> = mutableUiState.asStateFlow()

    private var loaded = false
    private var isScreenActive: Boolean = false

    /** Serial đã nhận trong phiên hiện tại (key = lineId|serial) để chặn quét trùng tại chỗ. */
    private val sessionSerials = HashSet<String>()

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                if (!isScreenActive) return@collect
                when (event) {
                    is ScanEvent.Success -> onScan(event.parsed.normalized)
                    is ScanEvent.Error -> setBanner(GrBannerTone.ERROR, event.message)
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
        scannerManager.start()
        applyScannerMode(uiState.value.activeLine?.trackingType)
        if (!loaded) load(initial = true)
    }

    fun onScreenLeft() {
        isScreenActive = false
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

    fun updateMfgDate(value: String) {
        mutableUiState.update { it.copy(mfgDateText = value) }
    }

    fun updateExpiryDate(value: String) {
        mutableUiState.update { it.copy(expiryDateText = value) }
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
            it.copy(
                activeLineId = lineId,
                qtyText = if (line.trackingType == GrTrackingType.SERIAL) "1" else it.qtyText.ifBlank { "1" },
                banner = null,
            )
        }
        applyScannerMode(line.trackingType)
    }

    fun submitScannedCode() {
        val code = uiState.value.scannedCode
        if (code.isBlank()) {
            setBanner(GrBannerTone.WARNING, "Quét hoặc nhập mã trước khi nhận.")
            return
        }
        scannerManager.submitManualScan(code)
    }

    /** Nút "Nhận SL" cho dòng NONE (không cần quét). */
    fun receiveActiveNoneQuantity() {
        val state = uiState.value
        val line = state.activeLine ?: run {
            setBanner(GrBannerTone.WARNING, "Chọn dòng cần nhận trước.")
            return
        }
        if (line.trackingType != GrTrackingType.NONE) {
            setBanner(GrBannerTone.WARNING, "Dòng này cần quét serial/lô.")
            return
        }
        val locationId = requireLocation() ?: return
        val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
        if (qty <= 0) {
            setBanner(GrBannerTone.WARNING, "Nhập số lượng hợp lệ.")
            return
        }
        runReceive(line) { goodsReceiptRepository.receiveNone(line, locationId, qty) }
    }

    private fun load(initial: Boolean) {
        if (initial) mutableUiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val header = goodsReceiptRepository.loadHeader(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, loadError = appError.message) }
                logger.error(TAG, "Tải header GR lỗi: ${appError.code}")
                return@launch
            }

            // Auto start receiving nếu phiếu đang CREATED (giống scanner PWA).
            var effectiveHeader = header
            if (header.status.needsStartReceiving) {
                mutableUiState.update { it.copy(isStarting = true) }
                val startResult = goodsReceiptRepository.startReceiving(headerId)
                if (startResult.isSuccess) {
                    effectiveHeader = goodsReceiptRepository.loadHeader(headerId).getOrDefault(header)
                } else {
                    val message = startResult.exceptionOrNull()?.toAppError()?.message ?: "Lỗi không xác định."
                    setBanner(GrBannerTone.WARNING, "Chưa thể bắt đầu nhận hàng. $message")
                }
                mutableUiState.update { it.copy(isStarting = false) }
            }

            val lines = goodsReceiptRepository.loadLines(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, header = effectiveHeader, loadError = appError.message) }
                return@launch
            }

            // Vị trí nhập theo kho của phiếu (đảm bảo location thuộc đúng kho).
            val locations = effectiveHeader.warehouseId
                ?.let { wh -> goodsReceiptRepository.loadLocations(wh).getOrNull() }
                ?: emptyList()

            loaded = true
            mutableUiState.update { current ->
                val nextActive = current.activeLineId
                    ?.takeIf { id -> lines.any { it.id == id } }
                    ?: lines.firstOrNull { !it.isComplete }?.id
                    ?: lines.firstOrNull()?.id
                current.copy(
                    isLoading = false,
                    header = effectiveHeader,
                    lines = lines,
                    locations = locations,
                    loadError = null,
                    activeLineId = nextActive,
                )
            }
            applyScannerMode(uiState.value.activeLine?.trackingType)
        }
    }

    fun refresh() = reloadDocument(banner = "Đã tải lại phiếu.")

    private fun reloadDocument(banner: String?) {
        viewModelScope.launch {
            val header = goodsReceiptRepository.loadHeader(headerId).getOrNull()
            val lines = goodsReceiptRepository.loadLines(headerId).getOrNull()
            mutableUiState.update { current ->
                val effectiveLines = lines ?: current.lines
                current.copy(
                    header = header ?: current.header,
                    lines = effectiveLines,
                    activeLineId = current.activeLineId?.takeIf { id -> effectiveLines.any { it.id == id } }
                        ?: effectiveLines.firstOrNull { !it.isComplete }?.id
                        ?: effectiveLines.firstOrNull()?.id,
                    banner = banner?.let { GrBanner(GrBannerTone.WARNING, it) } ?: current.banner,
                )
            }
        }
    }

    private fun onScan(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        val state = uiState.value
        if (!state.canScan) {
            setBanner(GrBannerTone.WARNING, "Phiếu đang ở trạng thái ${state.header?.status?.label ?: "—"}, không thể quét.")
            return
        }
        val line = state.activeLine ?: run {
            setBanner(GrBannerTone.WARNING, "Chọn dòng cần nhận trước khi quét.")
            return
        }
        if (line.isComplete) {
            setBanner(GrBannerTone.WARNING, "Dòng \"${line.productCode}\" đã nhận đủ.")
            return
        }
        val locationId = requireLocation() ?: return
        mutableUiState.update { it.copy(scannedCode = "") }

        when (line.trackingType) {
            GrTrackingType.SERIAL -> {
                val key = "${line.id}|${code.lowercase()}"
                if (sessionSerials.contains(key)) {
                    setBanner(GrBannerTone.ERROR, "Serial \"$code\" đã nhận trong phiên này.")
                    return
                }
                if (line.expectedQty > 0 && line.receivedQty + 1.0 > line.expectedQty + 1e-9) {
                    setBanner(GrBannerTone.WARNING, "Vượt số lượng cần nhận của dòng.")
                    return
                }
                val dateErr = dateValidationError(line)
                if (dateErr != null) {
                    setBanner(GrBannerTone.WARNING, dateErr)
                    return
                }
                runReceive(line, onSuccessExtra = { sessionSerials.add(key) }) {
                    goodsReceiptRepository.receiveSerial(line, locationId, code, mfgOrNull(), expiryOrNull())
                }
            }

            GrTrackingType.LOT -> {
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (qty <= 0) {
                    setBanner(GrBannerTone.WARNING, "Nhập số lượng lô hợp lệ trước khi quét.")
                    return
                }
                if (line.expectedQty > 0 && line.receivedQty + qty > line.expectedQty + 1e-9) {
                    setBanner(GrBannerTone.WARNING, "Vượt số lượng cần nhận của dòng.")
                    return
                }
                val dateErr = dateValidationError(line)
                if (dateErr != null) {
                    setBanner(GrBannerTone.WARNING, dateErr)
                    return
                }
                runReceive(line) {
                    goodsReceiptRepository.receiveLot(line, locationId, code, qty, mfgOrNull(), expiryOrNull())
                }
            }

            GrTrackingType.NONE -> {
                if (!code.equals(line.productCode, ignoreCase = true)) {
                    setBanner(GrBannerTone.ERROR, "Mã quét không khớp sản phẩm của dòng này.")
                    return
                }
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (qty <= 0) {
                    setBanner(GrBannerTone.WARNING, "Nhập số lượng hợp lệ.")
                    return
                }
                if (line.expectedQty > 0 && line.receivedQty + qty > line.expectedQty + 1e-9) {
                    setBanner(GrBannerTone.WARNING, "Vượt số lượng cần nhận của dòng.")
                    return
                }
                runReceive(line) { goodsReceiptRepository.receiveNone(line, locationId, qty) }
            }
        }
    }

    private fun requireLocation(): String? {
        val locId = uiState.value.selectedLocationId
        if (locId.isNullOrBlank()) {
            setBanner(GrBannerTone.WARNING, "Chọn vị trí nhập trong kho trước khi nhận hàng.")
            return null
        }
        return locId
    }

    private fun mfgOrNull(): String? = uiState.value.mfgDateText.trim().takeIf { it.isNotEmpty() }

    private fun expiryOrNull(): String? = uiState.value.expiryDateText.trim().takeIf { it.isNotEmpty() }

    /** Kiểm tra NSX/HSD theo yêu cầu dòng + định dạng + thứ tự ngày. Trả về message lỗi hoặc null. */
    private fun dateValidationError(line: GrLine): String? {
        val mfgRaw = mfgOrNull()
        val expRaw = expiryOrNull()
        if (line.needsMfgDate && mfgRaw == null) return "Dòng này bắt buộc nhập ngày sản xuất (NSX)."
        if (line.needsExpiryDate && expRaw == null) return "Dòng này bắt buộc nhập hạn sử dụng (HSD)."

        val mfg = mfgRaw?.let { parseDate(it) ?: return "NSX không hợp lệ (định dạng YYYY-MM-DD)." }
        val exp = expRaw?.let { parseDate(it) ?: return "HSD không hợp lệ (định dạng YYYY-MM-DD)." }
        if (exp != null && exp.isBefore(LocalDate.now())) return "HSD không được ở quá khứ."
        if (mfg != null && exp != null && exp.isBefore(mfg)) return "HSD phải lớn hơn hoặc bằng NSX."
        return null
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim(), DATE_FORMAT) }.getOrNull()

    private fun runReceive(
        line: GrLine,
        onSuccessExtra: () -> Unit = {},
        block: suspend () -> Result<GrReceiveSuccess>,
    ) {
        if (uiState.value.processingLineId != null) return
        mutableUiState.update { it.copy(processingLineId = line.id, banner = null) }
        viewModelScope.launch {
            block()
                .onSuccess { success ->
                    onSuccessExtra()
                    // Optimistic: tăng receivedQty của dòng; nền refresh để đồng bộ chuẩn.
                    mutableUiState.update { current ->
                        current.copy(
                            processingLineId = null,
                            lines = current.lines.map { l ->
                                if (l.id == success.lineId) {
                                    val next = l.receivedQty + success.appliedQty
                                    l.copy(receivedQty = if (l.expectedQty > 0) next.coerceAtMost(l.expectedQty) else next)
                                } else {
                                    l
                                }
                            },
                            banner = GrBanner(GrBannerTone.SUCCESS, success.message),
                        )
                    }
                    backgroundRefreshLines()
                }
                .onFailure { throwable ->
                    handleReceiveFailure(throwable)
                }
        }
    }

    private fun handleReceiveFailure(throwable: Throwable) {
        val appError = throwable.toAppError()
        mutableUiState.update { it.copy(processingLineId = null) }
        if (GoodsReceiptErrorMapper.isStatusConflict(appError.message) || GoodsReceiptErrorMapper.isStatusConflict(appError.code)) {
            setBanner(GrBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
            reloadDocument(banner = null)
            return
        }
        setBanner(GrBannerTone.ERROR, appError.message)
        logger.error(TAG, "Nhận hàng GR lỗi: ${appError.code}")
    }

    private fun backgroundRefreshLines() {
        viewModelScope.launch {
            goodsReceiptRepository.loadLines(headerId)
                .onSuccess { lines ->
                    mutableUiState.update { current ->
                        if (lines.isEmpty()) current else current.copy(lines = lines)
                    }
                }
                .onFailure { logger.error(TAG, "Refresh lines lỗi: ${it.message}") }
        }
    }

    // region submit / complete

    fun submit() {
        val state = uiState.value
        if (!state.canSubmit) {
            setBanner(GrBannerTone.WARNING, "Phiếu không ở trạng thái có thể chốt nhận.")
            return
        }
        if (state.hasShortage) {
            mutableUiState.update {
                it.copy(
                    confirm = GrConfirm(
                        title = "Còn nhận thiếu",
                        message = shortageMessage(state.lines),
                        confirmLabel = "Vẫn chốt nhận",
                        action = GrConfirmAction.SUBMIT,
                    ),
                )
            }
            return
        }
        doSubmit()
    }

    fun complete() {
        val state = uiState.value
        if (!state.canComplete) {
            setBanner(GrBannerTone.WARNING, "Phiếu không ở trạng thái có thể hoàn tất.")
            return
        }
        if (state.hasShortage) {
            mutableUiState.update {
                it.copy(
                    confirm = GrConfirm(
                        title = "Hoàn tất khi còn thiếu",
                        message = shortageMessage(state.lines),
                        confirmLabel = "Vẫn hoàn tất",
                        action = GrConfirmAction.COMPLETE,
                    ),
                )
            }
            return
        }
        doComplete()
    }

    fun confirmDialog() {
        val action = uiState.value.confirm?.action
        mutableUiState.update { it.copy(confirm = null) }
        when (action) {
            GrConfirmAction.SUBMIT -> doSubmit()
            GrConfirmAction.COMPLETE -> doComplete()
            null -> Unit
        }
    }

    fun dismissDialog() {
        mutableUiState.update { it.copy(confirm = null) }
    }

    private fun doSubmit() {
        if (uiState.value.isSubmitting) return
        if (uiState.value.isOffline) {
            setBanner(GrBannerTone.WARNING, "Cần có mạng để hoàn tất.")
            return
        }
        mutableUiState.update { it.copy(isSubmitting = true, banner = null) }
        viewModelScope.launch {
            val result = goodsReceiptRepository.submitReceive(headerId)
            if (result.isFailure) {
                finalizeFailure(result.exceptionOrNull(), "Không thể chốt nhận.")
                return@launch
            }
            val header = goodsReceiptRepository.loadHeader(headerId).getOrNull()
            val lines = goodsReceiptRepository.loadLines(headerId).getOrNull()
            val completed = header?.status == GrStatus.COMPLETED
            mutableUiState.update {
                it.copy(
                    isSubmitting = false,
                    header = header ?: it.header,
                    lines = lines ?: it.lines,
                    finished = completed,
                    banner = GrBanner(
                        GrBannerTone.SUCCESS,
                        if (completed) "Đã hoàn tất nhập kho phiếu." else "Đã chốt nhận. Phiếu chuyển sang trạng thái đã nhận.",
                    ),
                )
            }
        }
    }

    private fun doComplete() {
        if (uiState.value.isCompleting) return
        if (uiState.value.isOffline) {
            setBanner(GrBannerTone.WARNING, "Cần có mạng để hoàn tất.")
            return
        }
        mutableUiState.update { it.copy(isCompleting = true, banner = null) }
        viewModelScope.launch {
            val result = goodsReceiptRepository.complete(headerId)
            if (result.isFailure) {
                finalizeFailure(result.exceptionOrNull(), "Không thể hoàn tất nhập kho.")
                return@launch
            }
            val header = goodsReceiptRepository.loadHeader(headerId).getOrNull()
            mutableUiState.update {
                it.copy(
                    isCompleting = false,
                    header = header ?: it.header,
                    finished = true,
                    banner = GrBanner(GrBannerTone.SUCCESS, "Đã hoàn tất nhập kho. Tồn kho được cập nhật."),
                )
            }
        }
    }

    private fun finalizeFailure(throwable: Throwable?, prefix: String) {
        val appError = (throwable ?: RuntimeException("Lỗi không xác định.")).toAppError()
        mutableUiState.update { it.copy(isSubmitting = false, isCompleting = false) }
        if (GoodsReceiptErrorMapper.isStatusConflict(appError.message)) {
            setBanner(GrBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
            reloadDocument(banner = null)
            return
        }
        setBanner(GrBannerTone.ERROR, "$prefix ${appError.message}")
        logger.error(TAG, "Finalize GR lỗi: ${appError.code}")
    }

    // endregion

    private fun shortageMessage(lines: List<GrLine>): String {
        val shortages = lines.filter { it.receivedQty + 1e-9 < it.expectedQty }
        val shown = shortages.take(6).joinToString("\n") { line ->
            "• ${line.productCode}: cần ${formatQty(line.expectedQty)} • đã nhận ${formatQty(line.receivedQty)} • thiếu ${formatQty(line.expectedQty - line.receivedQty)}"
        }
        val more = shortages.size - 6
        val suffix = if (more > 0) "\n… và $more sản phẩm khác." else ""
        return "Phiếu còn thiếu ${shortages.size} sản phẩm so với kế hoạch:\n$shown$suffix"
    }

    private fun applyScannerMode(trackingType: GrTrackingType?) {
        when (trackingType) {
            GrTrackingType.SERIAL -> {
                scannerManager.setMode(ScannerMode.SERIAL)
                scannerManager.setContinuousSerial(true)
            }

            GrTrackingType.LOT -> {
                scannerManager.setMode(ScannerMode.LOT)
                scannerManager.setContinuousSerial(false)
            }

            else -> {
                scannerManager.setMode(ScannerMode.PRODUCT)
                scannerManager.setContinuousSerial(false)
            }
        }
    }

    private fun setBanner(tone: GrBannerTone, message: String) {
        mutableUiState.update { it.copy(banner = GrBanner(tone, message)) }
    }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')

    companion object {
        private const val TAG = "GoodsReceiptReceiveVM"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun factory(
            headerId: String,
            scannerManager: ScannerManager,
            goodsReceiptRepository: GoodsReceiptRepository,
            connectivityObserver: ConnectivityObserver,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoodsReceiptReceiveViewModel(
                    headerId = headerId,
                    scannerManager = scannerManager,
                    goodsReceiptRepository = goodsReceiptRepository,
                    connectivityObserver = connectivityObserver,
                    logger = logger,
                )
            }
        }
    }
}
