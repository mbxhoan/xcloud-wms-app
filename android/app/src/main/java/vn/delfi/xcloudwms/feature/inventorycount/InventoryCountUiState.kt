package vn.delfi.xcloudwms.feature.inventorycount

import vn.delfi.xcloudwms.domain.model.IcHeader
import vn.delfi.xcloudwms.domain.model.IcLine
import vn.delfi.xcloudwms.domain.model.IcLocation
import vn.delfi.xcloudwms.domain.model.IcTrackingType

enum class IcBannerTone { SUCCESS, ERROR, WARNING }

data class IcBanner(val tone: IcBannerTone, val message: String)

data class InventoryCountUiState(
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val header: IcHeader? = null,
    val lines: List<IcLine> = emptyList(),
    val locations: List<IcLocation> = emptyList(),
    val loadError: String? = null,

    val activeLineId: String? = null,
    val selectedLocationId: String? = null,
    val locationQuery: String = "",
    val scannedCode: String = "",
    val qtyText: String = "1",

    val isStarting: Boolean = false,
    val processingLineId: String? = null,

    /** Dialog kết thúc kiểm kê (yêu cầu ghi chú). */
    val showFinishDialog: Boolean = false,
    val finishNote: String = "",
    val isFinishing: Boolean = false,

    /** Phiếu đã COMPLETED → screen điều hướng quay lại. */
    val finished: Boolean = false,

    val banner: IcBanner? = null,
) {
    val activeLine: IcLine?
        get() = activeLineId?.let { id -> lines.firstOrNull { it.id == id } }

    val selectedLocation: IcLocation?
        get() = selectedLocationId?.let { id -> locations.firstOrNull { it.id == id } }

    val isBlind: Boolean
        get() = header?.isBlind == true

    val totalExpected: Double
        get() = lines.sumOf { it.expectedQty }

    val totalCounted: Double
        get() = lines.sumOf { it.countedQty }

    val canScan: Boolean
        get() = header?.status?.isScannable == true && !isStarting

    val canFinish: Boolean
        get() = header?.status?.canFinish == true && lines.isNotEmpty() && !isFinishing

    /** Active line nhập số lượng (LOT/NONE) thay vì serial từng cái. */
    val showQtyInput: Boolean
        get() = activeLine?.trackingType?.let { it == IcTrackingType.LOT || it == IcTrackingType.NONE } == true

    val isBusy: Boolean
        get() = isStarting || isFinishing || processingLineId != null

    fun filteredLocations(): List<IcLocation> {
        val q = locationQuery.trim().lowercase()
        if (q.isEmpty()) return locations.take(30)
        return locations.filter { loc ->
            loc.code.lowercase().contains(q) || (loc.name?.lowercase()?.contains(q) == true)
        }.take(30)
    }
}
