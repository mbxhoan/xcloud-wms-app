package vn.delfi.xcloudwms.feature.home

data class ModuleShortcut(
    val title: String,
    val note: String,
)

data class HomeUiState(
    val isAuthenticated: Boolean = false,
    val operatorName: String = "",
    val tenantLabel: String = "Chưa xác định",
    val warehouseLabel: String = "Chưa chọn",
    val buildEnvironment: String = "",
    val connectionLabel: String = "Chưa cấu hình",
    val moduleShortcuts: List<ModuleShortcut> = emptyList(),
    val canSwitchWarehouse: Boolean = false,
)
