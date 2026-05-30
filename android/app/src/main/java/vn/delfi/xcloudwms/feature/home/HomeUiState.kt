package vn.delfi.xcloudwms.feature.home

data class ModuleShortcut(
    val title: String,
    val note: String,
)

data class HomeUiState(
    val isAuthenticated: Boolean = true,
    val operatorName: String = "",
    val warehouseLabel: String = "Chưa chọn",
    val buildEnvironment: String = "",
    val baseApiUrl: String = "",
    val networkSummary: String = "",
    val moduleShortcuts: List<ModuleShortcut> = emptyList(),
)
