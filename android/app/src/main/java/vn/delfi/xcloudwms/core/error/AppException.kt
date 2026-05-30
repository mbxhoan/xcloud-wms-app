package vn.delfi.xcloudwms.core.error

class AppException(
    val appError: AppError,
) : IllegalStateException(appError.message)

fun Throwable.toAppError(): AppError {
    return when (this) {
        is AppException -> appError
        else -> AppError(
            code = "UNEXPECTED_ERROR",
            message = message ?: "Đã xảy ra lỗi ngoài dự kiến.",
        )
    }
}
