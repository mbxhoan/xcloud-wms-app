package vn.delfi.xcloudwms.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.config.ConnectionConfig
import vn.delfi.xcloudwms.core.error.AppError
import vn.delfi.xcloudwms.core.error.AppException
import vn.delfi.xcloudwms.core.error.toAppError
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.data.auth.AuthContext
import vn.delfi.xcloudwms.data.auth.AuthRepository
import vn.delfi.xcloudwms.data.device.DeviceLicenseRepository
import vn.delfi.xcloudwms.domain.model.DeviceLicenseState
import vn.delfi.xcloudwms.domain.model.SessionStatus
import vn.delfi.xcloudwms.domain.model.UserSession

interface SessionRepository {
    val session: StateFlow<UserSession>

    fun currentConnectionConfig(): ConnectionConfig?

    suspend fun restoreSession()

    suspend fun signIn(
        operatorCode: String,
        password: String,
    ): Result<Unit>

    suspend fun signOut()

    suspend fun refreshDeviceLicense(force: Boolean = false): Result<Unit>

    suspend fun selectWarehouse(warehouseId: String): Result<Unit>

    suspend fun testConnectionConfig(
        url: String,
        anonKey: String,
    ): Result<Unit>

    suspend fun saveConnectionConfig(
        url: String,
        anonKey: String,
    ): Result<Unit>
}

