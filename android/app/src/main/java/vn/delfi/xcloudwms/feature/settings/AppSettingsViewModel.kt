package vn.delfi.xcloudwms.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.error.toAppError
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
    val autoSync: Boolean = true,
    val manualOffline: Boolean = false,
)

/** Bước hiện tại của dialog cấu hình kết nối: nhập mật khẩu xác thực rồi mới tới cấu hình. */
enum class ConnConfigPhase { PASSWORD, CONFIG }

/** Trạng thái nút "Kiểm tra kết nối". */
enum class ConnTestState { IDLE, TESTING, OK, ERROR }

data class ConnConfigDialogState(
    val open: Boolean = false,
    val phase: ConnConfigPhase = ConnConfigPhase.PASSWORD,
    val passwordInput: String = "",
    val passwordError: String? = null,
    val urlInput: String = "",
    val keyInput: String = "",
    val showKey: Boolean = false,
    val testState: ConnTestState = ConnTestState.IDLE,
    val testError: String? = null,
    val saving: Boolean = false,
)

class AppSettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val prefsFlow = combine(
        appPreferences.blockSoftKeyboard,
        appPreferences.scannerSubmitMode,
        appPreferences.allowManualInputFallback,
        appPreferences.autoSync,
        appPreferences.manualOffline,
    ) { blockSoftKeyboard, scannerSubmitMode, allowManualInputFallback, autoSync, manualOffline ->
        PrefsSnapshot(
            blockSoftKeyboard = blockSoftKeyboard,
            scannerSubmitMode = scannerSubmitMode,
            allowManualInputFallback = allowManualInputFallback,
            autoSync = autoSync,
            manualOffline = manualOffline,
        )
    }

    val uiState: StateFlow<AppSettingsUiState> = combine(
        sessionRepository.session,
        prefsFlow,
    ) { session, prefs ->
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
            blockSoftKeyboard = prefs.blockSoftKeyboard,
            scannerSubmitMode = prefs.scannerSubmitMode,
            allowManualInputFallback = prefs.allowManualInputFallback,
            autoSync = prefs.autoSync,
            manualOffline = prefs.manualOffline,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettingsUiState(),
    )

    private val mutableConnConfig = MutableStateFlow(ConnConfigDialogState())
    val connConfigState: StateFlow<ConnConfigDialogState> = mutableConnConfig.asStateFlow()

    fun setBlockSoftKeyboard(enabled: Boolean) {
        appPreferences.setBlockSoftKeyboard(enabled)
    }

    fun setScannerSubmitMode(mode: ScannerSubmitMode) {
        appPreferences.setScannerSubmitMode(mode)
    }

    fun setAllowManualInputFallback(enabled: Boolean) {
        appPreferences.setAllowManualInputFallback(enabled)
    }

    fun setAutoSync(enabled: Boolean) {
        appPreferences.setAutoSync(enabled)
    }

    fun setManualOffline(enabled: Boolean) {
        appPreferences.setManualOffline(enabled)
    }

    // ─── Cấu hình kết nối ───────────────────────────────────────────────────────

    fun openConnConfig() {
        mutableConnConfig.value = ConnConfigDialogState(
            open = true,
            phase = ConnConfigPhase.PASSWORD,
            // Prefill URL để admin/IT thấy ngay cấu hình hiện tại; KHÔNG prefill key sau đăng nhập
            // để tránh lộ key trên thiết bị đã giao cho nhân viên (parity scanner PWA).
            urlInput = appPreferences.currentConnectionConfig()?.url.orEmpty(),
        )
    }

    fun closeConnConfig() {
        mutableConnConfig.update { it.copy(open = false) }
    }

    fun updateConnPassword(value: String) {
        mutableConnConfig.update { it.copy(passwordInput = value, passwordError = null) }
    }

    fun submitConnPassword() {
        val input = mutableConnConfig.value.passwordInput
        if (input == appPreferences.getConfigPassword()) {
            mutableConnConfig.update { it.copy(phase = ConnConfigPhase.CONFIG, passwordError = null) }
        } else {
            mutableConnConfig.update { it.copy(passwordError = "Mật khẩu xác thực không đúng.") }
        }
    }

    fun updateConnUrl(value: String) {
        mutableConnConfig.update {
            it.copy(urlInput = value, testState = ConnTestState.IDLE, testError = null)
        }
    }

    fun updateConnKey(value: String) {
        mutableConnConfig.update {
            it.copy(keyInput = value, testState = ConnTestState.IDLE, testError = null)
        }
    }

    fun toggleConnKeyVisible() {
        mutableConnConfig.update { it.copy(showKey = !it.showKey) }
    }

    fun testConnConfig() {
        val snapshot = mutableConnConfig.value
        if (snapshot.testState == ConnTestState.TESTING || snapshot.saving) {
            return
        }
        val url = snapshot.urlInput.trim()
        val key = snapshot.keyInput.trim()
        if (url.isBlank() || key.isBlank()) {
            mutableConnConfig.update {
                it.copy(
                    testState = ConnTestState.ERROR,
                    testError = "Vui lòng nhập đầy đủ địa chỉ kết nối và khóa truy cập công khai.",
                )
            }
            return
        }

        viewModelScope.launch {
            mutableConnConfig.update { it.copy(testState = ConnTestState.TESTING, testError = null) }
            sessionRepository.testConnectionConfig(url = url, anonKey = key)
                .onSuccess {
                    mutableConnConfig.update { it.copy(testState = ConnTestState.OK, testError = null) }
                }
                .onFailure { throwable ->
                    mutableConnConfig.update {
                        it.copy(
                            testState = ConnTestState.ERROR,
                            testError = throwable.toAppError().message,
                        )
                    }
                }
        }
    }

    /**
     * Lưu cấu hình mới rồi đăng xuất: đổi backend đồng nghĩa phải đăng nhập lại. Lưu trước (giữ config
     * mới), sau đó [SessionRepository.signOut] xoá token cũ. NavHost tự điều hướng về màn đăng nhập.
     */
    fun saveConnConfig() {
        val snapshot = mutableConnConfig.value
        if (snapshot.testState != ConnTestState.OK || snapshot.saving) {
            return
        }

        viewModelScope.launch {
            mutableConnConfig.update { it.copy(saving = true) }
            sessionRepository.saveConnectionConfig(
                url = snapshot.urlInput.trim(),
                anonKey = snapshot.keyInput.trim(),
            ).onSuccess {
                sessionRepository.signOut()
                mutableConnConfig.value = ConnConfigDialogState()
            }.onFailure { throwable ->
                mutableConnConfig.update {
                    it.copy(
                        saving = false,
                        testState = ConnTestState.ERROR,
                        testError = throwable.toAppError().message,
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.signOut()
        }
    }

    private data class PrefsSnapshot(
        val blockSoftKeyboard: Boolean,
        val scannerSubmitMode: ScannerSubmitMode,
        val allowManualInputFallback: Boolean,
        val autoSync: Boolean,
        val manualOffline: Boolean,
    )

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
