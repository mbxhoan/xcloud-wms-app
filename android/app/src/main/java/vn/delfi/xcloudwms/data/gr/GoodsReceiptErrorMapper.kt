package vn.delfi.xcloudwms.data.gr

/**
 * Map mã lỗi nghiệp vụ GR (từ RPC/PostgREST) sang câu tiếng Việt cho nhân viên kho.
 * Bám theo `mapGrReasonMessage` của scanner PWA `GrReceiveClient.tsx` để đồng nhất trải
 * nghiệm. Lỗi lệch trạng thái được tách riêng để màn nhận hàng refresh phiếu.
 */
object GoodsReceiptErrorMapper {
    private val STATUS_NOT_RECEIVING_REGEX =
        Regex("STATUS MUST BE RECEIVING,\\s*CURRENT:\\s*([A-Z_]+)", RegexOption.IGNORE_CASE)
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

        STATUS_NOT_RECEIVING_REGEX.find(normalized)?.let { match ->
            val current = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return "Phiếu không còn ở trạng thái đang nhận (hiện tại: $current). Vui lòng tải lại phiếu."
        }
        STATUS_START_REGEX.find(normalized)?.let { match ->
            val current = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return "Không thể bắt đầu nhận hàng, phiếu đang ở trạng thái $current. Vui lòng tải lại phiếu."
        }

        return when {
            lower.contains("gr_not_receiving") ->
                "Phiếu không ở trạng thái đang nhận. Vui lòng tải lại phiếu."

            lower.contains("gr_line_quantity_invalid") -> "Số lượng dòng nhận không hợp lệ."
            lower.contains("gr_detail_tracking_mismatch") ->
                "Mã quét không khớp kiểu theo dõi của dòng (NONE/LOT/SERIAL)."
            lower.contains("gr_receipt_no_details") ->
                "Chưa ghi nhận dòng nhận nào. Hãy nhận ít nhất một dòng trước khi chốt."

            lower.contains("gr_line_not_found") -> "Không tìm thấy dòng nhập."
            lower.contains("gr_header_not_found") || lower.contains("gr_not_found") ->
                "Không tìm thấy phiếu nhập."

            lower.contains("assigned_scanner_mismatch") ->
                "Phiếu đã được phân công cho nhân viên khác."

            lower.contains("product_mismatch") ->
                "Mã quét không thuộc sản phẩm của dòng này."

            // SERIAL
            lower.contains("serial_already_received") ||
                lower.contains("serial_already_in_this_header") ->
                "Serial này đã được nhận trong phiếu hiện tại."
            lower.contains("serial_already_in_stock") -> "Serial này còn đang trong tồn kho."
            lower.contains("serial_already_receiving_other_gr") ||
                lower.contains("serial_reserved_in_other_gr") ->
                "Serial đang được nhận ở phiếu khác."
            lower.contains("serial_status_not_outwarehouse") ->
                "Serial chưa ở trạng thái xuất khỏi kho (OUTWAREHOUSE)."
            lower.contains("serial_in_other_warehouse") -> "Serial đang ở kho khác."
            lower.contains("product_not_serial_tracking") -> "Sản phẩm không dùng tracking SERIAL."
            lower.contains("serial_not_found") -> "Không tìm thấy serial."

            // LOT
            lower.contains("lot_already_in_stock") -> "Lô này còn đang trong tồn kho."
            lower.contains("lot_in_other_warehouse") -> "Lô đang thuộc kho khác."
            lower.contains("lot_status_not_outwarehouse") ->
                "Lô chưa ở trạng thái xuất khỏi kho (OUTWAREHOUSE)."
            lower.contains("product_not_lot_tracking") -> "Sản phẩm không dùng tracking LOT."
            lower.contains("lot_not_found") -> "Không tìm thấy mã lô."

            lower.contains("external_tracking_scan_disabled") || lower.contains("allow_external") ->
                "Tenant không cho tạo lô/serial mới khi nhận. Vui lòng kiểm tra cấu hình."

            lower.contains("expiry") && lower.contains("manufacture") ->
                "Hạn sử dụng phải lớn hơn hoặc bằng ngày sản xuất."
            lower.contains("expiry_required") || lower.contains("require_expiry") ->
                "Dòng này bắt buộc nhập hạn sử dụng (HSD)."
            lower.contains("manufacture_required") || lower.contains("require_manufacture") ->
                "Dòng này bắt buộc nhập ngày sản xuất (NSX)."

            lower.contains("wrong_warehouse") -> "Mã không thuộc kho của phiếu nhập hiện tại."

            lower.contains("not_authorized") || lower.contains("permission denied") ||
                lower.contains("forbidden") || lower.contains("row-level security") ->
                "Bạn không có quyền thao tác phiếu/kho này."

            lower.contains("invalid_input") -> "Dữ liệu quét không hợp lệ."

            upper.contains("SQLSTATE") || upper.contains("P0001") || Regex("^[A-Z0-9_:-]+$").matches(normalized) ->
                "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu và thử lại."

            else -> normalized
        }
    }

    /** True nếu lỗi do lệch trạng thái phiếu → màn nhận hàng nên refresh tài liệu. */
    fun isStatusConflict(raw: String?): Boolean {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return false
        return STATUS_NOT_RECEIVING_REGEX.containsMatchIn(s) ||
            STATUS_START_REGEX.containsMatchIn(s) ||
            s.lowercase().let { it.contains("gr_not_receiving") || it.contains("status must be") }
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
}
