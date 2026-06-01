package vn.delfi.xcloudwms.feature.putaway

import vn.delfi.xcloudwms.domain.model.PaDraftLine
import vn.delfi.xcloudwms.domain.model.PaLocation
import vn.delfi.xcloudwms.domain.model.PaProduct
import vn.delfi.xcloudwms.domain.model.PaSession
import vn.delfi.xcloudwms.domain.model.PaTrackingType

/** Trường đang nhận dữ liệu quét ở stepper. */
enum class PaScanField { FROM_LOCATION, CODE, TO_LOCATION }

enum class BannerTone { SUCCESS, ERROR, WARNING }

data class PaBanner(
    val tone: BannerTone,
    val message: String,
)

data class PutawayUiState(
    val isOffline: Boolean = false,
    val warehouseLabel: String = "Chưa chọn",
    val hasWarehouse: Boolean = false,

    val isLoadingContext: Boolean = false,
    val contextError: String? = null,

    val isStartingSession: Boolean = false,
    val sessionNotes: String = "",

    val session: PaSession? = null,
    val isLoadingLines: Boolean = false,
    val draftLines: List<PaDraftLine> = emptyList(),

    val locations: List<PaLocation> = emptyList(),
    val products: List<PaProduct> = emptyList(),

    // Form stepper
    val activeScanField: PaScanField = PaScanField.FROM_LOCATION,
    val fromLocationId: String = "",
    val toLocationId: String = "",
    val selectedProductId: String? = null,
    val scannedCode: String = "",
    val qtyText: String = "1",
    val lineNotes: String = "",

    val isAddingLine: Boolean = false,
    val isSubmitting: Boolean = false,
    val deletingDetailId: String? = null,

    val banner: PaBanner? = null,
) {
    val selectedProduct: PaProduct?
        get() = selectedProductId?.let { id -> products.firstOrNull { it.id == id } }

    val trackingType: PaTrackingType
        get() = selectedProduct?.trackingType ?: PaTrackingType.NONE

    val canEditSession: Boolean
        get() = session?.status?.isEditable == true

    /** Cần nhập mã quét khi chưa chọn sản phẩm, hoặc sản phẩm có tracking SERIAL/LOT. */
    val requiresCode: Boolean
        get() = selectedProduct == null || trackingType != PaTrackingType.NONE

    /** SERIAL luôn qty = 1 nên ẩn ô nhập số lượng. */
    val showQtyInput: Boolean
        get() = trackingType != PaTrackingType.SERIAL

    val canSubmit: Boolean
        get() = canEditSession && draftLines.isNotEmpty() && !isSubmitting && !isAddingLine

    /** Bước hiện tại của stepper (1..4) để hiển thị tiến độ. */
    val currentStep: Int
        get() = when {
            fromLocationId.isBlank() -> 1
            requiresCode && scannedCode.isBlank() && selectedProductId == null -> 2
            showQtyInput && (qtyText.toDoubleOrNull() ?: 0.0) <= 0.0 -> 3
            toLocationId.isBlank() -> 4
            else -> 4
        }
}
