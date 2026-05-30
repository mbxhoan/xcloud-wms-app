package vn.delfi.xcloudwms.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.data.session.SessionRepository

class LoginViewModel(
    private val sessionRepository: SessionRepository,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    fun updateOperatorCode(value: String) {
        mutableUiState.update {
            it.copy(operatorCode = value, errorMessage = null)
        }
    }

    fun updatePassword(value: String) {
        mutableUiState.update {
            it.copy(password = value, errorMessage = null)
        }
    }

    fun submit() {
        if (mutableUiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoading = true, errorMessage = null)
            }

            sessionRepository.signIn(
                operatorCode = mutableUiState.value.operatorCode,
                password = mutableUiState.value.password,
            ).onSuccess {
                logger.info("LoginViewModel", "Entered placeholder home screen")
                mutableUiState.update {
                    it.copy(isLoading = false, isAuthenticated = true)
                }
            }.onFailure { throwable ->
                logger.error("LoginViewModel", "Placeholder login failed", throwable)
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Không thể tiếp tục.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            sessionRepository: SessionRepository,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(
                    sessionRepository = sessionRepository,
                    logger = logger,
                )
            }
        }
    }
}
