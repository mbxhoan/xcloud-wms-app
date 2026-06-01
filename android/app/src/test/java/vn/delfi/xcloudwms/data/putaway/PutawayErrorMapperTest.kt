package vn.delfi.xcloudwms.data.putaway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PutawayErrorMapperTest {

    @Test
    fun `insufficient available qty embeds requested and available`() {
        val message = PutawayErrorMapper.toUserMessage("insufficient_available_qty:5:3")
        assertTrue(message.contains("(5)"))
        assertTrue(message.contains("(3)"))
        assertTrue(message.endsWith("(insufficient_available_qty)"))
    }

    @Test
    fun `conflict line embeds line number and code`() {
        val message = PutawayErrorMapper.toUserMessage("pa_conflict_insufficient_stock_line:42")
        assertTrue(message.contains("#42"))
        assertTrue(message.endsWith("(pa_conflict_insufficient_stock_line:42)"))
    }

    @Test
    fun `known business codes map to vietnamese with code suffix`() {
        assertEquals(
            "Vị trí nguồn và vị trí đích phải khác nhau. (from_to_location_must_be_different)",
            PutawayErrorMapper.toUserMessage("from_to_location_must_be_different"),
        )
        assertEquals(
            "Phiếu không còn trạng thái nháp nên không thể lưu/hoàn tất. (pa_status_not_draft)",
            PutawayErrorMapper.toUserMessage("pa_status_not_draft"),
        )
        assertEquals(
            "Serial đã được thêm trong phiên sắp xếp này. (pa_serial_duplicate_in_session)",
            PutawayErrorMapper.toUserMessage("pa_serial_duplicate_in_session"),
        )
    }

    @Test
    fun `network error maps to connection message`() {
        assertEquals(
            "Không thể kết nối đến máy chủ sắp xếp. Vui lòng kiểm tra mạng rồi thử lại.",
            PutawayErrorMapper.toUserMessage("Failed to fetch"),
        )
    }

    @Test
    fun `opaque technical error maps to generic business message`() {
        assertEquals(
            "Lỗi nghiệp vụ từ máy chủ. Vui lòng kiểm tra dữ liệu đầu vào và thử lại.",
            PutawayErrorMapper.toUserMessage("P0001_SOMETHING_RAW"),
        )
    }

    @Test
    fun `blank maps to unknown`() {
        assertEquals("Lỗi không xác định.", PutawayErrorMapper.toUserMessage(null))
        assertEquals("Lỗi không xác định.", PutawayErrorMapper.toUserMessage("   "))
    }

    @Test
    fun `human readable message passes through`() {
        assertEquals("Một câu lỗi thường.", PutawayErrorMapper.toUserMessage("Một câu lỗi thường."))
    }

    @Test
    fun `isPermissionError detects permission group only`() {
        assertTrue(PutawayErrorMapper.isPermissionError("not_authorized"))
        assertTrue(PutawayErrorMapper.isPermissionError("permission denied for table"))
        assertTrue(PutawayErrorMapper.isPermissionError("pa_session_owner_required"))
        assertFalse(PutawayErrorMapper.isPermissionError("pa_no_lines"))
        assertFalse(PutawayErrorMapper.isPermissionError(null))
    }
}
