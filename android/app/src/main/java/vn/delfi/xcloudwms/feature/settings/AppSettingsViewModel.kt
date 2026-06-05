package vn.delfi.xcloudwms.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

data class AppSettingsUiState(
    val operatorName: String = "",
    val roleLabels: List<String> = emptyList(),
    val tenantLabel: String = "Chưa xác định",
    val warehouseLabel: String = "Chưa chọn",
    val buildEnvironment: String = "",
    val connectionLabel: String = "Chưa cấu hình",
    val deviceStatusLabel: String = "Chưa kiểm tra",
    val blockSoftKeyboard: Boolean = true,
    val scannerSubmitMode: ScannerSubmitMode = ScannerSubmitMode.ENTER,
    val allowManualInputFallback: Boolean = false,
)

class AppSettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<AppSettingsUiState> = combine(
        sessionRepository.session,
        appPreferences.blockSoftKeyboard,
        appPreferences.scannerSubmitMode,
        appPreferences.allowManualInputFallback,
    ) { session, blockSoftKeyboard, scannerSubmitMode, allowManualInputFallback ->
        AppSettingsUiState(
            operatorName = session.displayName ?: "Chưa đăng nhập",
            roleLabels = session.roles,
            tenantLabel = session.tenant?.label ?: "Chưa xác định",
            warehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn",
            buildEnvironment = session.buildEnvironment.uppercase(),
            connectionLabel = session.connectionLabel ?: "Chưa cấu hình",
            deviceStatusLabel = when (session.deviceLicense?.status) {
                null -> "Chưa kiểm tra"
                DeviceLicenseStatus.PENDING -> "Chờ duyệt"
                DeviceLicenseStatus.BLOCKED -> "Bị chặn"
                DeviceLicenseStatus.REVOKED -> "Đã thu hồi"
                DeviceLicenseStatus.EXPIRED -> "Hết hạn"
                else -> "Đang hoạt động"
            },
            blockSoftKeyboard = blockSoftKeyboard,
            scannerSubmitMode = scannerSubmitMode,
            allowManualInputFallback = allowManualInputFallback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettingsUiState(),
    )

    fun setBlockSoftKeyboard(enabled: Boolean) {
        appPreferences.setBlockSoftKeyboard(enabled)
    }

    fun setScannerSubmitMode(mode: ScannerSubmitMode) {
        appPreferences.setScannerSubmitMode(mode)
    }

    fun setAllowManualInputFallback(enabled: Boolean) {
        appPreferences.setAllowManualInputFallback(enabled)
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.signOut()
        }
    }

    companion object {
        fun factory(
            sessionRepository: SessionRepository,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppSettingsViewModel(
                    sessionRepository = sessionRepository,
                    appPreferences = appPreferences,
                )
            }
        }
    }
}
