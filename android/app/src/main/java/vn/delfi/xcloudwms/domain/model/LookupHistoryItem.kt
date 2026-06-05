package vn.delfi.xcloudwms.domain.model

/**
 * Một mục lịch sử tra cứu lưu cục bộ trên thiết bị (parity scanner PWA `xcloud_lookup_history`).
 * Chỉ là tiện ích thao tác nhanh; không phải nguồn sự thật, không gửi lên server.
 */
data class LookupHistoryItem(
    val code: String,
    val updatedAt: Long,
    val matchKind: String?,
    val productLabel: String?,
)
