package vn.delfi.xcloudwms.core.network

import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.error.AppError
import vn.delfi.xcloudwms.core.logging.SafeLogger

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val error: AppError) : NetworkResult<Nothing>
}

interface NetworkClient {
    val config: AppConfig

    fun summary(): String

    suspend fun requestPlaceholder(
        endpoint: String,
        method: String = "GET",
    ): NetworkResult<Unit>
}

class PlaceholderNetworkClient(
    override val config: AppConfig,
    private val logger: SafeLogger,
) : NetworkClient {
    override fun summary(): String {
        return "Client mạng đang ở chế độ chờ (${config.buildEnvironment.uppercase()})"
    }

    override suspend fun requestPlaceholder(
        endpoint: String,
        method: String,
    ): NetworkResult<Unit> {
        logger.info(
            tag = "NetworkClient",
            message = "Blocked placeholder request: method=$method endpoint=$endpoint env=${config.buildEnvironment}",
        )
        return NetworkResult.Failure(
            error = AppError(
                code = "AUTH_SPEC_PENDING",
                message = "Chưa kết nối API thật. Cần chốt đặc tả xác thực trước khi bật network.",
                userAction = "Tiếp tục dùng màn chờ hoặc bổ sung đặc tả xác thực ở bước kế tiếp.",
            ),
        )
    }
}
