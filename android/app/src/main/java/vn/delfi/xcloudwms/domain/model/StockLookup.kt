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
    val activeLpns: List<StockActiveLpn>,
    val lpnContents: List<StockLpnContent>,
    val events: List<StockEvent>,
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
    val referenceCode: String?,
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
    val eventCount: Int = 0,
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

/** LPN active đang chứa mã tra cứu (khi tra theo PRODUCT/LOT/SERIAL). */
data class StockActiveLpn(
    val lpnCode: String,
    val status: String?,
    val isSealed: Boolean,
    val warehouseCode: String?,
    val warehouseName: String?,
    val locationCode: String?,
    val locationName: String?,
    val packedQtyBase: Double,
    val contentsCount: Int,
)

/** Dòng nội dung bên trong LPN (khi tra theo LPN). */
data class StockLpnContent(
    val productCode: String?,
    val productName: String?,
    val trackingType: String?,
    val lotNumber: String?,
    val serialNumber: String?,
    val qtyBase: Double,
    val uomCode: String?,
    val uomName: String?,
)

/** Một sự kiện trên dòng thời gian luân chuyển. */
data class StockEvent(
    val id: String,
    val source: String,
    val time: String?,
    val title: String,
    val subtitle: String?,
    val quantityDelta: Double?,
    val quantityText: String?,
    val referenceCode: String?,
    val actorName: String?,
    val partnerName: String?,
    val lpnCode: String?,
    val locationCode: String?,
    val fromLocationCode: String?,
    val toLocationCode: String?,
    val reasonNote: String?,
    val notes: String?,
)
