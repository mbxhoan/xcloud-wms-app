package vn.delfi.xcloudwms.feature.stocklookup

import vn.delfi.xcloudwms.domain.model.StockMatch
import vn.delfi.xcloudwms.domain.model.StockRow
import vn.delfi.xcloudwms.domain.model.StockSummary

data class StockLookupUiState(
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val manualCode: String = "",
    val query: String = "",
    val currentWarehouseLabel: String = "Chưa chọn",
    val match: StockMatch? = null,
    val summary: StockSummary? = null,
    val rows: List<StockRow> = emptyList(),
    val totalRowCount: Int = 0,
    val showAllWarehouses: Boolean = false,
    val hasResult: Boolean = false,
    val isEmptyResult: Boolean = false,
    val errorMessage: String? = null,
    val errorRetryable: Boolean = false,
    /** "Tự động Enter / Tab": bật thì quét xong tra cứu ngay, tắt thì chỉ đổ mã vào ô chờ bấm. */
    val autoSubmitScanInput: Boolean = true,
) {
    /** Số dòng tồn thuộc kho khác đang bị ẩn khi chỉ xem kho hiện tại. */
    val hiddenWarehouseRowCount: Int
        get() = if (showAllWarehouses) 0 else (totalRowCount - rows.size).coerceAtLeast(0)
}
