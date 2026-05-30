package vn.delfi.xcloudwms.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.network.NetworkClient
import vn.delfi.xcloudwms.data.session.SessionRepository

class HomeViewModel(
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository,
    private val networkClient: NetworkClient,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = sessionRepository.session.map { session ->
        HomeUiState(
            isAuthenticated = session.isAuthenticated,
            operatorName = session.displayName ?: "Chưa đăng nhập",
            warehouseLabel = session.warehouseLabel ?: "Chưa chọn",
            buildEnvironment = appConfig.buildEnvironment.uppercase(),
            baseApiUrl = appConfig.normalizedBaseApiUrl,
            networkSummary = networkClient.summary(),
            moduleShortcuts = placeholderShortcuts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            isAuthenticated = sessionRepository.session.value.isAuthenticated,
            operatorName = sessionRepository.session.value.displayName ?: "Chưa đăng nhập",
            warehouseLabel = sessionRepository.session.value.warehouseLabel ?: "Chưa chọn",
            buildEnvironment = appConfig.buildEnvironment.uppercase(),
            baseApiUrl = appConfig.normalizedBaseApiUrl,
            networkSummary = networkClient.summary(),
            moduleShortcuts = placeholderShortcuts,
        ),
    )

    fun logout() {
        viewModelScope.launch {
            sessionRepository.signOut()
        }
    }

    companion object {
        private val placeholderShortcuts = listOf(
            ModuleShortcut(title = "Nhận hàng", note = "Sẽ triển khai ở bước tiếp theo"),
            ModuleShortcut(title = "Xuất hàng", note = "Sẽ triển khai ở bước tiếp theo"),
            ModuleShortcut(title = "Sắp xếp kho", note = "Sẽ triển khai ở bước tiếp theo"),
            ModuleShortcut(title = "Kiểm kê", note = "Sẽ triển khai ở bước tiếp theo"),
            ModuleShortcut(title = "Tra cứu tồn", note = "Sẽ triển khai ở bước tiếp theo"),
        )

        fun factory(
            appConfig: AppConfig,
            sessionRepository: SessionRepository,
            networkClient: NetworkClient,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    appConfig = appConfig,
                    sessionRepository = sessionRepository,
                    networkClient = networkClient,
                )
            }
        }
    }
}
