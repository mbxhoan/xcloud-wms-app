package vn.delfi.xcloudwms.feature.goodsissue

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
import vn.delfi.xcloudwms.data.gi.GoodsIssueErrorMapper
import vn.delfi.xcloudwms.data.gi.GoodsIssueRepository
import vn.delfi.xcloudwms.domain.model.GiLine
import vn.delfi.xcloudwms.domain.model.GiStatus
import vn.delfi.xcloudwms.domain.model.GiTrackingType

class GoodsIssuePickViewModel(
    private val headerId: String,
    private val scannerManager: ScannerManager,
    private val goodsIssueRepository: GoodsIssueRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GoodsIssuePickUiState())
    val uiState: StateFlow<GoodsIssuePickUiState> = mutableUiState.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Success -> onScan(event.parsed.normalized)
                    is ScanEvent.Error -> setBanner(GiBannerTone.ERROR, event.message)
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

    fun selectLine(lineId: String) {
        val line = uiState.value.lines.firstOrNull { it.id == lineId } ?: return
        mutableUiState.update {
            it.copy(
                activeLineId = lineId,
                qtyText = if (line.trackingType == GiTrackingType.SERIAL) "1" else it.qtyText.ifBlank { "1" },
                banner = null,
            )
        }
        applyScannerMode(line.trackingType)
    }

    fun submitScannedCode() {
        val code = uiState.value.scannedCode
        if (code.isNotBlank()) onScan(code)
    }

    /** Nút "Lấy" cho dòng NONE (không cần quét). */
    fun pickActiveNoneQuantity() {
        val state = uiState.value
        val line = state.activeLine ?: run {
            setBanner(GiBannerTone.WARNING, "Chọn dòng cần lấy trước.")
            return
        }
        if (line.trackingType != GiTrackingType.NONE) {
            setBanner(GiBannerTone.WARNING, "Dòng này cần quét serial/lot.")
            return
        }
        val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
        runPick(line) { goodsIssueRepository.pickQuantity(line, qty) }
    }

    private fun load(initial: Boolean) {
        if (initial) mutableUiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val header = goodsIssueRepository.loadHeader(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, loadError = appError.message) }
                logger.error(TAG, "Tải header GI lỗi: ${appError.code}")
                return@launch
            }

            // Auto start picking nếu phiếu đang CREATED (giống scanner PWA).
            var effectiveHeader = header
            if (header.status.needsStartPicking) {
                mutableUiState.update { it.copy(isStarting = true) }
                val startResult = goodsIssueRepository.startPicking(headerId)
                if (startResult.isSuccess) {
                    effectiveHeader = goodsIssueRepository.loadHeader(headerId).getOrDefault(header)
                } else {
                    val message = startResult.exceptionOrNull()?.toAppError()?.message ?: "Lỗi không xác định."
                    setBanner(GiBannerTone.WARNING, "Chưa thể bắt đầu picking. $message")
                }
                mutableUiState.update { it.copy(isStarting = false) }
            }

            val lines = goodsIssueRepository.loadLines(headerId).getOrElse { throwable ->
                val appError = throwable.toAppError()
                mutableUiState.update { it.copy(isLoading = false, header = effectiveHeader, loadError = appError.message) }
                return@launch
            }

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
            val header = goodsIssueRepository.loadHeader(headerId).getOrNull()
            val lines = goodsIssueRepository.loadLines(headerId).getOrNull()
            mutableUiState.update { current ->
                current.copy(
                    header = header ?: current.header,
                    lines = lines ?: current.lines,
                    activeLineId = current.activeLineId?.takeIf { id -> (lines ?: current.lines).any { it.id == id } }
                        ?: (lines ?: current.lines).firstOrNull { !it.isComplete }?.id
                        ?: (lines ?: current.lines).firstOrNull()?.id,
                    banner = banner?.let { GiBanner(GiBannerTone.WARNING, it) } ?: current.banner,
                )
            }
        }
    }

    private fun onScan(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        val state = uiState.value
        if (!state.canScan) {
            setBanner(GiBannerTone.WARNING, "Phiếu đang ở trạng thái ${state.header?.status?.label ?: "—"}, không thể quét.")
            return
        }
        val line = state.activeLine ?: run {
            setBanner(GiBannerTone.WARNING, "Chọn dòng cần lấy trước khi quét.")
            return
        }
        if (line.isComplete) {
            setBanner(GiBannerTone.WARNING, "Dòng \"${line.productCode}\" đã pick đủ.")
            return
        }
        mutableUiState.update { it.copy(scannedCode = "") }

        when (line.trackingType) {
            GiTrackingType.SERIAL -> {
                if (line.pickedQty + 1.0 > line.plannedQty + 1e-9) {
                    setBanner(GiBannerTone.WARNING, "Vượt số lượng cần pick của dòng.")
                    return
                }
                val header = state.header ?: return
                runPick(line) { goodsIssueRepository.pickSerial(header, line, code) }
            }

            GiTrackingType.LOT -> {
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (qty <= 0) {
                    setBanner(GiBannerTone.WARNING, "Nhập số lượng lot hợp lệ trước khi quét.")
                    return
                }
                if (line.pickedQty + qty > line.plannedQty + 1e-9) {
                    setBanner(GiBannerTone.WARNING, "Vượt số lượng cần pick của dòng.")
                    return
                }
                val header = state.header ?: return
                runPick(line) { goodsIssueRepository.pickLot(header, line, code, qty) }
            }

            GiTrackingType.NONE -> {
                if (!code.equals(line.productCode, ignoreCase = true)) {
                    setBanner(GiBannerTone.ERROR, "Mã quét không khớp sản phẩm của dòng này.")
                    return
                }
                val qty = state.qtyText.trim().toDoubleOrNull() ?: 0.0
                if (line.pickedQty + qty > line.plannedQty + 1e-9) {
                    setBanner(GiBannerTone.WARNING, "Vượt số lượng cần pick của dòng.")
                    return
                }
                runPick(line) { goodsIssueRepository.pickQuantity(line, qty) }
            }
        }
    }

    private fun runPick(line: GiLine, block: suspend () -> Result<vn.delfi.xcloudwms.domain.model.GiPickSuccess>) {
        if (uiState.value.processingLineId != null) return
        mutableUiState.update { it.copy(processingLineId = line.id, banner = null) }
        viewModelScope.launch {
            block()
                .onSuccess { success ->
                    // Optimistic: tăng pickedQty của dòng; nền refresh để đồng bộ qty_issued chuẩn.
                    mutableUiState.update { current ->
                        current.copy(
                            processingLineId = null,
                            lines = current.lines.map { l ->
                                if (l.id == success.lineId) {
                                    val next = l.pickedQty + success.appliedQty
                                    l.copy(pickedQty = if (l.plannedQty > 0) next.coerceAtMost(l.plannedQty) else next)
                                } else {
                                    l
                                }
                            },
                            banner = GiBanner(GiBannerTone.SUCCESS, success.message),
                        )
                    }
                    backgroundRefreshLines()
                }
                .onFailure { throwable ->
                    handlePickFailure(throwable)
                }
        }
    }

    private fun handlePickFailure(throwable: Throwable) {
        val appError = throwable.toAppError()
        mutableUiState.update { it.copy(processingLineId = null) }
        if (GoodsIssueErrorMapper.isStatusConflict(appError.message) || GoodsIssueErrorMapper.isStatusConflict(appError.code)) {
            setBanner(GiBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
            reloadDocument(banner = null)
            return
        }
        setBanner(GiBannerTone.ERROR, appError.message)
        logger.error(TAG, "Pick GI lỗi: ${appError.code}")
    }

    private fun backgroundRefreshLines() {
        viewModelScope.launch {
            goodsIssueRepository.loadLines(headerId)
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
            setBanner(GiBannerTone.WARNING, "Phiếu không ở trạng thái có thể chốt picking.")
            return
        }
        if (state.hasShortage) {
            mutableUiState.update {
                it.copy(
                    confirm = GiConfirm(
                        title = "Còn pick thiếu",
                        message = shortageMessage(state.lines),
                        confirmLabel = "Vẫn chốt phiếu",
                        action = GiConfirmAction.SUBMIT,
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
            setBanner(GiBannerTone.WARNING, "Phiếu không ở trạng thái có thể xuất kho.")
            return
        }
        if (state.hasShortage) {
            mutableUiState.update {
                it.copy(
                    confirm = GiConfirm(
                        title = "Xuất kho khi còn thiếu",
                        message = shortageMessage(state.lines),
                        confirmLabel = "Vẫn xuất kho",
                        action = GiConfirmAction.COMPLETE,
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
            GiConfirmAction.SUBMIT -> doSubmit()
            GiConfirmAction.COMPLETE -> doComplete()
            null -> Unit
        }
    }

    fun dismissDialog() {
        mutableUiState.update { it.copy(confirm = null) }
    }

    private fun doSubmit() {
        if (uiState.value.isSubmitting) return
        if (uiState.value.isOffline) {
            setBanner(GiBannerTone.WARNING, "Cần có mạng để hoàn tất.")
            return
        }
        mutableUiState.update { it.copy(isSubmitting = true, banner = null) }
        viewModelScope.launch {
            val result = goodsIssueRepository.submit(headerId)
            if (result.isFailure) {
                finalizeFailure(result.exceptionOrNull(), "Không thể chốt phiếu.")
                return@launch
            }
            val header = goodsIssueRepository.loadHeader(headerId).getOrNull()
            val lines = goodsIssueRepository.loadLines(headerId).getOrNull()
            val completed = header?.status == GiStatus.COMPLETED
            mutableUiState.update {
                it.copy(
                    isSubmitting = false,
                    header = header ?: it.header,
                    lines = lines ?: it.lines,
                    finished = completed,
                    banner = GiBanner(
                        GiBannerTone.SUCCESS,
                        if (completed) "Đã hoàn tất xuất kho phiếu." else "Đã chốt picking. Phiếu chuyển sang trạng thái đã pick.",
                    ),
                )
            }
        }
    }

    private fun doComplete() {
        if (uiState.value.isCompleting) return
        if (uiState.value.isOffline) {
            setBanner(GiBannerTone.WARNING, "Cần có mạng để hoàn tất.")
            return
        }
        mutableUiState.update { it.copy(isCompleting = true, banner = null) }
        viewModelScope.launch {
            val result = goodsIssueRepository.complete(headerId)
            if (result.isFailure) {
                finalizeFailure(result.exceptionOrNull(), "Không thể xuất kho.")
                return@launch
            }
            val header = goodsIssueRepository.loadHeader(headerId).getOrNull()
            mutableUiState.update {
                it.copy(
                    isCompleting = false,
                    header = header ?: it.header,
                    finished = true,
                    banner = GiBanner(GiBannerTone.SUCCESS, "Đã xuất kho phiếu. Tồn kho được cập nhật."),
                )
            }
        }
    }

    private fun finalizeFailure(throwable: Throwable?, prefix: String) {
        val appError = (throwable ?: RuntimeException("Lỗi không xác định.")).toAppError()
        mutableUiState.update { it.copy(isSubmitting = false, isCompleting = false) }
        if (GoodsIssueErrorMapper.isStatusConflict(appError.message)) {
            setBanner(GiBannerTone.WARNING, "Phiếu đã thay đổi trạng thái. Đang tải lại…")
            reloadDocument(banner = null)
            return
        }
        setBanner(GiBannerTone.ERROR, "$prefix ${appError.message}")
        logger.error(TAG, "Finalize GI lỗi: ${appError.code}")
    }

    // endregion

    private fun shortageMessage(lines: List<GiLine>): String {
        val shortages = lines.filter { it.pickedQty + 1e-9 < it.plannedQty }
        val shown = shortages.take(6).joinToString("\n") { line ->
            "• ${line.productCode}: cần ${formatQty(line.plannedQty)} • đã pick ${formatQty(line.pickedQty)} • thiếu ${formatQty(line.plannedQty - line.pickedQty)}"
        }
        val more = shortages.size - 6
        val suffix = if (more > 0) "\n… và $more sản phẩm khác." else ""
        return "Phiếu còn thiếu ${shortages.size} sản phẩm so với kế hoạch:\n$shown$suffix"
    }

    private fun applyScannerMode(trackingType: GiTrackingType?) {
        when (trackingType) {
            GiTrackingType.SERIAL -> {
                scannerManager.setMode(ScannerMode.SERIAL)
                scannerManager.setContinuousSerial(true)
            }

            GiTrackingType.LOT -> {
                scannerManager.setMode(ScannerMode.LOT)
                scannerManager.setContinuousSerial(false)
            }

            else -> {
                scannerManager.setMode(ScannerMode.PRODUCT)
                scannerManager.setContinuousSerial(false)
            }
        }
    }

    private fun setBanner(tone: GiBannerTone, message: String) {
        mutableUiState.update { it.copy(banner = GiBanner(tone, message)) }
    }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')

    companion object {
        private const val TAG = "GoodsIssuePickVM"

        fun factory(
            headerId: String,
            scannerManager: ScannerManager,
            goodsIssueRepository: GoodsIssueRepository,
            connectivityObserver: ConnectivityObserver,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoodsIssuePickViewModel(
                    headerId = headerId,
                    scannerManager = scannerManager,
                    goodsIssueRepository = goodsIssueRepository,
                    connectivityObserver = connectivityObserver,
                    logger = logger,
                )
            }
        }
    }
}
