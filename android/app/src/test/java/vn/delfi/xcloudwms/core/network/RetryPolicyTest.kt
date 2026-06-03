package vn.delfi.xcloudwms.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `chi retry cac ma loi mang tam thoi`() {
        assertTrue(RetryPolicy.isRetryable("NETWORK_TIMEOUT"))
        assertTrue(RetryPolicy.isRetryable("NETWORK_UNREACHABLE"))
        assertTrue(RetryPolicy.isRetryable("NETWORK_ERROR"))
    }

    @Test
    fun `khong retry loi nghiep vu hoac null`() {
        assertFalse(RetryPolicy.isRetryable("PA_CONFLICT"))
        assertFalse(RetryPolicy.isRetryable("SESSION_EXPIRED"))
        assertFalse(RetryPolicy.isRetryable(null))
    }

    @Test
    fun `shouldRetry dung khi con luot va loi retry duoc`() {
        assertTrue(RetryPolicy.shouldRetry(attempt = 1, maxAttempts = 3, errorCode = "NETWORK_TIMEOUT"))
        assertTrue(RetryPolicy.shouldRetry(attempt = 2, maxAttempts = 3, errorCode = "NETWORK_TIMEOUT"))
    }

    @Test
    fun `shouldRetry sai khi het luot`() {
        assertFalse(RetryPolicy.shouldRetry(attempt = 3, maxAttempts = 3, errorCode = "NETWORK_TIMEOUT"))
    }

    @Test
    fun `shouldRetry sai khi loi khong retry duoc du con luot`() {
        assertFalse(RetryPolicy.shouldRetry(attempt = 1, maxAttempts = 3, errorCode = "PA_CONFLICT"))
    }

    @Test
    fun `backoff tang luy thua va bi gioi han`() {
        assertEquals(400L, RetryPolicy.backoffMillis(1))
        assertEquals(800L, RetryPolicy.backoffMillis(2))
        assertEquals(1600L, RetryPolicy.backoffMillis(3))
        // Bị giới hạn ở mức trần, không tràn số.
        assertEquals(2000L, RetryPolicy.backoffMillis(10))
        assertEquals(0L, RetryPolicy.backoffMillis(0))
    }
}
