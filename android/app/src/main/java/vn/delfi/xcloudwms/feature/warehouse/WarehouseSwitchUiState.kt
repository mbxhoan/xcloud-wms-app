package vn.delfi.xcloudwms.feature.warehouse

data class WarehouseItemUiState(
    val id: String,
    val label: String,
    val code: String? = null,
)

data class WarehouseSwitchUiState(
    val warehouses: List<WarehouseItemUiState> = emptyList(),
    val currentWarehouseId: String? = null,
    val isLoadingWarehouseId: String? = null,
    val errorMessage: String? = null,
    val canGoBack: Boolean = false,
)
