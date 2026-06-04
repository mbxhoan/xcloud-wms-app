package vn.delfi.xcloudwms.feature.devicelicense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.data.device.DeviceLicenseRepository
import vn.delfi.xcloudwms.domain.model.DeviceLicenseState
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus
import vn.delfi.xcloudwms.domain.model.UserSession
import vn.delfi.xcloudwms.data.session.SessionRepository

class DeviceLicenseViewModel(
    private val sessionRepository: SessionRepository,
    private val deviceLicenseRepository: DeviceLicenseRepository,
    private val logger: SafeLogger,
) : ViewModel() {
    private val deviceSnapshot = deviceLicenseRepository.snapshot()
    private val mutableUiState = MutableStateFlow(
        DeviceLicenseUiState(
            installId = deviceSnapshot.installId,
            deviceName = deviceSnapshot.deviceName,
            deviceTypeLabel = deviceTypeLabel(deviceSnapshot.deviceType),
            brand = deviceSnapshot.brand,
            model = deviceSnapshot.model,
            osLabel = "${deviceSnapshot.deviceOs} ${deviceSnapshot.deviceOsVersion}".trim(),
            appVersion = deviceSnapshot.appVersion,
            fingerprintPreview = shorten(deviceSnapshot.fingerprint),
            androidIdHashPreview = deviceSnapshot.androidIdHash?.let(::shorten).orEmpty(),
            vendorSerialHashPreview = deviceSnapshot.vendorSerialHash?.let(::shorten).orEmpty(),
        ),
    )
    val uiState: StateFlow<DeviceLicenseUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                mutableUiState.update { current ->
                    current.mergeSession(session)
                }
            }
        }
    }

    fun refreshStatus() {
        if (mutableUiState.value.isRefreshing) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            sessionRepository.refreshDeviceLicense(force = true)
                .onFailure { throwable ->
                    logger.error(TAG, "Làm mới trạng thái thiết bị thất bại", throwable)
                    mutableUiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = throwable.message ?: "Không thể làm mới trạng thái thiết bị.",
                        )
                    }
                }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sessionRepository.signOut()
        }
    }

    companion object {
        private const val TAG = "DeviceLicenseVM"

        fun factory(
            sessionRepository: SessionRepository,
            deviceLicenseRepository: DeviceLicenseRepository,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceLicenseViewModel(
                    sessionRepository = sessionRepository,
                    deviceLicenseRepository = deviceLicenseRepository,
                    logger = logger,
                )
            }
        }

        private fun shorten(value: String): String {
            if (value.length <= 22) {
                return value
            }
            return "${value.take(12)}...${value.takeLast(6)}"
        }

        private fun deviceTypeLabel(rawType: String): String {
            return when (rawType.trim().uppercase()) {
                "TABLET" -> "Máy tính bảng"
                else -> "Thiết bị cầm tay"
            }
        }

        private fun statusLabel(status: DeviceLicenseStatus): String {
            return when (status) {
                DeviceLicenseStatus.ACTIVE -> "Đang hoạt động"
                DeviceLicenseStatus.PENDING -> "Chờ duyệt"
                DeviceLicenseStatus.BLOCKED -> "Bị chặn"
                DeviceLicenseStatus.REVOKED -> "Đã thu hồi"
                DeviceLicenseStatus.EXPIRED -> "Hết hạn"
            }
        }

        private fun formatCheckedAt(epochMillis: Long?): String {
            if (epochMillis == null || epochMillis <= 0L) {
                return "Chưa kiểm tra"
            }
            return "Kiểm tra lúc " + formatter.format(
                Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
            )
        }

        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        private fun DeviceLicenseUiState.mergeSession(session: UserSession): DeviceLicenseUiState {
            val deviceLicense = session.deviceLicense
            val resolvedStatus = deviceLicense?.status ?: DeviceLicenseStatus.ACTIVE
            return copy(
                operatorName = session.displayName ?: session.email ?: "Người dùng",
                tenantLabel = session.tenant?.label ?: "Chưa xác định",
                status = resolvedStatus,
                statusLabel = statusLabel(resolvedStatus),
                message = deviceLicense?.message ?: "Chưa có dữ liệu trạng thái thiết bị.",
                reasonCode = deviceLicense?.reasonCode ?: "UNKNOWN",
                checkedAtLabel = formatCheckedAt(deviceLicense?.checkedAtEpochMillis),
                backendDeviceName = deviceLicense?.backendDeviceName.orEmpty(),
                backendDeviceCode = deviceLicense?.backendDeviceCode.orEmpty(),
                canOperate = deviceLicense?.canOperate ?: true,
            )
        }
    }
}
