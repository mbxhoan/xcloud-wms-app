package vn.delfi.xcloudwms.feature.goodsreceipt

import vn.delfi.xcloudwms.domain.model.GrHeader
import vn.delfi.xcloudwms.domain.model.GrLine
import vn.delfi.xcloudwms.domain.model.GrLocation
import vn.delfi.xcloudwms.domain.model.GrTrackingType

enum class GrBannerTone { SUCCESS, ERROR, WARNING }

data class GrBanner(val tone: GrBannerTone, val message: String)

data class GrSessionDetail(
    val id: String,
    val lineId: String,
    val code: String,
    val qty: Double,
    val trackingType: GrTrackingType,
    val locationLabel: String,
    val mfgDate: String?,
    val expiryDate: String?,
    val scannedAtMillis: Long,
)

data class GoodsReceiptReceiveUiState(
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val header: GrHeader? = null,
    val lines: List<GrLine> = emptyList(),
    val locations: List<GrLocation> = emptyList(),
    val loadError: String? = null,

    val activeLineId: String? = null,
    val selectedLocationId: String? = null,
    val scannedCode: String = "",
    val qtyText: String = "1",
    val mfgDateText: String = "",
    val expiryDateText: String = "",

    val isStarting: Boolean = false,
    val processingLineId: String? = null,
    val isSubmitting: Boolean = false,

    /** Hiển thị dialog xác nhận khi chốt nhận lúc còn nhận thiếu. */
    val confirm: GrConfirm? = null,
    /** Đã chốt nhận xong → screen điều hướng quay lại. */
    val finished: Boolean = false,

    val banner: GrBanner? = null,
    val autoSubmitScanInput: Boolean = true,
    val sessionDetailsByLineId: Map<String, List<GrSessionDetail>> = emptyMap(),
) {
    val activeLine: GrLine?
        get() = activeLineId?.let { id -> lines.firstOrNull { it.id == id } }

    val selectedLocation: GrLocation?
        get() = selectedLocationId?.let { id -> locations.firstOrNull { it.id == id } }

    val totalExpected: Double
        get() = lines.sumOf { it.expectedQty }

    val totalReceived: Double
        get() = lines.sumOf { if (it.expectedQty > 0) it.receivedQty.coerceAtMost(it.expectedQty) else it.receivedQty }

    val allLinesComplete: Boolean
        get() = lines.isNotEmpty() && lines.all { it.isComplete }

    val hasShortage: Boolean
        get() = lines.any { it.receivedQty + 1e-9 < it.expectedQty }

    val canScan: Boolean
        get() = header?.status?.isScannable == true && !isStarting

    val requiresLocationSelection: Boolean
        get() = canScan && activeLine != null && selectedLocationId == null

    val canSubmitScannedCode: Boolean
        get() = !isBusy && canScan && activeLine != null && selectedLocationId != null && scannedCode.isNotBlank()

    val scanButtonLabel: String
        get() = if (requiresLocationSelection) "Chọn vị trí nhập trước" else "Nhận theo mã quét"

    /** "Lưu": chỉ lưu data đã quét, không đổi trạng thái → hiển thị khi phiếu còn đang nhận. */
    val canSaveDraft: Boolean
        get() = header?.status?.isScannable == true && lines.isNotEmpty() && !isStarting && !isSubmitting

    /** "Hoàn tất": chốt nhận RECEIVING → RECEIVED (đã nhận). */
    val canSubmit: Boolean
        get() = header?.status?.canSubmit == true && lines.isNotEmpty() && !isSubmitting

    /** Active line cần nhập số lượng (LOT/NONE) thay vì serial từng cái. */
    val showQtyInput: Boolean
        get() = activeLine?.trackingType?.let { it == GrTrackingType.LOT || it == GrTrackingType.NONE } == true

    val needsMfgInput: Boolean
        get() = activeLine?.needsMfgDate == true

    val needsExpiryInput: Boolean
        get() = activeLine?.needsExpiryDate == true

    val isBusy: Boolean
        get() = isStarting || isSubmitting || processingLineId != null

    val canReceiveNoneQuantity: Boolean
        get() = !isBusy && canScan && activeLine?.trackingType == GrTrackingType.NONE && selectedLocationId != null

    fun sessionDetailsForLine(lineId: String): List<GrSessionDetail> = sessionDetailsByLineId[lineId].orEmpty()
}

/** Yêu cầu xác nhận hành động chốt nhận khi còn nhận thiếu (kèm callback định danh). */
data class GrConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: GrConfirmAction,
)

enum class GrConfirmAction { SUBMIT }
