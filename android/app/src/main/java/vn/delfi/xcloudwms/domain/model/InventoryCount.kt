package vn.delfi.xcloudwms.domain.model

/**
 * Models cho luồng IC (Inventory Count / Kiểm kê) native, parity với scanner PWA
 * `count/IcCountClient.tsx` + route `/app/count`. DB/RPC/status contract giữ nguyên:
 * ic_headers/ic_lines/ic_details + rpc_ic_start_counting / rpc_ic_update_count /
 * rpc_ic_add_detail / rpc_check_serial_scan / rpc_check_lot_scan / rpc_ic_complete.
 *
 * An toàn: kết quả đếm KHÔNG tự điều chỉnh tồn. `rpc_ic_complete` với action
 * `COMPLETE_ONLY` chỉ đóng phiếu (không post ledger); cân bằng/duyệt (action `ADJUST`)
 * để webapp xử lý.
 */

/** Kiểu theo dõi hàng hoá của dòng kiểm kê (map từ products.tracking_type). */
enum class IcTrackingType {
    NONE,
    SERIAL,
    LOT,
    ;

    companion object {
        fun parse(raw: String?): IcTrackingType = when (raw?.trim()?.uppercase()) {
            "SERIAL", "SERIALIZED" -> SERIAL
            "LOT", "BATCH" -> LOT
            else -> NONE
        }
    }
}

/**
 * Trạng thái phiếu kiểm kê. Vòng đời: DRAFT → IN_PROGRESS (== COUNTING) → COMPLETED
 * (hoặc CANCELLED). Một số tenant tạo phiếu ở CREATED trước khi đếm.
 */
enum class IcStatus(val raw: String) {
    DRAFT("DRAFT"),
    CREATED("CREATED"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    OTHER("OTHER"),
    ;

    /** Phiếu còn đếm được (cho phép quét/ghi nhận). */
    val isScannable: Boolean
        get() = this == IN_PROGRESS

    /** Cần gọi rpc_ic_start_counting để chuyển sang IN_PROGRESS trước khi đếm. */
    val needsStartCounting: Boolean
        get() = this == DRAFT || this == CREATED

    /** Có thể kết thúc kiểm kê khi đang IN_PROGRESS. */
    val canFinish: Boolean
        get() = this == IN_PROGRESS

    /** Phiếu đã chốt, chỉ xem. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    val label: String
        get() = when (this) {
            DRAFT -> "Nháp"
            CREATED -> "Mới tạo"
            IN_PROGRESS -> "Đang kiểm kê"
            COMPLETED -> "Hoàn tất"
            CANCELLED -> "Đã huỷ"
            OTHER -> raw
        }

    companion object {
        fun parse(raw: String?): IcStatus = when (raw?.trim()?.uppercase()) {
            "DRAFT" -> DRAFT
            "CREATED" -> CREATED
            "IN_PROGRESS", "COUNTING" -> IN_PROGRESS
            "COMPLETED" -> COMPLETED
            "CANCELLED", "CANCELED" -> CANCELLED
            else -> OTHER
        }
    }
}

/** Header phiếu kiểm kê hiển thị ở list/detail. */
data class IcHeader(
    val id: String,
    val tenantId: String?,
    val warehouseId: String?,
    val code: String?,
    val status: IcStatus,
    val warehouseLabel: String?,
    val countMode: String?,
    val note: String?,
    val updatedAt: String? = null,
) {
    val displayCode: String
        get() = code?.takeIf { it.isNotBlank() } ?: "#$id"

    /** Đếm mù: ẩn số tồn hệ thống/khác biệt để tránh dẫn dắt người đếm. */
    val isBlind: Boolean
        get() = countMode?.trim()?.uppercase()?.let { it == "BLIND" || it == "BLIND_COUNT" } == true
}

/** Một dòng cần đếm trong phiếu (ic_lines + counted rollup từ ic_details.qty_counted). */
data class IcLine(
    val id: String,
    val productId: String?,
    val productCode: String,
    val productName: String?,
    val trackingType: IcTrackingType,
    /** Tồn hệ thống tại thời điểm chốt phiếu (qty_snapshot). */
    val expectedQty: Double,
    val countedQty: Double,
    val locationId: String? = null,
    val locationLabel: String? = null,
    val pickingStrategy: String? = null,
    val uomId: String? = null,
    val uomLabel: String? = null,
) {
    val productLabel: String
        get() = productName?.takeIf { it.isNotBlank() }?.let { "$productCode — $it" } ?: productCode

    /** Lệch giữa đếm thực và tồn hệ thống. */
    val diffQty: Double
        get() = countedQty - expectedQty

    val isCounted: Boolean
        get() = countedQty > 0
}

/** Kết quả một lần đếm (serial/lot/qty) để cập nhật optimistic + thông báo. */
data class IcCountSuccess(
    val lineId: String,
    /** Số lượng vừa đếm thêm (serial = 1). */
    val appliedQty: Double,
    val message: String,
)

/** Vị trí trong kho hiện tại (locations theo warehouse). */
data class IcLocation(
    val id: String,
    val code: String,
    val name: String?,
) {
    val label: String
        get() = name?.takeIf { it.isNotBlank() }?.let { "$code — $it" } ?: code
}
