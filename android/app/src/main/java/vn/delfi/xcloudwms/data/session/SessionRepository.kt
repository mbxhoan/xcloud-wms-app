package vn.delfi.xcloudwms.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.domain.model.UserSession

interface SessionRepository {
    val session: StateFlow<UserSession>

    suspend fun signIn(
        operatorCode: String,
        password: String,
    ): Result<Unit>

    suspend fun signOut()
}

class InMemorySessionRepository(
    private val appConfig: AppConfig,
    private val logger: SafeLogger,
) : SessionRepository {
    private val mutableSession = MutableStateFlow(
        UserSession(buildEnvironment = appConfig.buildEnvironment),
    )

    override val session: StateFlow<UserSession> = mutableSession.asStateFlow()

    override suspend fun signIn(
        operatorCode: String,
        password: String,
    ): Result<Unit> {
        val normalizedOperatorCode = operatorCode.trim()
        if (normalizedOperatorCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mã đăng nhập."))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mật khẩu."))
        }

        mutableSession.value = UserSession(
            isAuthenticated = true,
            operatorCode = normalizedOperatorCode,
            displayName = normalizedOperatorCode,
            warehouseLabel = null,
            placeholderMode = true,
            buildEnvironment = appConfig.buildEnvironment,
        )
        logger.info(
            "SessionRepository",
            "Started placeholder session for operator=$normalizedOperatorCode env=${appConfig.buildEnvironment}",
        )
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        logger.info("SessionRepository", "Cleared placeholder session")
        mutableSession.value = UserSession(buildEnvironment = appConfig.buildEnvironment)
    }
}
