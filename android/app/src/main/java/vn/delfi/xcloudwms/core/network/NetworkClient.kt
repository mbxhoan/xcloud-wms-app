package vn.delfi.xcloudwms.core.network

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.config.ConnectionConfig
import vn.delfi.xcloudwms.core.error.AppError
import vn.delfi.xcloudwms.core.logging.SafeLogger

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val error: AppError) : NetworkResult<Nothing>
}

enum class HttpMethod {
    GET,
    POST,
}

data class NetworkRequest(
    val connectionConfig: ConnectionConfig,
    val path: String,
    val method: HttpMethod = HttpMethod.GET,
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val authToken: String? = null,
    val useAnonAuthorization: Boolean = false,
)

data class NetworkResponse(
    val statusCode: Int,
    val body: String?,
)

interface NetworkClient {
    fun summary(connectionConfig: ConnectionConfig?): String

    suspend fun execute(request: NetworkRequest): NetworkResult<NetworkResponse>

    suspend fun testConnection(connectionConfig: ConnectionConfig): NetworkResult<Unit>
}

class DefaultNetworkClient(
    private val appConfig: AppConfig,
    private val logger: SafeLogger,
) : NetworkClient {
    override fun summary(connectionConfig: ConnectionConfig?): String {
        return if (connectionConfig == null) {
            "Chưa cấu hình kết nối"
        } else {
            "Đã cấu hình kết nối tới ${connectionConfig.hostLabel}"
        }
    }

    override suspend fun execute(request: NetworkRequest): NetworkResult<NetworkResponse> {
        return withContext(Dispatchers.IO) {
            val fullUrl = buildUrl(
                connectionConfig = request.connectionConfig,
                path = request.path,
                queryParams = request.queryParams,
            )

            try {
                val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = request.method.name
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                    setRequestProperty("Accept", request.headers["Accept"] ?: "application/json")
                    setRequestProperty("apikey", request.connectionConfig.anonKey)
                    setRequestProperty("X-App-Channel", appConfig.appChannel)

                    val authorizationValue = when {
                        request.authToken != null -> "Bearer ${request.authToken}"
                        request.useAnonAuthorization -> "Bearer ${request.connectionConfig.anonKey}"
                        else -> null
                    }
                    if (!authorizationValue.isNullOrBlank()) {
                        setRequestProperty("Authorization", authorizationValue)
                    }

                    request.headers.forEach { (key, value) ->
                        if (key != "Accept") {
                            setRequestProperty(key, value)
                        }
                    }

                    if (request.body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use { output ->
                            output.write(request.body.toByteArray(Charsets.UTF_8))
                        }
                    }
                }

                val statusCode = connection.responseCode
                val responseBody = runCatching {
                    (if (statusCode >= HTTP_BAD_REQUEST) {
                        connection.errorStream
                    } else {
                        connection.inputStream
                    })?.bufferedReader()?.use { it.readText() }
                }.getOrNull()

                logger.debug(
                    tag = "NetworkClient",
                    message = "HTTP ${request.method.name} ${request.path} -> $statusCode",
                )

                NetworkResult.Success(
                    NetworkResponse(
                        statusCode = statusCode,
                        body = responseBody,
                    ),
                )
            } catch (throwable: Throwable) {
                NetworkResult.Failure(mapThrowableToError(throwable))
            }
        }
    }

    override suspend fun testConnection(connectionConfig: ConnectionConfig): NetworkResult<Unit> {
        val healthResponse = execute(
            NetworkRequest(
                connectionConfig = connectionConfig,
                path = "/auth/v1/health",
            ),
        )
        if (healthResponse !is NetworkResult.Success<*> || (healthResponse.data as NetworkResponse).statusCode !in HTTP_OK..HTTP_MULTIPLE_CHOICES) {
            return NetworkResult.Failure(
                AppError(
                    code = "CONNECTION_URL_UNREACHABLE",
                    message = "Không thể kết nối. Vui lòng kiểm tra lại URL hoặc mạng.",
                    retryable = true,
                ),
            )
        }

        val keyValidationResponse = execute(
            NetworkRequest(
                connectionConfig = connectionConfig,
                path = "/rest/v1/",
                headers = mapOf("Accept" to "application/openapi+json"),
                useAnonAuthorization = true,
            ),
        )

        return when (keyValidationResponse) {
            is NetworkResult.Success<*> -> {
                val networkResponse = keyValidationResponse.data as NetworkResponse
                if (networkResponse.statusCode in HTTP_OK..HTTP_MULTIPLE_CHOICES) {
                    NetworkResult.Success(Unit)
                } else {
                        NetworkResult.Failure(
                            AppError(
                                code = "CONNECTION_KEY_INVALID",
                                message = "Khóa truy cập công khai không hợp lệ hoặc không đủ quyền truy cập.",
                            ),
                        )
                }
            }

            is NetworkResult.Failure -> keyValidationResponse
        }
    }

    private fun buildUrl(
        connectionConfig: ConnectionConfig,
        path: String,
        queryParams: Map<String, String>,
    ): String {
        val builder = Uri.parse(
            connectionConfig.normalizedUrl + ensureLeadingSlash(path),
        ).buildUpon()
        queryParams.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun ensureLeadingSlash(path: String): String {
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun mapThrowableToError(throwable: Throwable): AppError {
        return when (throwable) {
            is UnknownHostException -> AppError(
                code = "NETWORK_UNREACHABLE",
                message = "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng.",
                retryable = true,
            )

            is SocketTimeoutException -> AppError(
                code = "NETWORK_TIMEOUT",
                message = "Kết nối máy chủ quá lâu. Vui lòng thử lại.",
                retryable = true,
            )

            is IOException -> AppError(
                code = "NETWORK_ERROR",
                message = "Không thể hoàn tất yêu cầu mạng. Vui lòng thử lại.",
                retryable = true,
            )

            else -> AppError(
                code = "NETWORK_ERROR",
                message = "Đã xảy ra lỗi kết nối ngoài dự kiến.",
                retryable = true,
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val HTTP_OK = 200
        const val HTTP_MULTIPLE_CHOICES = 299
        const val HTTP_BAD_REQUEST = 400
    }
}
