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
import vn.delfi.xcloudwms.domain.model.SessionStatus
import vn.delfi.xcloudwms.domain.model.UserSession

interface SessionRepository {
    val session: StateFlow<UserSession>

    suspend fun restoreSession()

    suspend fun signIn(
        operatorCode: String,
        password: String,
    ): Result<Unit>

    suspend fun signOut()

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
    private val logger: SafeLogger,
) : SessionRepository {
    private val mutableSession = MutableStateFlow(
        emptySession(
            connectionConfig = authRepository.getConnectionConfig(),
            status = SessionStatus.RESTORING,
        ),
    )

    override val session: StateFlow<UserSession> = mutableSession.asStateFlow()

    override suspend fun restoreSession() {
        val connectionConfig = authRepository.getConnectionConfig()
        mutableSession.value = emptySession(
            connectionConfig = connectionConfig,
            status = SessionStatus.RESTORING,
        )

        authRepository.restoreSession()
            .onSuccess { authContext ->
                mutableSession.value = if (authContext == null) {
                    emptySession(connectionConfig = connectionConfig)
                } else {
                    authContext.toUserSession()
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
        ).map { authContext ->
            mutableSession.value = authContext.toUserSession()
            logger.info("SessionRepository", "Đăng nhập thành công cho user=${authContext.userId}")
        }
    }

    override suspend fun signOut() {
        val connectionConfig = authRepository.getConnectionConfig()
        authRepository.signOut()
        mutableSession.value = emptySession(connectionConfig = connectionConfig)
        logger.info("SessionRepository", "Đã xóa phiên đăng nhập cục bộ")
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
            status = SessionStatus.AUTHENTICATED,
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

    private fun AuthContext.toUserSession(): UserSession {
        val status = when {
            allowedWarehouses.isEmpty() -> SessionStatus.NO_WAREHOUSE_ASSIGNED
            currentWarehouse == null -> SessionStatus.WAREHOUSE_SELECTION_REQUIRED
            else -> SessionStatus.AUTHENTICATED
        }

        return UserSession(
            status = status,
            userId = userId,
            email = email,
            operatorCode = operatorCode,
            displayName = displayName,
            tenant = tenant,
            currentWarehouse = currentWarehouse,
            allowedWarehouses = allowedWarehouses,
            permissions = permissions,
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

    private fun <T> failure(error: AppError): Result<T> {
        return Result.failure(AppException(error))
    }
}
