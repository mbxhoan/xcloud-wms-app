package vn.delfi.xcloudwms.data.ic

/**
 * Map mã lỗi nghiệp vụ IC (từ RPC/PostgREST) sang câu tiếng Việt cho nhân viên kho.
 * Bám theo `mapIcErrorCodeMessage` của scanner PWA `IcCountClient.tsx`. Lỗi lệch trạng
 * thái được tách riêng để màn kiểm kê refresh phiếu.
 */
object InventoryCountErrorMapper {
    fun toUserMessage(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return "Lỗi không xác định."
        val lower = normalized.lowercase()
        val upper = normalized.uppercase()

        if (isNetworkError(lower)) {
            return "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại."
        }

        return when {
            lower.contains("uniq_ic_details_header_serial") ||
                lower.contains("serial_already_scanned_in_ic") ->
                "Serial đã được quét trong phiếu kiểm kê này hoặc một phiếu khác đang hoạt động."

            lower.contains("ic_line_has_lpn_scan") ->
                "Dòng này đang đếm theo LPN. Hãy gỡ quét LPN trước khi đếm thủ công."

            lower.contains("ic_complete_note_required") ->
                "Cần ghi chú khi kết thúc phiếu kiểm kê."
            lower.contains("ic_complete_action_invalid") ->
                "Hành động kết thúc không hợp lệ."
            lower.contains("ic_complete_not_in_progress") ->
                "Phiếu không ở trạng thái đang kiểm kê để kết thúc. Vui lòng tải lại phiếu."

            lower.contains("ic_header_not_found") || lower.contains("ic not found") ->
                "Không tìm thấy phiếu kiểm kê."
            lower.contains("ic_line_not_found") -> "Không tìm thấy dòng kiểm kê."

            lower.contains("product_mismatch") ->
                "Mã quét không thuộc sản phẩm của dòng này."
            lower.contains("lot_product_mismatch") ->
                "Mã lô không thuộc sản phẩm đang kiểm kê."

            lower.contains("serial_not_found") -> "Không tìm thấy serial."
            lower.contains("serial_in_other_warehouse") -> "Serial đang ở kho khác."
            lower.contains("lot_not_found") -> "Không tìm thấy mã lô."

            lower.contains("not_authorized") || lower.contains("permission denied") ||
                lower.contains("forbidden") || lower.contains("row-level security") ->
                "Bạn không có quyền thao tác phiếu/kho này."

            lower.contains("invalid_input") -> "Dữ liệu quét không hợp lệ."

            upper.contains("SQLSTATE") || upper.contains("P0001") || Regex("^[A-Z0-9_:-]+$").matches(normalized) ->
                "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu và thử lại."

            else -> normalized
        }
    }

    /** True nếu lỗi do lệch trạng thái phiếu → màn kiểm kê nên refresh tài liệu. */
    fun isStatusConflict(raw: String?): Boolean {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return false
        return s.contains("ic_complete_not_in_progress") ||
            s.contains("not in progress") ||
            s.contains("status must be") ||
            s.contains("already started or completed")
    }

    fun isPermissionError(raw: String?): Boolean {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return lower.contains("not_authorized") ||
            lower.contains("permission denied") ||
            lower.contains("forbidden") ||
            lower.contains("row-level security") ||
            lower.contains("assigned_scanner_mismatch")
    }

    /** True nếu RPC chưa hỗ trợ param (signature mismatch / hàm không tồn tại) → cần fallback. */
    fun isMissingRpcFunction(raw: String?): Boolean {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return lower.contains("could not find") ||
            lower.contains("does not exist") ||
            lower.contains("pgrst202") ||
            lower.contains("no function matches")
    }

    private fun isNetworkError(lower: String): Boolean =
        lower.contains("failed to fetch") ||
            lower.contains("networkerror") ||
            lower.contains("network request failed") ||
            lower.contains("unreachable") ||
            lower.contains("timeout")
}
