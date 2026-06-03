package vn.delfi.xcloudwms.feature.inventorycount

import vn.delfi.xcloudwms.domain.model.IcHeader

data class InventoryCountListUiState(
    val isOffline: Boolean = false,
    val hasWarehouse: Boolean = false,
    val warehouseLabel: String = "Chưa chọn",
    val isLoading: Boolean = false,
    val headers: List<IcHeader> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
    /** ID phiếu cần mở ngay (khi quét trúng đúng 1 phiếu) — screen tiêu thụ rồi clear. */
    val pendingOpenHeaderId: String? = null,
) {
    val visibleHeaders: List<IcHeader>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return headers
            return headers.filter { header ->
                listOfNotNull(
                    header.code,
                    header.warehouseLabel,
                    header.id,
                ).any { it.lowercase().contains(q) }
            }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && visibleHeaders.isEmpty()
}
