package vn.delfi.xcloudwms.domain.model

data class UserSession(
    val isAuthenticated: Boolean = false,
    val operatorCode: String? = null,
    val displayName: String? = null,
    val warehouseLabel: String? = null,
    val placeholderMode: Boolean = true,
    val buildEnvironment: String = "dev",
)
