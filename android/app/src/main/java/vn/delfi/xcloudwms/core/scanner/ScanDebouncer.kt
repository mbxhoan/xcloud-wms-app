package vn.delfi.xcloudwms.core.scanner

/**
 * Chống trùng quét theo mã đã chuẩn hoá. Tách riêng (pure Kotlin) để dễ kiểm thử và để
 * pipeline không tự quản state trùng lặp.
 */
class ScanDebouncer {
    private var lastNormalized: String? = null
    private var lastAcceptedAt: Long = 0L

    /**
     * @return true nếu nên nhận mã này; false nếu là trùng trong [ScanDebounceConfig.intervalMs].
     * Khi [ScanDebounceConfig.continuousSerial] bật thì luôn nhận.
     */
    fun shouldAccept(normalized: String, now: Long, config: ScanDebounceConfig): Boolean {
        if (config.continuousSerial) {
            record(normalized, now)
            return true
        }
        if (normalized == lastNormalized && now - lastAcceptedAt < config.intervalMs) {
            return false
        }
        record(normalized, now)
        return true
    }

    fun reset() {
        lastNormalized = null
        lastAcceptedAt = 0L
    }

    private fun record(normalized: String, now: Long) {
        lastNormalized = normalized
        lastAcceptedAt = now
    }
}
