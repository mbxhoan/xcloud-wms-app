package vn.delfi.xcloudwms.domain.model

/**
 * Kết quả tra cứu tồn kho (read-only) từ `rpc_traceability_lookup`.
 * Model nội bộ Android; không thuộc DB/RPC/API contract dùng chung.
 */
data class StockLookupResult(
    val query: String,
    val match: StockMatch?,
    val summary: StockSummary,
    val rows: List<StockRow>,
    val warnings: List<String>,
) {
    /** Không tìm thấy mã: backend trả match = null. */
    val isEmpty: Boolean get() = match == null
}

data class StockMatch(
    val kind: String,
    val code: String?,
    val label: String?,
    val productCode: String?,
    val productName: String?,
    val trackingType: String?,
    val uomCode: String?,
    val uomName: String?,
    val status: String?,
    val lotNumber: String?,
    val serialNumber: String?,
    val lpnCode: String?,
) {
    val productLabel: String get() = productName ?: productCode ?: label ?: code ?: "—"
}

data class StockSummary(
    val totalOnHand: Double = 0.0,
    val totalReserved: Double = 0.0,
    val totalLocked: Double = 0.0,
    val totalAvailable: Double = 0.0,
    val warehouseCount: Int = 0,
    val locationCount: Int = 0,
    val activeLpnCount: Int = 0,
)

data class StockRow(
    val warehouseId: String?,
    val warehouseCode: String?,
    val warehouseName: String?,
    val locationCode: String?,
    val locationName: String?,
    val trackingValue: String?,
    val quantityOnHand: Double,
    val quantityReserved: Double,
    val quantityLocked: Double,
    val quantityAvailable: Double,
    val inboundDate: String?,
    val manufactureDate: String?,
    val expiryDate: String?,
) {
    val warehouseLabel: String get() = warehouseName ?: warehouseCode ?: warehouseId ?: "—"
    val locationLabel: String get() = locationName ?: locationCode ?: "—"
}
