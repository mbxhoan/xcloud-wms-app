package vn.delfi.xcloudwms.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.data.session.SessionRepository

class LoginViewModel(
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()
    private var autoLoginAttempted = false
    private var autoLoginJob: Job? = null

    init {
        mutableUiState.update {
            it.copy(
                operatorCode = appConfig.defaultOperatorCode,
                password = appConfig.defaultPassword,
                showConnectionSection = !appConfig.hasBootstrapConnectionConfig,
            )
        }

        sessionRepository.currentConnectionConfig()?.let { connectionConfig ->
            mutableUiState.update {
                it.copy(
                    connectionUrl = connectionConfig.normalizedUrl,
                    anonKey = connectionConfig.anonKey,
                )
            }
        }

        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                mutableUiState.update { current ->
                    current.copy(
                        connectionConfigured = session.connectionConfigured,
                        connectionLabel = session.connectionLabel,
                        loginErrorMessage = current.loginErrorMessage ?: session.errorMessage,
                    )
                }

                if (shouldAutoLogin()) {
                    autoLoginAttempted = true
                    submit()
                }
            }
        }
    }

    fun updateOperatorCode(value: String) {
        mutableUiState.update {
            it.copy(operatorCode = value, loginErrorMessage = null)
        }
    }

    fun updatePassword(value: String) {
        mutableUiState.update {
            it.copy(password = value, loginErrorMessage = null)
        }
    }

    fun togglePasswordVisibility() {
        mutableUiState.update {
            it.copy(isPasswordVisible = !it.isPasswordVisible)
        }
    }

    fun updateConnectionUrl(value: String) {
        mutableUiState.update {
            it.copy(
                connectionUrl = value,
                connectionErrorMessage = null,
                connectionSuccessMessage = null,
            )
        }
    }

    fun updateAnonKey(value: String) {
        mutableUiState.update {
            it.copy(
                anonKey = value,
                connectionErrorMessage = null,
                connectionSuccessMessage = null,
            )
        }
    }

    fun testConnection() {
        val snapshot = mutableUiState.value
        if (snapshot.isConnectionBusy || snapshot.isLoading) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isTestingConnection = true,
                    connectionErrorMessage = null,
                    connectionSuccessMessage = null,
                )
            }

            sessionRepository.testConnectionConfig(
                url = snapshot.connectionUrl,
                anonKey = snapshot.anonKey,
            ).onSuccess {
                mutableUiState.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionSuccessMessage = "Kết nối thành công. Bạn có thể lưu cấu hình.",
                    )
                }
            }.onFailure { throwable ->
                logger.error("LoginViewModel", "Kiểm tra kết nối thất bại", throwable)
                mutableUiState.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionErrorMessage = throwable.message ?: "Không thể kiểm tra kết nối.",
                    )
                }
            }
        }
    }

    fun saveConnection() {
        val snapshot = mutableUiState.value
        if (snapshot.isConnectionBusy || snapshot.isLoading) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSavingConnection = true,
                    connectionErrorMessage = null,
                    connectionSuccessMessage = null,
                )
            }

            sessionRepository.testConnectionConfig(
                url = snapshot.connectionUrl,
                anonKey = snapshot.anonKey,
            ).onFailure { throwable ->
                logger.error("LoginViewModel", "Kiểm tra cấu hình trước khi lưu thất bại", throwable)
                mutableUiState.update {
                    it.copy(
                        isSavingConnection = false,
                        connectionErrorMessage = throwable.message ?: "Không thể lưu cấu hình kết nối.",
                    )
                }
                return@launch
            }

            sessionRepository.saveConnectionConfig(
                url = snapshot.connectionUrl,
                anonKey = snapshot.anonKey,
            ).onSuccess {
                mutableUiState.update {
                    it.copy(
                        isSavingConnection = false,
                        connectionSuccessMessage = "Đã lưu cấu hình kết nối.",
                    )
                }
            }.onFailure { throwable ->
                logger.error("LoginViewModel", "Lưu cấu hình kết nối thất bại", throwable)
                mutableUiState.update {
                    it.copy(
                        isSavingConnection = false,
                        connectionErrorMessage = throwable.message ?: "Không thể lưu cấu hình kết nối.",
                    )
                }
            }
        }
    }

    fun submit() {
        if (mutableUiState.value.isLoading || !mutableUiState.value.connectionConfigured) {
            return
        }

        autoLoginJob?.cancel()
        autoLoginJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoading = true, loginErrorMessage = null)
            }

            sessionRepository.signIn(
                operatorCode = mutableUiState.value.operatorCode,
                password = mutableUiState.value.password,
            ).onSuccess {
                logger.info("LoginViewModel", "Đã hoàn tất đăng nhập và tải ngữ cảnh")
                mutableUiState.update {
                    it.copy(isLoading = false)
                }
            }.onFailure { throwable ->
                logger.error("LoginViewModel", "Đăng nhập thất bại", throwable)
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        loginErrorMessage = throwable.message ?: "Không thể đăng nhập.",
                    )
                }
            }
        }
    }

    private fun shouldAutoLogin(): Boolean {
        val snapshot = mutableUiState.value
        return appConfig.autoLoginOnLaunch &&
            !autoLoginAttempted &&
            !snapshot.isLoading &&
            snapshot.connectionConfigured &&
            snapshot.operatorCode.isNotBlank() &&
            snapshot.password.isNotBlank()
    }

    companion object {
        fun factory(
            appConfig: AppConfig,
            sessionRepository: SessionRepository,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(
                    appConfig = appConfig,
                    sessionRepository = sessionRepository,
                    logger = logger,
                )
            }
        }
    }
}
