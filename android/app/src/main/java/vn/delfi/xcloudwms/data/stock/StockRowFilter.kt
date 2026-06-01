package vn.delfi.xcloudwms.data.stock

import vn.delfi.xcloudwms.domain.model.StockRow

/**
 * Lọc hiển thị dòng tồn theo kho hiện tại. Đây là view-filter phía client (không tính lại tồn) —
 * RPC `rpc_traceability_lookup` trả về toàn bộ kho user được phân quyền, app chỉ chọn dòng để hiện.
 */
object StockRowFilter {
    fun forWarehouse(
        rows: List<StockRow>,
        currentWarehouseId: String?,
        showAll: Boolean,
    ): List<StockRow> {
        if (showAll || currentWarehouseId.isNullOrBlank()) {
            return rows
        }
        val target = currentWarehouseId.trim()
        return rows.filter { it.warehouseId?.trim() == target }
    }
}
