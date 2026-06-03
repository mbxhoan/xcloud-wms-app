package vn.delfi.xcloudwms.core.network

/**
 * Chính sách retry cho GET/list/detail (xem `docs/06` mục 7).
 *
 * Chỉ retry các lỗi mạng tạm thời và CHỈ cho request idempotent (GET). KHÔNG dùng để retry
 * commit POST khi backend chưa hỗ trợ idempotency — retry mù có thể commit trùng (sai stock).
 */
object RetryPolicy {
    const val MAX_GET_ATTEMPTS = 3

    private const val BASE_DELAY_MS = 400L
    private const val MAX_DELAY_MS = 2_000L

    /** Mã lỗi mạng tạm thời do [DefaultNetworkClient] sinh ra. */
    private val RETRYABLE_CODES = setOf(
        "NETWORK_TIMEOUT",
        "NETWORK_UNREACHABLE",
        "NETWORK_ERROR",
    )

    fun isRetryable(errorCode: String?): Boolean = errorCode in RETRYABLE_CODES

    /**
     * @param attempt số lần đã gọi (bắt đầu từ 1 cho lần đầu).
     * @return true nếu còn lượt và lỗi thuộc nhóm retry được.
     */
    fun shouldRetry(attempt: Int, maxAttempts: Int, errorCode: String?): Boolean =
        attempt in 1 until maxAttempts && isRetryable(errorCode)

    /** Backoff luỹ thừa: 400ms, 800ms, 1600ms… giới hạn ở [MAX_DELAY_MS]. */
    fun backoffMillis(attempt: Int): Long {
        if (attempt < 1) return 0L
        val shift = (attempt - 1).coerceAtMost(MAX_SHIFT)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }

    private const val MAX_SHIFT = 16
}
