package vn.delfi.xcloudwms.data.putaway

/**
 * Map mã lỗi nghiệp vụ PA (từ RPC/PostgREST) sang câu tiếng Việt cho nhân viên kho.
 * Bám theo `mapApiErrorMessage` của scanner PWA `PaPutawayClient.tsx` để đồng nhất trải nghiệm.
 */
object PutawayErrorMapper {
    private val INSUFFICIENT_REGEX = Regex("insufficient_available_qty:([^:]+):([^:]+)", RegexOption.IGNORE_CASE)
    private val CONFLICT_LINE_REGEX = Regex("pa_conflict_insufficient_stock_line:(\\d+)", RegexOption.IGNORE_CASE)

    fun toUserMessage(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return "Lỗi không xác định."
        val lower = normalized.lowercase()

        INSUFFICIENT_REGEX.find(normalized)?.let { match ->
            val requested = match.groupValues.getOrNull(1)?.trim().orEmpty().ifEmpty { "?" }
            val available = match.groupValues.getOrNull(2)?.trim().orEmpty().ifEmpty { "?" }
            return withCode(
                "Số lượng yêu cầu ($requested) vượt tồn khả dụng ($available) ở vị trí nguồn.",
                "insufficient_available_qty",
            )
        }

        CONFLICT_LINE_REGEX.find(normalized)?.let { match ->
            val lineId = match.groupValues.getOrNull(1).orEmpty()
            return withCode(
                "Dòng #$lineId không đủ tồn tại thời điểm hoàn tất. Vui lòng tải lại, chỉnh dữ liệu rồi thử lại.",
                "pa_conflict_insufficient_stock_line:$lineId",
            )
        }

        return when {
            isNetworkError(lower) ->
                "Không thể kết nối đến máy chủ sắp xếp. Vui lòng kiểm tra mạng rồi thử lại."

            lower.contains("from_location_invalid") -> withCode(
                "Vị trí nguồn không hợp lệ hoặc đã ngưng hoạt động trong kho hiện tại. Vui lòng chọn lại vị trí nguồn.",
                "from_location_invalid",
            )

            lower.contains("to_location_invalid") -> withCode(
                "Vị trí đích không hợp lệ hoặc đã ngưng hoạt động trong kho hiện tại. Vui lòng chọn lại vị trí đích.",
                "to_location_invalid",
            )

            lower.contains("from_to_location_must_be_different") -> withCode(
                "Vị trí nguồn và vị trí đích phải khác nhau.",
                "from_to_location_must_be_different",
            )

            lower.contains("pa_status_not_draft") -> withCode(
                "Phiếu không còn trạng thái nháp nên không thể lưu/hoàn tất.",
                "pa_status_not_draft",
            )

            lower.contains("pa_no_lines") -> withCode(
                "Phiếu chưa có dòng sắp xếp nào để hoàn tất.",
                "pa_no_lines",
            )

            lower.contains("pa_not_found") -> withCode(
                "Không tìm thấy phiên sắp xếp.",
                "pa_not_found",
            )

            lower.contains("pa_session_owner_required") -> withCode(
                "Bạn không phải người sở hữu phiên nháp này nên không thể chỉnh sửa.",
                "pa_session_owner_required",
            )

            lower.contains("not_authorized") -> withCode(
                "Bạn không có quyền thao tác sắp xếp cho kho hoặc phiếu đã chọn.",
                "not_authorized",
            )

            lower.contains("warehouse_not_found_or_not_active") -> withCode(
                "Kho không tồn tại hoặc đã ngưng hoạt động.",
                "warehouse_not_found_or_not_active",
            )

            lower.contains("product_not_found_or_inactive") -> withCode(
                "Sản phẩm không tồn tại hoặc đã ngưng hoạt động.",
                "product_not_found_or_inactive",
            )

            lower.contains("serial_not_in_from_location") -> withCode(
                "Serial không còn tồn khả dụng tại vị trí nguồn đã chọn.",
                "serial_not_in_from_location",
            )

            lower.contains("lot_not_found_or_invalid") -> withCode(
                "Lot không hợp lệ hoặc không còn ở trạng thái trong kho.",
                "lot_not_found_or_invalid",
            )

            lower.contains("pa_serial_duplicate_in_session") -> withCode(
                "Serial đã được thêm trong phiên sắp xếp này.",
                "pa_serial_duplicate_in_session",
            )

            lower.contains("pa_lot_duplicate_in_session") -> withCode(
                "Lot đã được thêm trong phiên sắp xếp này tại cùng vị trí nguồn.",
                "pa_lot_duplicate_in_session",
            )

            lower.contains("pa_item_already_moved_in_session") -> withCode(
                "Sản phẩm không tracking tại vị trí nguồn này đã được thêm trước đó.",
                "pa_item_already_moved_in_session",
            )

            isLikelyTechnicalError(normalized) ->
                "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu đầu vào và thử lại."

            else -> normalized
        }
    }

    /** True nếu thông điệp lỗi thuộc nhóm thiếu quyền (để phân nhánh HTTP 403). */
    fun isPermissionError(raw: String?): Boolean {
        val lower = raw?.trim()?.lowercase().orEmpty()
        return lower.contains("not_authorized") ||
            lower.contains("permission denied") ||
            lower.contains("forbidden") ||
            lower.contains("row-level security") ||
            lower.contains("pa_session_owner_required")
    }

    private fun isNetworkError(lower: String): Boolean =
        lower.contains("failed to fetch") ||
            lower.contains("network") ||
            lower.contains("unreachable") ||
            lower.contains("timeout")

    private fun isLikelyTechnicalError(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (Regex("^[A-Z0-9_:-]+$").matches(trimmed)) return true
        val upper = trimmed.uppercase()
        return upper.contains("SQLSTATE") || upper.contains("P0001")
    }

    private fun withCode(message: String, code: String): String = "$message ($code)"
}
