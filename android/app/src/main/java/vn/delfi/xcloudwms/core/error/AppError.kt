package vn.delfi.xcloudwms.core.error

data class AppError(
    val code: String,
    val message: String,
    val userAction: String? = null,
    val retryable: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
)
