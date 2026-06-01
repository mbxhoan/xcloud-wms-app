package vn.delfi.xcloudwms.data.gi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsIssueErrorMapperTest {

    @Test
    fun `overpick maps to vietnamese`() {
        assertEquals(
            "Vượt số lượng cần pick của phiếu.",
            GoodsIssueErrorMapper.toUserMessage("GI_OVERPICK_DETECTED"),
        )
    }

    @Test
    fun `serial reserved in other gi maps to vietnamese`() {
        assertEquals(
            "Serial đang được giữ bởi phiếu xuất khác.",
            GoodsIssueErrorMapper.toUserMessage("serial_reserved_in_other_gi"),
        )
    }

    @Test
    fun `lot not reserved maps to vietnamese`() {
        assertEquals(
            "Lot chưa được reserve cho phiếu xuất hiện tại.",
            GoodsIssueErrorMapper.toUserMessage("lot_not_reserved_in_this_gi"),
        )
    }

    @Test
    fun `status conflict on submit is detected and rephrased`() {
        val raw = "Cannot submit. Status must be PICKING, current: PICKED"
        assertTrue(GoodsIssueErrorMapper.isStatusConflict(raw))
        assertTrue(GoodsIssueErrorMapper.toUserMessage(raw).contains("PICKED"))
        assertTrue(GoodsIssueErrorMapper.toUserMessage(raw).contains("tải lại"))
    }

    @Test
    fun `status conflict on start is detected`() {
        val raw = "Cannot start. Status must be CREATED, current: COMPLETED"
        assertTrue(GoodsIssueErrorMapper.isStatusConflict(raw))
    }

    @Test
    fun `non status error is not a conflict`() {
        assertFalse(GoodsIssueErrorMapper.isStatusConflict("serial_not_in_stock"))
    }

    @Test
    fun `network error maps to connection message`() {
        assertEquals(
            "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.",
            GoodsIssueErrorMapper.toUserMessage("TypeError: Failed to fetch"),
        )
    }

    @Test
    fun `permission error is detected`() {
        assertTrue(GoodsIssueErrorMapper.isPermissionError("not_authorized"))
        assertTrue(GoodsIssueErrorMapper.isPermissionError("ASSIGNED_SCANNER_MISMATCH"))
        assertFalse(GoodsIssueErrorMapper.isPermissionError("lot_not_found"))
    }

    @Test
    fun `unknown technical code falls back to generic business error`() {
        assertEquals(
            "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu và thử lại.",
            GoodsIssueErrorMapper.toUserMessage("SOME_UNKNOWN_CODE"),
        )
    }

    @Test
    fun `blank message yields default`() {
        assertEquals("Lỗi không xác định.", GoodsIssueErrorMapper.toUserMessage("  "))
    }
}
