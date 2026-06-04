package vn.delfi.xcloudwms.data.device

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import vn.delfi.xcloudwms.BuildConfig
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

    override fun snapshot(): DeviceRegistrationSnapshot {
        val manufacturer = normalizedValue(Build.MANUFACTURER, "Android")
        val brand = normalizedValue(Build.BRAND, manufacturer)
        val model = normalizedValue(Build.MODEL, "Unknown Model")
        val deviceName = buildDeviceName(manufacturer = manufacturer, model = model)
        val androidIdHash = readAndroidIdHash()
        val vendorSerialHash: String? = null
        val fingerprint = sha256Hex(
            listOf(
                manufacturer,
                brand,
                model,
                androidIdHash.orEmpty(),
                vendorSerialHash.orEmpty(),
            ).joinToString("|"),
        )

        return DeviceRegistrationSnapshot(
            installId = offlineStore.deviceInstallId(),
            deviceName = deviceName,
            deviceType = resolveDeviceType(),
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            deviceOs = "Android",
            deviceOsVersion = buildOsVersion(),
            appVersion = BuildConfig.VERSION_NAME,
            androidIdHash = androidIdHash,
            vendorSerialHash = vendorSerialHash,
            fingerprint = fingerprint,
        )
    }

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

    private fun readAndroidIdHash(): String? {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        if (androidId.isBlank() || androidId.equals("unknown", ignoreCase = true)) {
            return null
        }

        return sha256Hex(androidId)
    }

    private fun buildDeviceName(
        manufacturer: String,
        model: String,
    ): String {
        val normalizedManufacturer = manufacturer.trim()
        val normalizedModel = model.trim()
        return if (
            normalizedManufacturer.isBlank() ||
            normalizedModel.startsWith(normalizedManufacturer, ignoreCase = true)
        ) {
            normalizedModel.ifBlank { "Android Device" }
        } else {
            "$normalizedManufacturer $normalizedModel".trim()
        }
    }

    private fun resolveDeviceType(): String {
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return if (screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            "TABLET"
        } else {
            "MOBILE"
        }
    }

    private fun buildOsVersion(): String {
        return buildString {
            append(normalizedValue(Build.VERSION.RELEASE, "Unknown"))
            append(" (API ")
            append(Build.VERSION.SDK_INT)
            append(")")
        }
    }

    private fun normalizedValue(
        value: String?,
        fallback: String,
    ): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.isBlank() || normalized.equals("unknown", ignoreCase = true)) {
            fallback
        } else {
            normalized
        }
    }

    private fun failure(
        code: String,
        message: String,
        retryable: Boolean = false,
    ): Result<DeviceLicenseState> {
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
