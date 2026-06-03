package vn.delfi.xcloudwms.feature.deviceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.data.device.DeviceHardwareRepository

class DeviceHardwareInfoViewModel(
    private val repository: DeviceHardwareRepository,
    private val logger: SafeLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DeviceHardwareInfoUiState())
    val uiState: StateFlow<DeviceHardwareInfoUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.loadSnapshot() }
                .onSuccess { snapshot ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            capturedAtLabel = snapshot.capturedAtLabel,
                            missingPermissionLabels = snapshot.missingPermissionLabels,
                            sections = snapshot.sections,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    logger.error(TAG, "Không thể đọc thông tin phần cứng", throwable)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Không thể đọc thông tin thiết bị. Vui lòng thử lại.",
                        )
                    }
                }
        }
    }

    companion object {
        private const val TAG = "DeviceHardwareInfoVM"

        fun factory(
            repository: DeviceHardwareRepository,
            logger: SafeLogger,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceHardwareInfoViewModel(
                    repository = repository,
                    logger = logger,
                )
            }
        }
    }
}
