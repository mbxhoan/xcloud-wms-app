package vn.delfi.xcloudwms.data.device

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.error.AppError
import vn.delfi.xcloudwms.core.error.AppException
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.network.HttpMethod
import vn.delfi.xcloudwms.core.network.NetworkClient
import vn.delfi.xcloudwms.core.network.NetworkRequest
import vn.delfi.xcloudwms.core.network.NetworkResponse
import vn.delfi.xcloudwms.core.network.NetworkResult
import vn.delfi.xcloudwms.core.security.SecureSessionStorage
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.core.storage.OfflineStore
import vn.delfi.xcloudwms.domain.model.DeviceLicenseState
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

data class DeviceRegistrationSnapshot(
    val installId: String,
    val deviceName: String,
    val deviceType: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val deviceOs: String,
    val deviceOsVersion: String,
    val appVersion: String,
    val androidIdHash: String?,
    val vendorSerialHash: String?,
    val fingerprint: String,
    val screenResolution: String,
    val timezone: String,
    val language: String,
    val hardwareConcurrency: Int,
    val deviceMemoryGb: Double?,
    val userAgent: String,
    val clientPlatform: String,
    val metadata: JSONObject,
)

internal data class DeviceLicenseRpcPayload(
    val allowed: Boolean,
    val reason: String,
    val message: String?,
    val deviceName: String?,
    val deviceCode: String?,
)

interface DeviceLicenseRepository {
    fun snapshot(): DeviceRegistrationSnapshot

    suspend fun verify(userId: String): Result<DeviceLicenseState>

    suspend fun syncDeviceProfile(userId: String): Result<Unit>

    suspend fun recordLoginHistory(
        userId: String,
        status: String,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): Result<Unit>
}

