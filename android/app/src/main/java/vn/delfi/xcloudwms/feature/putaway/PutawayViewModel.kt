package vn.delfi.xcloudwms.feature.putaway

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
import vn.delfi.xcloudwms.data.putaway.PaAddLineRequest
import vn.delfi.xcloudwms.data.putaway.PutawayLineValidator
import vn.delfi.xcloudwms.data.putaway.PutawayRepository
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.domain.model.PaProduct
import vn.delfi.xcloudwms.domain.model.PaResolvedCode
import vn.delfi.xcloudwms.domain.model.PaTrackingType

class PutawayViewModel(
    private val scannerManager: ScannerManager,
    private val putawayRepository: PutawayRepository,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PutawayUiState())
    val uiState: StateFlow<PutawayUiState> = mutableUiState.asStateFlow()

    private var warehouseId: String? = null
    private var contextLoaded = false

    init {
        viewModelScope.launch {
            scannerManager.scanEvents.collect { event ->
                when (event) {
                    is ScanEvent.Success -> onScan(event.parsed.normalized)
                    is ScanEvent.Error -> setBanner(BannerTone.ERROR, event.message)
                }
            }
        }

        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                warehouseId = session.currentWarehouse?.id
                mutableUiState.update {
                    it.copy(
                        warehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn",
                        hasWarehouse = session.currentWarehouse != null,
                    )
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
        scannerManager.setMode(ScannerMode.GENERIC)
        scannerManager.start()
        if (!contextLoaded) {
            loadContext()
        }
    }

    fun onScreenLeft() {
        scannerManager.stop()
    }

    fun loadContext() {
        val wh = warehouseId
        if (wh.isNullOrBlank()) {
            mutableUiState.update {
                it.copy(
                    isLoadingContext = false,
                    contextError = "Chưa chọn kho làm việc. Vui lòng chọn kho trước khi sắp xếp.",
                )
            }
            return
        }
        mutableUiState.update { it.copy(isLoadingContext = true, contextError = null) }
        viewModelScope.launch {
            val locationsResult = putawayRepository.loadLocations(wh)
            val productsResult = putawayRepository.loadProducts(wh)

            val locations = locationsResult.getOrElse { throwable ->
                applyContextError(throwable)
                return@launch
            }
            val products = productsResult.getOrElse { throwable ->
                applyContextError(throwable)
                return@launch
            }

            contextLoaded = true
            mutableUiState.update {
                it.copy(
                    isLoadingContext = false,
                    contextError = null,
                    locations = locations,
                    products = products,
                )
            }

            // Tiếp tục draft gần nhất nếu có (không chặn flow nếu lỗi).
            putawayRepository.loadActiveDraft(wh)
                .onSuccess { session ->
                    if (session != null) {
                        mutableUiState.update { it.copy(session = session) }
                        refreshLines(session.id)
                    }
                }
                .onFailure { logger.error(TAG, "Tải draft PA hiện có lỗi: ${it.message}") }
        }
    }

    private fun applyContextError(throwable: Throwable) {
        val appError = throwable.toAppError()
        mutableUiState.update {
            it.copy(
                isLoadingContext = false,
                contextError = appError.message,
            )
        }
        logger.error(TAG, "Tải dữ liệu PA lỗi: ${appError.code}")
    }

    fun updateSessionNotes(value: String) {
        mutableUiState.update { it.copy(sessionNotes = value) }
    }

    fun startSession() {
        val wh = warehouseId
        if (wh.isNullOrBlank()) {
            setBanner(BannerTone.WARNING, "Vui lòng chọn kho trước.")
            return
        }
        if (uiState.value.isStartingSession) return
        mutableUiState.update { it.copy(isStartingSession = true, banner = null) }
        viewModelScope.launch {
            putawayRepository.startSession(wh, uiState.value.sessionNotes)
                .onSuccess { session ->
                    mutableUiState.update {
                        it.copy(
                            isStartingSession = false,
                            session = session,
                            sessionNotes = "",
                            draftLines = emptyList(),
                            banner = PaBanner(
                                BannerTone.SUCCESS,
                                session.code?.let { code -> "Đã tạo phiên sắp xếp nháp: $code." }
                                    ?: "Đã tạo phiên sắp xếp nháp.",
                            ),
                        )
                    }
                    resetForm()
                }
                .onFailure { throwable ->
                    val appError = throwable.toAppError()
                    mutableUiState.update {
                        it.copy(
                            isStartingSession = false,
                            banner = PaBanner(BannerTone.ERROR, "Không thể tạo phiên sắp xếp. ${appError.message}"),
                        )
                    }
                }
        }
    }

    fun setActiveScanField(field: PaScanField) {
        mutableUiState.update { it.copy(activeScanField = field) }
    }

    fun selectFromLocation(id: String) {
        mutableUiState.update { it.copy(fromLocationId = id) }
    }

    fun selectToLocation(id: String) {
        mutableUiState.update { it.copy(toLocationId = id) }
    }

    fun selectProduct(productId: String?) {
        mutableUiState.update { state ->
            val product = productId?.let { id -> state.products.firstOrNull { it.id == id } }
            state.copy(
                selectedProductId = productId,
                // SERIAL luôn qty 1; sản phẩm khác giữ qty đang nhập.
                qtyText = if (product?.trackingType == PaTrackingType.SERIAL) "1" else state.qtyText,
            )
        }
    }

    fun updateScannedCode(value: String) {
        mutableUiState.update { it.copy(scannedCode = value) }
    }

    fun updateQty(value: String) {
        mutableUiState.update { it.copy(qtyText = value) }
    }

    fun updateLineNotes(value: String) {
        mutableUiState.update { it.copy(lineNotes = value) }
    }

    fun dismissBanner() {
        mutableUiState.update { it.copy(banner = null) }
    }

    private fun onScan(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        val state = uiState.value
        when (state.activeScanField) {
            PaScanField.FROM_LOCATION -> {
                val location = matchLocation(code)
                if (location == null) {
                    setBanner(BannerTone.WARNING, "Không tìm thấy vị trí “$code” trong kho hiện tại.")
                    return
                }
                mutableUiState.update {
                    it.copy(fromLocationId = location.id, activeScanField = PaScanField.CODE)
                }
            }

            PaScanField.TO_LOCATION -> {
                val location = matchLocation(code)
                if (location == null) {
                    setBanner(BannerTone.WARNING, "Không tìm thấy vị trí “$code” trong kho hiện tại.")
                    return
                }
                mutableUiState.update { it.copy(toLocationId = location.id) }
            }

            PaScanField.CODE -> mutableUiState.update { it.copy(scannedCode = code) }
        }
    }

    private fun matchLocation(code: String) =
        uiState.value.locations.firstOrNull { it.code.equals(code, ignoreCase = true) }

    fun addLine() {
        val state = uiState.value
        val session = state.session
        if (session == null || !state.canEditSession) {
            setBanner(BannerTone.WARNING, "Phiên hiện tại không ở trạng thái nháp để chỉnh sửa.")
            return
        }
        if (state.isAddingLine) return
        val wh = warehouseId
        if (wh.isNullOrBlank()) {
            setBanner(BannerTone.ERROR, "Thiếu kho làm việc.")
            return
        }

        val fromId = state.fromLocationId
        val toId = state.toLocationId
        if (fromId.isBlank()) {
            setBanner(BannerTone.WARNING, "Vui lòng chọn hoặc quét vị trí nguồn.")
            return
        }
        if (state.locations.none { it.id == fromId }) {
            setBanner(BannerTone.ERROR, "Vị trí nguồn không hợp lệ trong kho hiện tại.")
            return
        }
        if (toId.isBlank()) {
            setBanner(BannerTone.WARNING, "Vui lòng chọn hoặc quét vị trí đích.")
            return
        }
        if (state.locations.none { it.id == toId }) {
            setBanner(BannerTone.ERROR, "Vị trí đích không hợp lệ trong kho hiện tại.")
            return
        }
        if (fromId == toId) {
            setBanner(BannerTone.WARNING, "Vị trí đích phải khác vị trí nguồn.")
            return
        }

        val selectedProduct = state.selectedProduct
        val isAutoProductFlow = selectedProduct == null
        val code = state.scannedCode.trim()
        if (state.requiresCode && code.isBlank() && selectedProduct == null) {
            setBanner(BannerTone.WARNING, "Vui lòng quét SKU/serial/lot hoặc chọn sản phẩm.")
            return
        }

        mutableUiState.update { it.copy(isAddingLine = true, banner = null) }
        viewModelScope.launch {
            try {
                var productId: String? = selectedProduct?.id
                var trackingType: PaTrackingType = selectedProduct?.trackingType ?: PaTrackingType.NONE
                var serialId: String? = null
                var lotId: String? = null

                when {
                    selectedProduct == null -> {
                        val resolved = putawayRepository.resolveScannedCode(wh, fromId, code, null, null)
                        if (resolved is PaResolvedCode.Fail) {
                            failAddLine(resolved.message)
                            return@launch
                        }
                        resolved as PaResolvedCode.Ok
                        productId = resolved.productId
                        trackingType = resolved.trackingType
                        serialId = resolved.serialId
                        lotId = resolved.lotId
                    }

                    selectedProduct.trackingType == PaTrackingType.SERIAL -> {
                        val resolved = putawayRepository.resolveScannedCode(
                            wh, fromId, code, selectedProduct.id, PaTrackingType.SERIAL,
                        )
                        if (resolved is PaResolvedCode.Fail) {
                            failAddLine(resolved.message)
                            return@launch
                        }
                        serialId = (resolved as PaResolvedCode.Ok).serialId
                    }

                    selectedProduct.trackingType == PaTrackingType.LOT -> {
                        val resolved = putawayRepository.resolveScannedCode(
                            wh, fromId, code, selectedProduct.id, PaTrackingType.LOT,
                        )
                        if (resolved is PaResolvedCode.Fail) {
                            failAddLine(resolved.message)
                            return@launch
                        }
                        lotId = (resolved as PaResolvedCode.Ok).lotId
                    }
                }

                val resolvedProductId = productId
                if (resolvedProductId == null) {
                    failAddLine("Vui lòng chọn sản phẩm hoặc quét serial/lot hợp lệ.")
                    return@launch
                }
                val effectiveProduct: PaProduct? = state.products.firstOrNull { it.id == resolvedProductId }
                if (effectiveProduct == null) {
                    failAddLine("Không thể nhận diện sản phẩm từ mã quét trong kho/vị trí nguồn hiện tại.")
                    return@launch
                }

                val quantity = if (trackingType == PaTrackingType.SERIAL) {
                    1.0
                } else {
                    state.qtyText.trim().toDoubleOrNull() ?: 0.0
                }

                val validation = PutawayLineValidator.validate(
                    PutawayLineValidator.Input(
                        fromLocationId = fromId,
                        toLocationId = toId,
                        hasProductOrCode = true,
                        quantity = quantity,
                    ),
                )
                if (validation is PutawayLineValidator.Result.Invalid) {
                    failAddLine(validation.message)
                    return@launch
                }

                // Chặn trùng phía client (giống scanner PWA) trước khi gọi RPC.
                val duplicateMessage = detectDuplicate(trackingType, resolvedProductId, fromId, serialId, lotId)
                if (duplicateMessage != null) {
                    failAddLine(duplicateMessage)
                    return@launch
                }

                // Kiểm tra tồn khả dụng tức thời; backend vẫn xác thực lại lúc submit.
                val liveStock = putawayRepository.liveStockCheck(wh, fromId, resolvedProductId, lotId, serialId)
                    .getOrElse { throwable ->
                        failAddLine("Không thể kiểm tra tồn khả dụng. ${throwable.toAppError().message}")
                        return@launch
                    }
                val available = liveStock.availableQty
                if (available != null && quantity > available + 1e-9) {
                    failAddLine(
                        "Số lượng vượt tồn khả dụng (yêu cầu ${PutawayLineValidator.formatQty(quantity)}, " +
                            "khả dụng ${PutawayLineValidator.formatQty(available)}).",
                    )
                    return@launch
                }

                val request = PaAddLineRequest(
                    headerId = session.id,
                    fromLocationId = fromId,
                    toLocationId = toId,
                    productId = resolvedProductId,
                    quantity = quantity,
                    uomId = effectiveProduct.uomId,
                    lotId = lotId,
                    serialId = serialId,
                    notes = state.lineNotes,
                )
                putawayRepository.addLine(request)
                    .onSuccess {
                        mutableUiState.update {
                            it.copy(
                                isAddingLine = false,
                                scannedCode = "",
                                qtyText = "1",
                                lineNotes = "",
                                selectedProductId = if (isAutoProductFlow) null else it.selectedProductId,
                                activeScanField = PaScanField.CODE,
                                banner = PaBanner(BannerTone.SUCCESS, "Đã lưu dòng nháp vào phiên sắp xếp."),
                            )
                        }
                        refreshLines(session.id)
                        refreshProducts(wh)
                    }
                    .onFailure { throwable ->
                        failAddLine("Không thể lưu dòng sắp xếp. ${throwable.toAppError().message}")
                    }
            } catch (throwable: Throwable) {
                failAddLine(throwable.toAppError().message)
            }
        }
    }

    private fun detectDuplicate(
        trackingType: PaTrackingType,
        productId: String,
        fromId: String,
        serialId: String?,
        lotId: String?,
    ): String? {
        val lines = uiState.value.draftLines
        return when (trackingType) {
            PaTrackingType.SERIAL ->
                if (serialId != null && lines.any { it.serialId == serialId }) {
                    "Serial đã tồn tại trong phiên nháp."
                } else {
                    null
                }

            PaTrackingType.LOT ->
                if (lotId != null && lines.any { it.lotId == lotId && it.fromLocationId == fromId }) {
                    "Lot đã tồn tại trong phiên nháp tại cùng vị trí nguồn."
                } else {
                    null
                }

            PaTrackingType.NONE ->
                if (lines.any {
                        it.productId == productId && it.fromLocationId == fromId &&
                            it.lotId == null && it.serialId == null
                    }
                ) {
                    "Sản phẩm không tracking ở vị trí nguồn này đã được chuyển trong phiên nháp."
                } else {
                    null
                }
        }
    }

    private fun failAddLine(message: String) {
        mutableUiState.update {
            it.copy(isAddingLine = false, banner = PaBanner(BannerTone.ERROR, message))
        }
    }

    fun deleteLine(detailId: String) {
        if (uiState.value.deletingDetailId != null) return
        val session = uiState.value.session ?: return
        mutableUiState.update { it.copy(deletingDetailId = detailId, banner = null) }
        viewModelScope.launch {
            putawayRepository.deleteLine(detailId)
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            deletingDetailId = null,
                            banner = PaBanner(BannerTone.SUCCESS, "Đã xoá dòng nháp."),
                        )
                    }
                    refreshLines(session.id)
                }
                .onFailure { throwable ->
                    mutableUiState.update {
                        it.copy(
                            deletingDetailId = null,
                            banner = PaBanner(BannerTone.ERROR, "Không thể xoá dòng nháp. ${throwable.toAppError().message}"),
                        )
                    }
                }
        }
    }

    fun submit() {
        val state = uiState.value
        val session = state.session
        if (session == null || !state.canEditSession) {
            setBanner(BannerTone.WARNING, "Phiên hiện tại không còn ở trạng thái nháp để hoàn tất.")
            return
        }
        if (state.draftLines.isEmpty()) {
            setBanner(BannerTone.WARNING, "Phiên chưa có dòng nào để hoàn tất.")
            return
        }
        // Chống double-tap: nếu đang submit thì bỏ qua lần nhấn sau.
        if (state.isSubmitting) return
        mutableUiState.update { it.copy(isSubmitting = true, banner = null) }
        viewModelScope.launch {
            putawayRepository.submit(session.id)
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            session = null,
                            draftLines = emptyList(),
                            banner = PaBanner(BannerTone.SUCCESS, "Đã hoàn tất phiếu sắp xếp thành công."),
                        )
                    }
                    resetForm()
                    warehouseId?.let { refreshProducts(it) }
                }
                .onFailure { throwable ->
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            banner = PaBanner(BannerTone.ERROR, "Hoàn tất thất bại. ${throwable.toAppError().message}"),
                        )
                    }
                }
        }
    }

    private fun refreshLines(headerId: String) {
        mutableUiState.update { it.copy(isLoadingLines = true) }
        viewModelScope.launch {
            putawayRepository.loadDraftLines(headerId)
                .onSuccess { lines ->
                    mutableUiState.update { it.copy(isLoadingLines = false, draftLines = lines) }
                }
                .onFailure { throwable ->
                    mutableUiState.update {
                        it.copy(
                            isLoadingLines = false,
                            banner = PaBanner(BannerTone.ERROR, "Không thể tải dòng nháp. ${throwable.toAppError().message}"),
                        )
                    }
                }
        }
    }

    private fun refreshProducts(warehouseId: String) {
        viewModelScope.launch {
            putawayRepository.loadProducts(warehouseId)
                .onSuccess { products -> mutableUiState.update { it.copy(products = products) } }
                .onFailure { logger.error(TAG, "Làm mới sản phẩm PA lỗi: ${it.message}") }
        }
    }

    private fun resetForm() {
        mutableUiState.update {
            it.copy(
                activeScanField = PaScanField.FROM_LOCATION,
                fromLocationId = "",
                toLocationId = "",
                selectedProductId = null,
                scannedCode = "",
                qtyText = "1",
                lineNotes = "",
            )
        }
    }

    private fun setBanner(tone: BannerTone, message: String) {
        mutableUiState.update { it.copy(banner = PaBanner(tone, message)) }
    }

    companion object {
        private const val TAG = "PutawayViewModel"

        fun factory(
            scannerManager: ScannerManager,
            putawayRepository: PutawayRepository,
            sessionRepository: SessionRepository,
            connectivityObserver: ConnectivityObserver,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PutawayViewModel(
                    scannerManager = scannerManager,
                    putawayRepository = putawayRepository,
                    sessionRepository = sessionRepository,
                    connectivityObserver = connectivityObserver,
                    logger = logger,
                )
            }
        }
    }
}