class DefaultSessionRepository(
    private val appConfig: AppConfig,
    private val authRepository: AuthRepository,
    private val deviceLicenseRepository: DeviceLicenseRepository,
    private val logger: SafeLogger,
) : SessionRepository {
    private val mutableSession = MutableStateFlow(
        emptySession(
            connectionConfig = authRepository.getConnectionConfig(),
            status = SessionStatus.RESTORING,
        ),
    )

    override val session: StateFlow<UserSession> = mutableSession.asStateFlow()

    override fun currentConnectionConfig(): ConnectionConfig? = authRepository.getConnectionConfig()

    override suspend fun restoreSession() {
        val connectionConfig = authRepository.getConnectionConfig()
        mutableSession.value = emptySession(
            connectionConfig = connectionConfig,
            status = SessionStatus.RESTORING,
        )

        authRepository.restoreSession()
            .onSuccess { authContext ->
                if (authContext == null) {
                    mutableSession.value = emptySession(connectionConfig = connectionConfig)
                    return@onSuccess
                }

                resolveAuthenticatedSession(authContext)
                    .onSuccess { userSession ->
                        mutableSession.value = userSession
                    }
                    .onFailure { throwable ->
                        val appError = throwable.toAppError()
                        logger.error("SessionRepository", "Khôi phục phiên thất bại", throwable)
                        mutableSession.value = emptySession(
                            connectionConfig = connectionConfig,
                            errorMessage = appError.message,
                        )
                    }
            }
            .onFailure { throwable ->
                val appError = throwable.toAppError()
                logger.error("SessionRepository", "Khôi phục phiên thất bại", throwable)
                mutableSession.value = emptySession(
                    connectionConfig = connectionConfig,
                    errorMessage = appError.message,
                )
            }
    }

    override suspend fun signIn(
        operatorCode: String,
        password: String,
    ): Result<Unit> {
        return authRepository.signIn(
            identifier = operatorCode,
            password = password,
        ).fold(
            onSuccess = { authContext ->
                resolveAuthenticatedSession(authContext).map { userSession ->
                    mutableSession.value = userSession
                    logger.info(
                        "SessionRepository",
                        "Đăng nhập thành công cho user=${authContext.userId} với trạng thái ${userSession.status.name}",
                    )
                }
            },
            onFailure = { throwable ->
                Result.failure(throwable)
            },
        )
    }

    override suspend fun signOut() {
        val connectionConfig = authRepository.getConnectionConfig()
        authRepository.signOut()
        mutableSession.value = emptySession(connectionConfig = connectionConfig)
        logger.info("SessionRepository", "Đã xóa phiên đăng nhập cục bộ")
    }

    override suspend fun refreshDeviceLicense(force: Boolean): Result<Unit> {
        val currentSession = mutableSession.value
        val userId = currentSession.userId ?: return Result.success(Unit)
        val currentDeviceLicense = currentSession.deviceLicense
        val now = System.currentTimeMillis()

        if (
            !force &&
            currentDeviceLicense != null &&
            now - currentDeviceLicense.checkedAtEpochMillis < DEVICE_LICENSE_REFRESH_THROTTLE_MS
        ) {
            return Result.success(Unit)
        }

        return deviceLicenseRepository.verify(userId).fold(
            onSuccess = { deviceLicense ->
                mutableSession.value = currentSession.copy(
                    status = resolveSessionStatus(
                        allowedWarehouses = currentSession.allowedWarehouses,
                        currentWarehouse = currentSession.currentWarehouse,
                        deviceLicense = deviceLicense,
                    ),
                    deviceLicense = deviceLicense,
                    errorMessage = null,
                )
                Result.success(Unit)
            },
            onFailure = { throwable ->
                val error = throwable.toAppError()
                if (error.code == "SESSION_EXPIRED") {
                    val connectionConfig = authRepository.getConnectionConfig()
                    authRepository.signOut()
                    mutableSession.value = emptySession(connectionConfig = connectionConfig)
                }
                Result.failure(throwable)
            },
        )
    }

    override suspend fun selectWarehouse(warehouseId: String): Result<Unit> {
        val currentSession = mutableSession.value
        val userId = currentSession.userId
            ?: return failure(
                AppError(
                    code = "SESSION_REQUIRED",
                    message = "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
                ),
            )

        val selectedWarehouse = currentSession.allowedWarehouses.firstOrNull {
            it.id == warehouseId.trim()
        } ?: return failure(
            AppError(
                code = "WAREHOUSE_NOT_ALLOWED",
                message = "Kho đã chọn không nằm trong danh sách được phân quyền.",
            ),
        )

        authRepository.saveCurrentWarehouse(
            userId = userId,
            warehouseId = selectedWarehouse.id,
        )
        mutableSession.value = currentSession.copy(
            status = resolveSessionStatus(
                allowedWarehouses = currentSession.allowedWarehouses,
                currentWarehouse = selectedWarehouse,
                deviceLicense = currentSession.deviceLicense,
            ),
            currentWarehouse = selectedWarehouse,
            errorMessage = null,
        )
        return Result.success(Unit)
    }

    override suspend fun testConnectionConfig(
        url: String,
        anonKey: String,
    ): Result<Unit> {
        val connectionConfig = ConnectionConfig.create(url, anonKey)
            ?: return failure(
                AppError(
                    code = "CONNECTION_FIELDS_REQUIRED",
                    message = "Vui lòng nhập đầy đủ địa chỉ kết nối và khóa truy cập công khai.",
                ),
            )

        return authRepository.testConnection(connectionConfig)
    }

    override suspend fun saveConnectionConfig(
        url: String,
        anonKey: String,
    ): Result<Unit> {
        val connectionConfig = ConnectionConfig.create(url, anonKey)
            ?: return failure(
                AppError(
                    code = "CONNECTION_FIELDS_REQUIRED",
                    message = "Vui lòng nhập đầy đủ địa chỉ kết nối và khóa truy cập công khai.",
                ),
            )

        authRepository.saveConnectionConfig(connectionConfig)
        mutableSession.value = emptySession(connectionConfig = connectionConfig)
        return Result.success(Unit)
    }

    private suspend fun resolveAuthenticatedSession(authContext: AuthContext): Result<UserSession> {
        return deviceLicenseRepository.verify(authContext.userId).map { deviceLicense ->
            authContext.toUserSession(deviceLicense = deviceLicense)
        }
    }

    private fun AuthContext.toUserSession(deviceLicense: DeviceLicenseState): UserSession {
        return UserSession(
            status = resolveSessionStatus(
                allowedWarehouses = allowedWarehouses,
                currentWarehouse = currentWarehouse,
                deviceLicense = deviceLicense,
            ),
            userId = userId,
            email = email,
            operatorCode = operatorCode,
            displayName = displayName,
            tenant = tenant,
            currentWarehouse = currentWarehouse,
            allowedWarehouses = allowedWarehouses,
            permissions = permissions,
            roles = roles,
            deviceLicense = deviceLicense,
            buildEnvironment = appConfig.buildEnvironment,
            connectionConfigured = true,
            connectionLabel = connectionLabel,
            errorMessage = if (allowedWarehouses.isEmpty()) {
                "Bạn chưa được phân quyền kho nào."
            } else {
                null
            },
        )
    }

    private fun emptySession(
        connectionConfig: ConnectionConfig?,
        status: SessionStatus = SessionStatus.UNAUTHENTICATED,
        errorMessage: String? = null,
    ): UserSession {
        return UserSession(
            status = status,
            buildEnvironment = appConfig.buildEnvironment,
            connectionConfigured = connectionConfig != null,
            connectionLabel = connectionConfig?.hostLabel,
            errorMessage = errorMessage,
        )
    }

    private fun resolveSessionStatus(
        allowedWarehouses: List<vn.delfi.xcloudwms.domain.model.WarehouseSummary>,
        currentWarehouse: vn.delfi.xcloudwms.domain.model.WarehouseSummary?,
        deviceLicense: DeviceLicenseState?,
    ): SessionStatus {
        if (deviceLicense != null && !deviceLicense.canOperate) {
            return SessionStatus.DEVICE_LICENSE_REQUIRED
        }
        return when {
            allowedWarehouses.isEmpty() -> SessionStatus.NO_WAREHOUSE_ASSIGNED
            currentWarehouse == null -> SessionStatus.WAREHOUSE_SELECTION_REQUIRED
            else -> SessionStatus.AUTHENTICATED
        }
    }

    private fun <T> failure(error: AppError): Result<T> {
        return Result.failure(AppException(error))
    }

    private companion object {
        const val DEVICE_LICENSE_REFRESH_THROTTLE_MS = 45_000L
    }
}
