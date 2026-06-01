package vn.delfi.xcloudwms.data.gi

/**
 * Map mã lỗi nghiệp vụ GI (từ RPC/PostgREST) sang câu tiếng Việt cho nhân viên kho.
 * Bám theo `mapGiReasonMessage` của scanner PWA `GiPickClient.tsx`/`PdaGiScanClient.tsx`
 * để đồng nhất trải nghiệm. Lỗi lệch trạng thái được tách riêng để màn pick refresh phiếu.
 */
object GoodsIssueErrorMapper {
    private val STATUS_SUBMIT_REGEX =
        Regex("STATUS MUST BE PICKING,\\s*CURRENT:\\s*([A-Z_]+)", RegexOption.IGNORE_CASE)
    private val STATUS_COMPLETE_REGEX =
        Regex("STATUS MUST BE PICKING OR PICKED,\\s*CURRENT:\\s*([A-Z_]+)", RegexOption.IGNORE_CASE)
    private val STATUS_START_REGEX =
        Regex("STATUS MUST BE CREATED,\\s*CURRENT:\\s*([A-Z_]+)", RegexOption.IGNORE_CASE)

    fun toUserMessage(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return "Lỗi không xác định."
        val lower = normalized.lowercase()
        val upper = normalized.uppercase()

        if (isNetworkError(lower)) {
            return "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại."
        }

        (STATUS_SUBMIT_REGEX.find(normalized) ?: STATUS_COMPLETE_REGEX.find(normalized))?.let { match ->
            val current = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return "Phiếu không còn ở trạng thái picking (hiện tại: $current). Vui lòng tải lại phiếu."
        }
        STATUS_START_REGEX.find(normalized)?.let { match ->
            val current = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return "Không thể bắt đầu picking, phiếu đang ở trạng thái $current. Vui lòng tải lại phiếu."
        }

        return when {
            upper.contains("GI_OVERPICK_DETECTED") || lower.contains("gi_overpick_detected") ->
                "Vượt số lượng cần pick của phiếu."

            upper.contains("GI_NOT_PICKABLE") || lower.contains("gi_not_pickable") ->
                "Phiếu xuất chưa sẵn sàng để picking."

            upper.contains("GI_LINE_FULLY_PICKED") || lower.contains("gi_line_fully_picked") ->
                "Dòng này đã được pick đủ số lượng."

            lower.contains("gi_line_not_found") -> "Không tìm thấy dòng xuất."
            lower.contains("gi_header_not_found") || lower.contains("gi_not_found") ->
                "Không tìm thấy phiếu xuất."

            lower.contains("assigned_scanner_mismatch") ->
                "Phiếu đã được phân công cho nhân viên khác."

            lower.contains("product_mismatch") ->
                "Mã quét không thuộc sản phẩm của dòng này."

            lower.contains("serial_status_not_inwarehouse") ->
                "Serial không ở trạng thái trong kho."

            lower.contains("serial_in_other_warehouse") -> "Serial đang ở kho khác."
            lower.contains("serial_not_in_stock") -> "Serial không còn trong tồn kho khả dụng."
            lower.contains("serial_reserved_in_other_gi") ->
                "Serial đang được giữ bởi phiếu xuất khác."
            lower.contains("serial_not_reserved_in_this_gi") ->
                "Serial chưa được reserve cho phiếu xuất hiện tại."
            lower.contains("serial_already_picked_in_this_gi") ||
                lower.contains("serial_already_in_this_header") ->
                "Serial đã được đồng bộ trong phiếu hiện tại."
            lower.contains("serial_not_available") -> "Serial hiện không khả dụng để pick."
            lower.contains("serial_not_found") -> "Không tìm thấy serial."

            lower.contains("lot_status_not_inwarehouse") -> "Lot không ở trạng thái trong kho."
            lower.contains("lot_in_other_warehouse") -> "Lot đang thuộc kho khác."
            lower.contains("lot_not_in_stock") -> "Lot không còn trong tồn kho khả dụng."
            lower.contains("lot_reserved_in_other_gi") ->
                "Lot đang được giữ bởi phiếu xuất khác."
            lower.contains("lot_not_reserved_in_this_gi") ->
                "Lot chưa được reserve cho phiếu xuất hiện tại."
            lower.contains("lot_already_fully_picked_in_this_gi") ||
                lower.contains("lot_already_in_this_header") ->
                "Lot này đã được pick đủ trong phiếu hiện tại."
            lower.contains("lot_not_available") -> "Lot hiện không khả dụng để pick."
            lower.contains("product_not_lot_tracking") -> "Sản phẩm không dùng tracking LOT."
            lower.contains("lot_not_found") -> "Không tìm thấy mã lot."

            lower.contains("gi_reserved_qty_exceeds_line_needed") ->
                "Số lượng vượt quá nhu cầu còn lại của dòng xuất."

            lower.contains("wrong_warehouse") -> "Mã không thuộc kho của phiếu xuất hiện tại."
            lower.contains("out_of_stock") -> "Hết tồn khả dụng cho mã này."

            lower.contains("not_authorized") || lower.contains("permission denied") ||
                lower.contains("forbidden") || lower.contains("row-level security") ->
                "Bạn không có quyền thao tác phiếu/kho này."

            lower.contains("invalid_input") -> "Dữ liệu quét không hợp lệ."

            isLikelyTechnicalError(normalized) ->
                "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu và thử lại."

            else -> normalized
        }
    }

    /** True nếu lỗi do lệch trạng thái phiếu → màn pick nên refresh tài liệu. */
    fun isStatusConflict(raw: String?): Boolean {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return false
        return STATUS_SUBMIT_REGEX.containsMatchIn(s) ||
            STATUS_COMPLETE_REGEX.containsMatchIn(s) ||
            STATUS_START_REGEX.containsMatchIn(s) ||
            s.lowercase().let { it.contains("gi_not_pickable") || it.contains("status must be") }
    }

    fun isPermissionError(raw: String?): Boolean {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return lower.contains("not_authorized") ||
            lower.contains("permission denied") ||
            lower.contains("forbidden") ||
            lower.contains("row-level security") ||
            lower.contains("assigned_scanner_mismatch")
    }

    private fun isNetworkError(lower: String): Boolean =
        lower.contains("failed to fetch") ||
            lower.contains("networkerror") ||
            lower.contains("network request failed") ||
            lower.contains("unreachable") ||
            lower.contains("timeout")

    private fun isLikelyTechnicalError(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (Regex("^[A-Z0-9_:-]+$").matches(trimmed)) return true
        val upper = trimmed.uppercase()
        return upper.contains("SQLSTATE") || upper.contains("P0001")
    }
}
