package vn.delfi.xcloudwms.core.network

import java.util.UUID

/**
 * Sinh request id idempotency cho các API commit theo contract `docs/04` mục 6 và
 * `docs/06` mục 4: `<device>:<feature>:<document>:<line>:<uuid>`.
 *
 * Backend sync engine dùng key này (`sync_operations.operation_id`, unique theo tenant) để
 * chống commit trùng. Với các RPC legacy chưa idempotent, header chỉ mang tính forward-compat
 * (backend bỏ qua), nên client KHÔNG được tự động retry commit dựa vào id này — xem [RetryPolicy].
 */
object RequestId {
    /**
     * Tạo id ổn định theo hành động. Cùng một (feature, document, line) nhưng mỗi lần gọi
     * sẽ kèm uuid mới; để retry cùng id, caller phải lưu lại id đã tạo và truyền lại.
     */
    fun forCommit(
        feature: String,
        documentId: String? = null,
        lineId: String? = null,
        deviceId: String? = null,
    ): String {
        return listOf(
            deviceId.sanitize("app"),
            feature.sanitize("feature"),
            documentId.sanitize("-"),
            lineId.sanitize("-"),
            UUID.randomUUID().toString(),
        ).joinToString(":")
    }

    private fun String?.sanitize(fallback: String): String {
        val cleaned = this?.trim()
            ?.replace(":", "_")
            ?.replace(Regex("\\s+"), "_")
        return if (cleaned.isNullOrEmpty()) fallback else cleaned
    }
}
