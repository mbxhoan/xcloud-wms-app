package vn.delfi.xcloudwms.domain.model

/**
 * Models cho luồng PA (Put-away / Internal transfer) native, parity với scanner PWA
 * `PaPutawayClient.tsx`. Đây là model phía Android; DB/RPC/status contract giữ nguyên.
 */

/** Kiểu theo dõi hàng hoá. Map từ `tracking_type` của product/pa_details. */
enum class PaTrackingType {
    NONE,
    SERIAL,
    LOT,
    ;

    companion object {
        fun parse(raw: String?): PaTrackingType = when (raw?.trim()?.uppercase()) {
            "SERIAL" -> SERIAL
            "LOT" -> LOT
            else -> NONE
        }
    }
}

/** Trạng thái phiên PA. Backend dùng chuỗi; native chỉ phân nhánh các trạng thái cần thiết. */
enum class PaSessionStatus {
    DRAFT,
    COMPLETED,
    LOCKED,
    OTHER,
    ;

    val isEditable: Boolean
        get() = this == DRAFT

    companion object {
        fun parse(raw: String?): PaSessionStatus = when (raw?.trim()?.uppercase()) {
            // CREATED được coi như DRAFT (cùng nghĩa "đang soạn") như scanner PWA.
            "DRAFT", "CREATED" -> DRAFT
            "COMPLETED" -> COMPLETED
            "LOCKED" -> LOCKED
            else -> OTHER
        }
    }
}

data class PaLocation(
    val id: String,
    val code: String,
    val name: String? = null,
) {
    val label: String
        get() = name?.let { "$code — $it" } ?: code
}

data class PaProduct(
    val id: String,
    val code: String,
    val name: String? = null,
    val trackingType: PaTrackingType = PaTrackingType.NONE,
    val availableQty: Double? = null,
    val uomId: String? = null,
    val uomLabel: String? = null,
) {
    val label: String
        get() = name?.let { "$code — $it" } ?: code
}

data class PaSession(
    val id: String,
    val code: String?,
    val status: PaSessionStatus,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null,
)

data class PaDraftLine(
    val id: String,
    val fromLocationLabel: String,
    val toLocationLabel: String,
    val productLabel: String,
    val trackingType: PaTrackingType,
    val quantity: Double,
    val uomLabel: String? = null,
    val lotNumber: String? = null,
    val serialNumber: String? = null,
    val notes: String? = null,
    val fromLocationId: String? = null,
    val lotId: String? = null,
    val serialId: String? = null,
    val productId: String? = null,
)

/** Kết quả phân giải mã quét (serial/lot) tại vị trí nguồn. */
sealed interface PaResolvedCode {
    data class Ok(
        val trackingType: PaTrackingType,
        val productId: String,
        val serialId: String? = null,
        val lotId: String? = null,
    ) : PaResolvedCode

    data class Fail(
        val reason: Reason,
        val message: String,
    ) : PaResolvedCode

    enum class Reason { NOT_FOUND, NOT_IN_SOURCE, QUERY_ERROR }
}

/** Kết quả kiểm tra tồn khả dụng tức thời tại vị trí nguồn. */
data class PaLiveStock(
    val availableQty: Double?,
)
