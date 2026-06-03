package vn.delfi.xcloudwms.domain.model

/**
 * Buffer nhập liệu PA đang soạn dở (chưa thêm dòng / chưa submit), lưu local để không mất dữ liệu
 * khi mạng yếu hoặc app bị kill (xem `docs/06` mục 3). Đây CHỈ là phần form đang gõ; các dòng đã
 * thêm vẫn nằm ở server dưới trạng thái DRAFT. Không lưu token ở đây.
 */
data class PaDraftBuffer(
    val warehouseId: String,
    val sessionId: String? = null,
    val fromLocationId: String = "",
    val toLocationId: String = "",
    val selectedProductId: String? = null,
    val scannedCode: String = "",
    val qtyText: String = "1",
    val lineNotes: String = "",
    val sessionNotes: String = "",
    val updatedAt: Long = 0L,
) {
    /** Có gì đáng khôi phục không (tránh restore buffer rỗng đè lên form mới). */
    val isMeaningful: Boolean
        get() = fromLocationId.isNotBlank() ||
            toLocationId.isNotBlank() ||
            !selectedProductId.isNullOrBlank() ||
            scannedCode.isNotBlank() ||
            lineNotes.isNotBlank() ||
            sessionNotes.isNotBlank()
}
