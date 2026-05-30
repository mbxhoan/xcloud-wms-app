package vn.delfi.xcloudwms.feature.warehouse

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
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.domain.model.SessionStatus

class WarehouseSwitchViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WarehouseSwitchUiState())
    val uiState: StateFlow<WarehouseSwitchUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                mutableUiState.update { current ->
                    current.copy(
                        warehouses = session.allowedWarehouses.map { warehouse ->
                            WarehouseItemUiState(
                                id = warehouse.id,
                                label = warehouse.label,
                                code = warehouse.code,
                            )
                        },
                        currentWarehouseId = session.currentWarehouse?.id,
                        canGoBack = session.status == SessionStatus.AUTHENTICATED && session.currentWarehouse != null,
                    )
                }
            }
        }
    }

    fun selectWarehouse(warehouseId: String) {
        if (mutableUiState.value.isLoadingWarehouseId != null) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoadingWarehouseId = warehouseId,
                    errorMessage = null,
                )
            }

            sessionRepository.selectWarehouse(warehouseId)
                .onSuccess {
                    mutableUiState.update {
                        it.copy(isLoadingWarehouseId = null)
                    }
                }
                .onFailure { throwable ->
                    mutableUiState.update {
                        it.copy(
                            isLoadingWarehouseId = null,
                            errorMessage = throwable.message ?: "Không thể đổi kho làm việc.",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WarehouseSwitchViewModel(
                    sessionRepository = sessionRepository,
                )
            }
        }
    }
}
