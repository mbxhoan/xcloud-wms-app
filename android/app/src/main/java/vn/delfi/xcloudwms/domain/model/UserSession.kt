package vn.delfi.xcloudwms.domain.model

enum class SessionStatus {
    RESTORING,
    UNAUTHENTICATED,
    DEVICE_LICENSE_REQUIRED,
    WAREHOUSE_SELECTION_REQUIRED,
    AUTHENTICATED,
    NO_WAREHOUSE_ASSIGNED,
}

data class TenantSummary(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null,
) {
    val label: String
        get() = name ?: code ?: id ?: "Chưa xác định"
}

data class WarehouseSummary(
    val id: String,
    val code: String? = null,
    val name: String? = null,
) {
    val label: String
        get() = name ?: code ?: id
}

data class UserSession(
    val status: SessionStatus = SessionStatus.RESTORING,
    val userId: String? = null,
    val email: String? = null,
    val operatorCode: String? = null,
    val displayName: String? = null,
    val tenant: TenantSummary? = null,
    val currentWarehouse: WarehouseSummary? = null,
    val allowedWarehouses: List<WarehouseSummary> = emptyList(),
    val permissions: Set<String> = emptySet(),
    val roles: List<String> = emptyList(),
    val deviceLicense: DeviceLicenseState? = null,
    val buildEnvironment: String = "dev",
    val connectionConfigured: Boolean = false,
    val connectionLabel: String? = null,
    val errorMessage: String? = null,
) {
    val isAuthenticated: Boolean
        get() = status == SessionStatus.AUTHENTICATED ||
            status == SessionStatus.DEVICE_LICENSE_REQUIRED ||
            status == SessionStatus.WAREHOUSE_SELECTION_REQUIRED ||
            status == SessionStatus.NO_WAREHOUSE_ASSIGNED
}
