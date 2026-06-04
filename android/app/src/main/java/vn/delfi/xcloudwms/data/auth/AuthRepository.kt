package vn.delfi.xcloudwms.data.auth

import vn.delfi.xcloudwms.core.config.ConnectionConfig
import vn.delfi.xcloudwms.domain.model.TenantSummary
import vn.delfi.xcloudwms.domain.model.WarehouseSummary

data class AuthContext(
    val userId: String,
    val email: String?,
    val operatorCode: String,
    val displayName: String,
    val tenant: TenantSummary?,
    val currentWarehouse: WarehouseSummary?,
    val allowedWarehouses: List<WarehouseSummary>,
    val permissions: Set<String>,
    val roles: List<String> = emptyList(),
    val connectionLabel: String,
)

interface AuthRepository {
    fun getConnectionConfig(): ConnectionConfig?

    suspend fun testConnection(config: ConnectionConfig): Result<Unit>

    suspend fun saveConnectionConfig(config: ConnectionConfig)

    suspend fun signIn(
        identifier: String,
        password: String,
    ): Result<AuthContext>

    suspend fun restoreSession(): Result<AuthContext?>

    suspend fun saveCurrentWarehouse(
        userId: String,
        warehouseId: String,
    )

    suspend fun signOut()
}
