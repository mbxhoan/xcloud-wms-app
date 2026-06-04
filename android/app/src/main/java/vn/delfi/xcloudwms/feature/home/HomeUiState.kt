package vn.delfi.xcloudwms.feature.home

data class ModuleShortcut(
    val title: String,
    val note: String,
    val actionKey: String? = null,
    val actionLabel: String? = null,
)

data class HomeUiState(
    val isAuthenticated: Boolean = false,
    val operatorName: String = "",
    val roleLabels: List<String> = emptyList(),
    val tenantLabel: String = "Chưa xác định",
    val warehouseLabel: String = "Chưa chọn",
    val buildEnvironment: String = "",
    val connectionLabel: String = "Chưa cấu hình",
    val moduleShortcuts: List<ModuleShortcut> = emptyList(),
    val canSwitchWarehouse: Boolean = false,
    val deviceStatusLabel: String = "Chưa kiểm tra",
)
