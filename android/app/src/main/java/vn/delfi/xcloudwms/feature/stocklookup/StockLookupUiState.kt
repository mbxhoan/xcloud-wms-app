package vn.delfi.xcloudwms.feature.stocklookup

import vn.delfi.xcloudwms.domain.model.LookupHistoryItem
import vn.delfi.xcloudwms.domain.model.StockLookupResult

data class StockLookupUiState(
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val manualCode: String = "",
    val query: String = "",
    /** Mã đang được tra cứu (để hiện "Đang tra cứu: <mã>"). */
    val busyCode: String? = null,
    val currentWarehouseLabel: String = "Chưa chọn",
    /** Lịch sử tra cứu gần đây trên thiết bị, mới nhất lên đầu. */
    val history: List<LookupHistoryItem> = emptyList(),
    /** Kết quả có match → mở sheet chi tiết. */
    val detailResult: StockLookupResult? = null,
    /** Mã không tìm thấy → mở sheet "Không tìm thấy mã". */
    val notFoundCode: String? = null,
    val errorMessage: String? = null,
    val errorRetryable: Boolean = false,
    /** "Tự động Enter / Tab": bật thì quét xong tra cứu ngay, tắt thì chỉ đổ mã vào ô chờ bấm. */
    val autoSubmitScanInput: Boolean = true,
)