class DefaultDeviceLicenseRepository(
    private val context: Context,
    private val appConfig: AppConfig,
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val offlineStore: OfflineStore,
    private val logger: SafeLogger,
) : DeviceLicenseRepository {
    private val profileCollector = NativeDeviceProfileCollector(
        context = context,
        offlineStore = offlineStore,
    )

    override fun snapshot(): DeviceRegistrationSnapshot = profileCollector.collect()

    override suspend fun verify(userId: String): Result<DeviceLicenseState> {
        if (!appConfig.enableDeviceLicenseCheck) {
            return Result.success(
                DeviceLicenseState(
                    status = DeviceLicenseStatus.ACTIVE,
                    reasonCode = "DISABLED",
                    message = "Đơn vị hiện chưa bật kiểm soát thiết bị scanner.",
                    checkedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }

        val connectionConfig = appPreferences.currentConnectionConfig()
            ?: return failure(
                code = "CONNECTION_REQUIRED",
                message = "Chưa có cấu hình kết nối để kiểm tra trạng thái thiết bị.",
            )

        val storedSession = secureSessionStorage.loadSession()
            ?: return failure(
                code = "SESSION_REQUIRED",
                message = "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
            )

        val device = snapshot()
        val request = NetworkRequest(
            connectionConfig = connectionConfig,
            path = DEVICE_LICENSE_RPC_PATH,
            method = HttpMethod.POST,
            authToken = storedSession.accessToken,
            body = JSONObject()
                .put("p_user_id", userId)
                .put("p_device_id", device.installId)
                .put("p_device_fingerprint", device.fingerprint)
                .put("p_device_name", device.deviceName)
                .put("p_device_type", device.deviceType)
                .put("p_device_os", device.deviceOs)
                .put("p_device_os_version", device.deviceOsVersion)
                .put("p_device_model", device.model)
                .toString(),
        )

        return withContext(Dispatchers.IO) {
            when (val result = networkClient.execute(request)) {
                is NetworkResult.Failure -> Result.failure(AppException(result.error))
                is NetworkResult.Success<*> -> parseVerifyResponse(result.data as NetworkResponse)
            }
        }.onSuccess { state ->
            logger.info(
                TAG,
                "Kiểm tra trạng thái thiết bị thành công: status=${state.status.name}, reason=${state.reasonCode}",
            )
        }.onFailure { throwable ->
            logger.error(TAG, "Kiểm tra trạng thái thiết bị thất bại", throwable)
        }
    }

    override suspend fun syncDeviceProfile(userId: String): Result<Unit> {
        if (!appConfig.enableDeviceLicenseCheck) {
            return Result.success(Unit)
        }

        val connectionConfig = appPreferences.currentConnectionConfig()
            ?: return failure(
                code = "CONNECTION_REQUIRED",
                message = "Chưa có cấu hình kết nối để đồng bộ hồ sơ thiết bị.",
            )

        val storedSession = secureSessionStorage.loadSession()
            ?: return failure(
                code = "SESSION_REQUIRED",
                message = "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
            )

        val device = snapshot()
        val request = NetworkRequest(
            connectionConfig = connectionConfig,
            path = DEVICE_LICENSE_PROFILE_RPC_PATH,
            method = HttpMethod.POST,
            authToken = storedSession.accessToken,
            body = JSONObject()
                .put("p_user_id", userId)
                .put("p_device_id", device.installId)
                .put("p_device_fingerprint", device.fingerprint)
                .put("p_device_name", device.deviceName)
                .put("p_device_type", device.deviceType)
                .put("p_device_os", device.deviceOs)
                .put("p_device_os_version", device.deviceOsVersion)
                .put("p_device_model", device.model)
                .put("p_client_platform", device.clientPlatform)
                .put("p_device_metadata", device.metadata)
                .toString(),
        )

        return withContext(Dispatchers.IO) {
            when (val result = networkClient.execute(request)) {
                is NetworkResult.Failure -> Result.failure(AppException(result.error))
                is NetworkResult.Success<*> -> parseWriteResponse(
                    response = result.data as NetworkResponse,
                    fallbackCode = "DEVICE_PROFILE_SYNC_FAILED",
                    fallbackMessage = "Không thể đồng bộ hồ sơ thiết bị.",
                )
            }
        }
    }

    override suspend fun recordLoginHistory(
        userId: String,
        status: String,
        errorCode: String?,
        errorMessage: String?,
    ): Result<Unit> {
        val connectionConfig = appPreferences.currentConnectionConfig()
            ?: return failure(
                code = "CONNECTION_REQUIRED",
                message = "Chưa có cấu hình kết nối để ghi lịch sử đăng nhập.",
            )

        val storedSession = secureSessionStorage.loadSession()
            ?: return failure(
                code = "SESSION_REQUIRED",
                message = "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
            )

        val device = snapshot()
        val request = NetworkRequest(
            connectionConfig = connectionConfig,
            path = LOGIN_HISTORY_TABLE_PATH,
            method = HttpMethod.POST,
            authToken = storedSession.accessToken,
            headers = mapOf("Prefer" to "return=minimal"),
            body = JSONObject()
                .put("user_id", userId)
                .put("status", status)
                .put("login_app", "SCANNER")
                .put("client_platform", device.clientPlatform)
                .put("device_id", device.installId)
                .put("device_name", device.deviceName)
                .put("device_type", device.deviceType)
                .put("device_os", device.deviceOs)
                .put("device_os_version", device.deviceOsVersion)
                .put("device_model", device.model)
                .put("browser_name", "Scanner Native")
                .put("browser_version", device.appVersion)
                .put("user_agent", device.userAgent)
                .put("app_version", device.appVersion)
                .put("screen_resolution", device.screenResolution)
                .put("timezone", device.timezone)
                .put("language", device.language)
                .put("is_mobile", true)
                .put("is_pwa", false)
                .put("hardware_concurrency", device.hardwareConcurrency)
                .put("device_memory", device.deviceMemoryGb)
                .put("device_metadata", device.metadata)
                .put("error_code", errorCode)
                .put("error_message", errorMessage)
                .toString(),
        )

        return withContext(Dispatchers.IO) {
            when (val result = networkClient.execute(request)) {
                is NetworkResult.Failure -> Result.failure(AppException(result.error))
                is NetworkResult.Success<*> -> parseWriteResponse(
                    response = result.data as NetworkResponse,
                    fallbackCode = "LOGIN_HISTORY_RECORD_FAILED",
                    fallbackMessage = "Không thể ghi lịch sử đăng nhập.",
                )
            }
        }
    }

    private fun parseVerifyResponse(response: NetworkResponse): Result<DeviceLicenseState> {
        if (response.statusCode == HTTP_UNAUTHORIZED || response.statusCode == HTTP_FORBIDDEN) {
            return failure(
                code = "SESSION_EXPIRED",
                message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
            )
        }

        if (response.statusCode !in HTTP_OK..HTTP_MULTIPLE_CHOICES) {
            return failure(
                code = "DEVICE_LICENSE_CHECK_FAILED",
                message = extractErrorMessage(response.body)
                    ?: "Không thể xác thực trạng thái thiết bị. Vui lòng thử lại.",
                retryable = response.statusCode >= HTTP_INTERNAL_SERVER_ERROR,
            )
        }

        val payload = parsePayload(response.body)
            ?: return failure(
                code = "DEVICE_LICENSE_INVALID_RESPONSE",
                message = "Máy chủ trả về trạng thái thiết bị không hợp lệ.",
            )

        return Result.success(
            mapDeviceLicensePayloadToState(
                payload = payload,
                checkedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun parsePayload(body: String?): DeviceLicenseRpcPayload? {
        val root = body?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        } ?: return null

        val json = root as? JSONObject ?: return null
        return DeviceLicenseRpcPayload(
            allowed = json.optBoolean("allowed", false),
            reason = json.optString("reason").orEmpty(),
            message = json.optString("message").takeIf { it.isNotBlank() },
            deviceName = json.optString("device_name").takeIf { it.isNotBlank() },
            deviceCode = json.optString("device_code").takeIf { it.isNotBlank() },
        )
    }

    private fun extractErrorMessage(body: String?): String? {
        val root = body?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        } ?: return null

        if (root is JSONObject) {
            root.optString("message").takeIf { it.isNotBlank() }?.let { return it }
            val nestedError = root.optJSONObject("error")
            nestedError?.optString("message")?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return null
    }

    private fun parseWriteResponse(
        response: NetworkResponse,
        fallbackCode: String,
        fallbackMessage: String,
    ): Result<Unit> {
        if (response.statusCode == HTTP_UNAUTHORIZED || response.statusCode == HTTP_FORBIDDEN) {
            return failure(
                code = "SESSION_EXPIRED",
                message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
            )
        }

        if (response.statusCode !in HTTP_OK..HTTP_MULTIPLE_CHOICES) {
            return failure(
                code = fallbackCode,
                message = extractErrorMessage(response.body) ?: fallbackMessage,
                retryable = response.statusCode >= HTTP_INTERNAL_SERVER_ERROR,
            )
        }

        return Result.success(Unit)
    }

    private fun <T> failure(
        code: String,
        message: String,
        retryable: Boolean = false,
    ): Result<T> {
        return Result.failure(
            AppException(
                AppError(
                    code = code,
                    message = message,
                    retryable = retryable,
                ),
            ),
        )
    }

    private companion object {
        const val TAG = "DeviceLicenseRepo"
        const val DEVICE_LICENSE_RPC_PATH = "/rest/v1/rpc/fn_scanner_check_device_license"
        const val DEVICE_LICENSE_PROFILE_RPC_PATH = "/rest/v1/rpc/fn_scanner_sync_device_license_profile"
        const val LOGIN_HISTORY_TABLE_PATH = "/rest/v1/login_history"
        const val HTTP_OK = 200
        const val HTTP_MULTIPLE_CHOICES = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_INTERNAL_SERVER_ERROR = 500
    }
}

internal fun mapDeviceLicensePayloadToState(
    payload: DeviceLicenseRpcPayload,
    checkedAtEpochMillis: Long,
): DeviceLicenseState {
    val normalizedReason = payload.reason.trim().uppercase(Locale.ROOT)

    val (status, message) = when {
        payload.allowed -> {
            val activeMessage = when (normalizedReason) {
                "DISABLED" -> "Đơn vị hiện chưa bật kiểm soát thiết bị scanner."
                else -> "Thiết bị đã được cấp phép sử dụng app scanner."
            }
            DeviceLicenseStatus.ACTIVE to (payload.message?.trim().takeUnless { it.isNullOrEmpty() } ?: activeMessage)
        }

        normalizedReason == "PENDING_APPROVAL" ->
            DeviceLicenseStatus.PENDING to "Thiết bị đang chờ quản trị viên duyệt."

        normalizedReason in QUOTA_EXCEEDED_REASON_CODES ->
            DeviceLicenseStatus.BLOCKED to "Số lượng thiết bị scanner đã vượt giới hạn gói."

        normalizedReason in EXPIRED_REASON_CODES ->
            DeviceLicenseStatus.EXPIRED to "Thiết bị này đã hết hạn sử dụng trên hệ thống."

        normalizedReason == "DEVICE_REVOKED" ->
            DeviceLicenseStatus.REVOKED to "Thiết bị này không được phép sử dụng app scanner."

        normalizedReason == "USER_NOT_ASSIGNED" ->
            DeviceLicenseStatus.BLOCKED to "Tài khoản này chưa được cấp phép sử dụng thiết bị này."

        normalizedReason == "FINGERPRINT_MISMATCH" ->
            DeviceLicenseStatus.BLOCKED to "Thông tin thiết bị không khớp với đăng ký đã lưu."

        normalizedReason == "NO_DEVICE_ID" ->
            DeviceLicenseStatus.BLOCKED to "Không thể xác định mã cài đặt của thiết bị."

        normalizedReason == "USER_NOT_FOUND" ->
            DeviceLicenseStatus.BLOCKED to "Không tìm thấy người dùng gắn với thiết bị này."

        else ->
            DeviceLicenseStatus.BLOCKED to (
                payload.message?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Thiết bị này không được phép sử dụng app scanner."
                )
    }

    return DeviceLicenseState(
        status = status,
        reasonCode = normalizedReason.ifBlank { if (payload.allowed) "OK" else "BLOCKED" },
        message = message,
        checkedAtEpochMillis = checkedAtEpochMillis,
        backendDeviceName = payload.deviceName,
        backendDeviceCode = payload.deviceCode,
    )
}

private val QUOTA_EXCEEDED_REASON_CODES = setOf(
    "QUOTA_EXCEEDED",
    "MAX_SCANNERS_EXCEEDED",
    "SCANNER_LIMIT_EXCEEDED",
    "SUBSCRIPTION_SCANNER_LIMIT_EXCEEDED",
)

private val EXPIRED_REASON_CODES = setOf(
    "EXPIRED",
    "DEVICE_EXPIRED",
    "LICENSE_EXPIRED",
    "SUBSCRIPTION_EXPIRED",
)

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
