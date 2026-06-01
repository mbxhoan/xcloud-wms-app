package vn.delfi.xcloudwms.data.stock

/**
 * Map thông điệp lỗi thô (từ backend/PostgREST) sang câu tiếng Việt cho người dùng kho.
 * Bám theo `formatLookupErrorMessage` của scanner PWA để đồng nhất trải nghiệm.
 */
object LookupErrorMapper {
    fun toUserMessage(raw: String?): String {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return when {
            lower.contains("not found") || lower.contains("no rows") ->
                "Không tìm thấy mã này."

            lower.contains("permission denied") ||
                lower.contains("not authorized") ||
                lower.contains("not_authorized") ||
                lower.contains("forbidden") ||
                lower.contains("row-level security") ->
                "Bạn không có quyền tra cứu mã này."

            lower.contains("timeout") ->
                "Kết nối quá lâu. Vui lòng thử lại."

            lower.contains("unreachable") ||
                lower.contains("failed to connect") ||
                lower.contains("network") ->
                "Mất kết nối mạng. Vui lòng thử lại."

            else -> "Không tra cứu được. Vui lòng thử lại."
        }
    }

    /** True nếu thông điệp lỗi thuộc nhóm thiếu quyền. */
    fun isPermissionError(raw: String?): Boolean {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return lower.contains("permission denied") ||
            lower.contains("not authorized") ||
            lower.contains("not_authorized") ||
            lower.contains("forbidden") ||
            lower.contains("row-level security")
    }
}
