package vn.delfi.xcloudwms.feature.goodsissue

import vn.delfi.xcloudwms.domain.model.GiHeader

data class GoodsIssueListUiState(
    val isOffline: Boolean = false,
    val hasWarehouse: Boolean = false,
    val warehouseLabel: String = "Chưa chọn",
    val isLoading: Boolean = false,
    val headers: List<GiHeader> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
    /** ID phiếu cần mở ngay (khi quét trúng đúng 1 phiếu) — screen tiêu thụ rồi clear. */
    val pendingOpenHeaderId: String? = null,
) {
    val visibleHeaders: List<GiHeader>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return headers
            return headers.filter { header ->
                listOfNotNull(
                    header.code,
                    header.referenceNumber,
                    header.partnerLabel,
                    header.warehouseLabel,
                    header.id,
                ).any { it.lowercase().contains(q) }
            }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && visibleHeaders.isEmpty()
}
