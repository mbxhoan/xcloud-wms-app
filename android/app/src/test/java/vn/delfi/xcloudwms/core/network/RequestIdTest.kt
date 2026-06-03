package vn.delfi.xcloudwms.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestIdTest {

    @Test
    fun `dung dinh dang device feature document line uuid`() {
        val id = RequestId.forCommit(
            feature = "PA_SUBMIT",
            documentId = "42",
            lineId = "7",
            deviceId = "dev1",
        )
        val parts = id.split(":")
        assertEquals(5, parts.size)
        assertEquals("dev1", parts[0])
        assertEquals("PA_SUBMIT", parts[1])
        assertEquals("42", parts[2])
        assertEquals("7", parts[3])
        assertTrue(parts[4].isNotBlank())
    }

    @Test
    fun `moi lan goi sinh uuid khac nhau`() {
        val a = RequestId.forCommit(feature = "PA_SUBMIT", documentId = "1")
        val b = RequestId.forCommit(feature = "PA_SUBMIT", documentId = "1")
        assertNotEquals(a, b)
    }

    @Test
    fun `dung fallback khi thieu device document line`() {
        val id = RequestId.forCommit(feature = "GI_COMPLETE")
        val parts = id.split(":")
        assertEquals("app", parts[0])
        assertEquals("GI_COMPLETE", parts[1])
        assertEquals("-", parts[2])
        assertEquals("-", parts[3])
    }

    @Test
    fun `lam sach dau hai cham va khoang trang de khong vo dinh dang`() {
        val id = RequestId.forCommit(
            feature = "PA SUBMIT",
            documentId = "a:b",
            deviceId = " dev 1 ",
        )
        val parts = id.split(":")
        assertEquals(5, parts.size)
        assertEquals("dev_1", parts[0])
        assertEquals("PA_SUBMIT", parts[1])
        assertEquals("a_b", parts[2])
    }
}
