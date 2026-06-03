package vn.delfi.xcloudwms.domain.model

/**
 * Models cho luồng GR (Goods Receipt / Nhận hàng) native, parity với scanner PWA
 * `inbound/GrReceiveClient.tsx` + route `/app/inbound`. DB/RPC/status contract giữ nguyên:
 * gr_headers/gr_lines/gr_details + rpc_gr_start_receiving / rpc_gr_resolve_lot_scan /
 * rpc_gr_resolve_serial_scan / rpc_gr_submit_receive / rpc_gr_complete. Client không tự
 * tăng tồn — backend là nguồn sự thật.
 */

/** Kiểu theo dõi hàng hoá của dòng nhập (map từ products.tracking_type). */
enum class GrTrackingType {
    NONE,
    SERIAL,
    LOT,
    ;

    companion object {
        fun parse(raw: String?): GrTrackingType = when (raw?.trim()?.uppercase()) {
            "SERIAL", "SERIALIZED" -> SERIAL
            "LOT", "BATCH" -> LOT
            else -> NONE
        }
    }
}

/**
 * Trạng thái phiếu nhập. Backend dùng chuỗi; native chỉ phân nhánh các trạng thái cần
 * cho receiving. Vòng đời: DRAFT → CREATED → RECEIVING → RECEIVED → COMPLETED (hoặc CANCELLED).
 */
enum class GrStatus(val raw: String) {
    DRAFT("DRAFT"),
    CREATED("CREATED"),
    RECEIVING("RECEIVING"),
    RECEIVED("RECEIVED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    OTHER("OTHER"),
    ;

    /** Phiếu còn nhận hàng được (cho phép quét/ghi nhận). */
    val isScannable: Boolean
        get() = this == CREATED || this == RECEIVING

    /** Cần gọi rpc_gr_start_receiving để chuyển sang RECEIVING trước khi quét. */
    val needsStartReceiving: Boolean
        get() = this == CREATED

    /** Có thể chốt nhận (submit) khi đang RECEIVING. */
    val canSubmit: Boolean
        get() = this == RECEIVING

    /** Có thể hoàn tất nhập kho khi RECEIVING/RECEIVED. */
    val canComplete: Boolean
        get() = this == RECEIVING || this == RECEIVED

    /** Phiếu đã chốt, chỉ xem. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    val label: String
        get() = when (this) {
            DRAFT -> "Nháp"
            CREATED -> "Mới tạo"
            RECEIVING -> "Đang nhận"
            RECEIVED -> "Đã nhận"
            COMPLETED -> "Hoàn tất"
            CANCELLED -> "Đã huỷ"
            OTHER -> raw
        }

    companion object {
        fun parse(raw: String?): GrStatus = when (raw?.trim()?.uppercase()) {
            "DRAFT" -> DRAFT
            "CREATED" -> CREATED
            "RECEIVING", "IN_PROGRESS" -> RECEIVING
            "RECEIVED" -> RECEIVED
            "COMPLETED" -> COMPLETED
            "CANCELLED", "CANCELED" -> CANCELLED
            else -> OTHER
        }
    }
}

/** Header phiếu nhập hiển thị ở list/detail. */
data class GrHeader(
    val id: String,
    val tenantId: String?,
    val warehouseId: String?,
    val code: String?,
    val status: GrStatus,
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

/** Một dòng cần nhận trong phiếu (gr_lines + received rollup từ gr_details.quantity). */
data class GrLine(
    val id: String,
    val productId: String?,
    val productCode: String,
    val productName: String?,
    val trackingType: GrTrackingType,
    val expectedQty: Double,
    val receivedQty: Double,
    val requireMfgDate: Boolean = false,
    val requireExpiryDate: Boolean = false,
    val pickingStrategy: String? = null,
    val uomId: String? = null,
    val uomLabel: String? = null,
) {
    val productLabel: String
        get() = productName?.takeIf { it.isNotBlank() }?.let { "$productCode — $it" } ?: productCode

    val remainingQty: Double
        get() = (expectedQty - receivedQty).coerceAtLeast(0.0)

    val isComplete: Boolean
        get() = receivedQty + 1e-9 >= expectedQty && expectedQty > 0

    /** FEFO → luôn cần HSD để cấp phát đúng hạn dù product không bắt buộc. */
    val isFefo: Boolean
        get() = pickingStrategy?.trim()?.uppercase() == "FEFO"

    /** Có cần nhập NSX cho dòng này không. */
    val needsMfgDate: Boolean
        get() = trackingType != GrTrackingType.NONE && requireMfgDate

    /** Có cần nhập HSD cho dòng này không (require_expiry_date hoặc FEFO). */
    val needsExpiryDate: Boolean
        get() = trackingType != GrTrackingType.NONE && (requireExpiryDate || isFefo)
}

/** Kết quả một lần nhận (serial/lot/qty) để cập nhật optimistic + thông báo. */
data class GrReceiveSuccess(
    val lineId: String,
    /** Số lượng vừa nhận thêm (serial = 1). */
    val appliedQty: Double,
    val message: String,
)

/** Vị trí nhập trong kho hiện tại (locations theo warehouse). */
data class GrLocation(
    val id: String,
    val code: String,
    val name: String?,
) {
    val label: String
        get() = name?.takeIf { it.isNotBlank() }?.let { "$code — $it" } ?: code
}
