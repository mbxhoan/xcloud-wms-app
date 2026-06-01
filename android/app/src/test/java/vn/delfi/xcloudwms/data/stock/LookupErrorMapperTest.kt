package vn.delfi.xcloudwms.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupErrorMapperTest {

    @Test
    fun `not found maps to vietnamese not found`() {
        assertEquals("Không tìm thấy mã này.", LookupErrorMapper.toUserMessage("No rows returned"))
        assertEquals("Không tìm thấy mã này.", LookupErrorMapper.toUserMessage("not found"))
    }

    @Test
    fun `permission errors map to permission message`() {
        assertEquals("Bạn không có quyền tra cứu mã này.", LookupErrorMapper.toUserMessage("not_authorized"))
        assertEquals("Bạn không có quyền tra cứu mã này.", LookupErrorMapper.toUserMessage("permission denied for table"))
        assertEquals("Bạn không có quyền tra cứu mã này.", LookupErrorMapper.toUserMessage("new row violates row-level security policy"))
    }

    @Test
    fun `network errors map to connection message`() {
        assertEquals("Mất kết nối mạng. Vui lòng thử lại.", LookupErrorMapper.toUserMessage("failed to connect"))
        assertEquals("Kết nối quá lâu. Vui lòng thử lại.", LookupErrorMapper.toUserMessage("Read timeout"))
    }

    @Test
    fun `unknown maps to generic`() {
        assertEquals("Không tra cứu được. Vui lòng thử lại.", LookupErrorMapper.toUserMessage("weird backend message"))
        assertEquals("Không tra cứu được. Vui lòng thử lại.", LookupErrorMapper.toUserMessage(null))
    }

    @Test
    fun `isPermissionError detects permission group only`() {
        assertTrue(LookupErrorMapper.isPermissionError("not_authorized"))
        assertTrue(LookupErrorMapper.isPermissionError("Forbidden"))
        assertFalse(LookupErrorMapper.isPermissionError("no rows"))
        assertFalse(LookupErrorMapper.isPermissionError(null))
    }
}
