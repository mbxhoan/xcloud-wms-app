package vn.delfi.xcloudwms.domain.model

/**
 * Models cho luồng GI (Goods Issue / Picking) native, parity với scanner PWA
 * `GiPickClient.tsx` + route `/app/outbound`. DB/RPC/status contract giữ nguyên:
 * gi_headers/gi_lines/gi_details + rpc_gi_start_picking/check_serial/check_lot/
 * bind_serial_to_summary_line/submit/complete.
 */

/** Kiểu theo dõi hàng hoá của dòng xuất (map từ products.tracking_type). */
enum class GiTrackingType {
    NONE,
    SERIAL,
    LOT,
    ;

    companion object {
        fun parse(raw: String?): GiTrackingType = when (raw?.trim()?.uppercase()) {
            "SERIAL", "SERIALIZED" -> SERIAL
            "LOT", "BATCH" -> LOT
            else -> NONE
        }
    }
}

/**
 * Trạng thái phiếu xuất. Backend dùng chuỗi; native chỉ phân nhánh các trạng thái
 * cần cho picking. Vòng đời: DRAFT → CREATED → PICKING → PICKED → COMPLETED (hoặc CANCELLED).
 */
enum class GiStatus(val raw: String) {
    DRAFT("DRAFT"),
    CREATED("CREATED"),
    PICKING("PICKING"),
    PICKED("PICKED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    OTHER("OTHER"),
    ;

    /** Phiếu còn thao tác picking được (cho phép quét/cập nhật). */
    val isScannable: Boolean
        get() = this == CREATED || this == PICKING

    /** Cần gọi rpc_gi_start_picking để chuyển sang PICKING trước khi quét. */
    val needsStartPicking: Boolean
        get() = this == CREATED

    /** Có thể submit (chốt picking) khi đang PICKING. */
    val canSubmit: Boolean
        get() = this == PICKING

    /** Có thể hoàn tất xuất kho (ship phần đã pick) khi PICKING/PICKED. */
    val canComplete: Boolean
        get() = this == PICKING || this == PICKED

    /** Phiếu đã chốt, chỉ xem. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    val label: String
        get() = when (this) {
            DRAFT -> "Nháp"
            CREATED -> "Mới tạo"
            PICKING -> "Đang xuất"
            PICKED -> "Đã pick"
            COMPLETED -> "Hoàn tất"
            CANCELLED -> "Đã huỷ"
            OTHER -> raw
        }

    companion object {
        fun parse(raw: String?): GiStatus = when (raw?.trim()?.uppercase()) {
            "DRAFT" -> DRAFT
            "CREATED" -> CREATED
            "PICKING", "IN_PROGRESS" -> PICKING
            "PICKED" -> PICKED
            "COMPLETED" -> COMPLETED
            "CANCELLED", "CANCELED" -> CANCELLED
            else -> OTHER
        }
    }
}

/** Header phiếu xuất hiển thị ở list/detail. */
data class GiHeader(
    val id: String,
    val tenantId: String?,
    val warehouseId: String?,
    val code: String?,
    val status: GiStatus,
    val warehouseLabel: String?,
    val partnerLabel: String?,
    val referenceType: String?,
    val referenceNumber: String?,
    val note: String?,
    val updatedAt: String? = null,
) {
    val displayCode: String
        get() = code?.takeIf { it.isNotBlank() } ?: "#$id"
}

/** Một dòng cần pick trong phiếu (gi_lines + tồn picked rollup từ gi_details). */
data class GiLine(
    val id: String,
    val productId: String?,
    val productCode: String,
    val productName: String?,
    val trackingType: GiTrackingType,
    val plannedQty: Double,
    val pickedQty: Double,
    val uomId: String? = null,
    val uomLabel: String? = null,
) {
    val productLabel: String
        get() = productName?.takeIf { it.isNotBlank() }?.let { "$productCode — $it" } ?: productCode

    val remainingQty: Double
        get() = (plannedQty - pickedQty).coerceAtLeast(0.0)

    val isComplete: Boolean
        get() = pickedQty + 1e-9 >= plannedQty && plannedQty > 0
}

/** Kết quả một lần pick (serial/lot/qty) để cập nhật optimistic + thông báo. */
data class GiPickSuccess(
    val lineId: String,
    /** Số lượng vừa pick thêm (serial = 1). */
    val appliedQty: Double,
    val message: String,
)
