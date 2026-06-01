package vn.delfi.xcloudwms.feature.goodsissue

import vn.delfi.xcloudwms.domain.model.GiHeader
import vn.delfi.xcloudwms.domain.model.GiLine
import vn.delfi.xcloudwms.domain.model.GiTrackingType

enum class GiBannerTone { SUCCESS, ERROR, WARNING }

data class GiBanner(val tone: GiBannerTone, val message: String)

data class GoodsIssuePickUiState(
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val header: GiHeader? = null,
    val lines: List<GiLine> = emptyList(),
    val loadError: String? = null,

    val activeLineId: String? = null,
    val scannedCode: String = "",
    val qtyText: String = "1",

    val isStarting: Boolean = false,
    val processingLineId: String? = null,
    val isSubmitting: Boolean = false,
    val isCompleting: Boolean = false,

    /** Hiển thị dialog xác nhận khi hoàn tất/xuất kho lúc còn pick thiếu. */
    val confirm: GiConfirm? = null,
    /** Phiếu đã COMPLETED → screen điều hướng quay lại. */
    val finished: Boolean = false,

    val banner: GiBanner? = null,
) {
    val activeLine: GiLine?
        get() = activeLineId?.let { id -> lines.firstOrNull { it.id == id } }

    val totalPlanned: Double
        get() = lines.sumOf { it.plannedQty }

    val totalPicked: Double
        get() = lines.sumOf { if (it.plannedQty > 0) it.pickedQty.coerceAtMost(it.plannedQty) else it.pickedQty }

    val allLinesComplete: Boolean
        get() = lines.isNotEmpty() && lines.all { it.isComplete }

    val hasShortage: Boolean
        get() = lines.any { it.pickedQty + 1e-9 < it.plannedQty }

    val canScan: Boolean
        get() = header?.status?.isScannable == true && !isStarting

    val canSubmit: Boolean
        get() = header?.status?.canSubmit == true && lines.isNotEmpty() && !isSubmitting && !isCompleting

    val canComplete: Boolean
        get() = header?.status?.canComplete == true && lines.isNotEmpty() && !isSubmitting && !isCompleting

    /** Active line cần nhập số lượng (LOT/NONE) thay vì serial liên tục. */
    val showQtyInput: Boolean
        get() = activeLine?.trackingType?.let { it == GiTrackingType.LOT || it == GiTrackingType.NONE } == true

    val isBusy: Boolean
        get() = isStarting || isSubmitting || isCompleting || processingLineId != null
}

/** Yêu cầu xác nhận hành động chốt/xuất kho (kèm callback định danh). */
data class GiConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: GiConfirmAction,
)

enum class GiConfirmAction { SUBMIT, COMPLETE }
