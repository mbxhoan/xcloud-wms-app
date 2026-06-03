package vn.delfi.xcloudwms.data.auth

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import vn.delfi.xcloudwms.core.config.ConnectionConfig
import vn.delfi.xcloudwms.core.error.AppError
import vn.delfi.xcloudwms.core.error.AppException
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.network.HttpMethod
import vn.delfi.xcloudwms.core.network.NetworkClient
import vn.delfi.xcloudwms.core.network.NetworkRequest
import vn.delfi.xcloudwms.core.network.NetworkResponse
import vn.delfi.xcloudwms.core.network.NetworkResult
import vn.delfi.xcloudwms.core.security.SecureSessionStorage
import vn.delfi.xcloudwms.core.security.StoredAuthSession
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.domain.model.TenantSummary
import vn.delfi.xcloudwms.domain.model.WarehouseSummary

class SupabaseAuthRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : AuthRepository {
    override fun getConnectionConfig(): ConnectionConfig? = appPreferences.currentConnectionConfig()

    override suspend fun testConnection(config: ConnectionConfig): Result<Unit> {
        return when (val result = networkClient.testConnection(config)) {
            is NetworkResult.Success<*> -> Result.success(Unit)
            is NetworkResult.Failure -> failure(result.error)
        }
    }

    override suspend fun saveConnectionConfig(config: ConnectionConfig) {
        val previousSession = secureSessionStorage.loadSession()
        previousSession?.userId?.let { userId ->
            appPreferences.clearSelectedWarehouseId(userId)
        }
        appPreferences.clearAllWarehouseSelections()
        secureSessionStorage.clearSession()
        appPreferences.saveConnectionConfig(config)
    }

    override suspend fun signIn(
        identifier: String,
        password: String,
    ): Result<AuthContext> {
        val connectionConfig = getConnectionConfig()
            ?: return failure(
                AppError(
                    code = "CONNECTION_REQUIRED",
                    message = "Vui lòng lưu cấu hình kết nối trước khi đăng nhập.",
                ),
            )

        val normalizedIdentifier = identifier.trim()
        if (normalizedIdentifier.isBlank()) {
            return failure(
                AppError(
                    code = "IDENTIFIER_REQUIRED",
                    message = "Vui lòng nhập mã đăng nhập hoặc email.",
                ),
            )
        }
        if (password.isBlank()) {
            return failure(
                AppError(
                    code = "PASSWORD_REQUIRED",
                    message = "Vui lòng nhập mật khẩu.",
                ),
            )
        }

        val email = if (normalizedIdentifier.contains("@")) {
            normalizedIdentifier
        } else {
            resolveEmailByUsername(
                connectionConfig = connectionConfig,
                identifier = normalizedIdentifier,
            ).getOrElse { return Result.failure(it) }
        }

        val requestBody = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val signInResponse = requestJson(
            connectionConfig = connectionConfig,
            path = "/auth/v1/token",
            method = HttpMethod.POST,
            queryParams = mapOf("grant_type" to "password"),
            body = requestBody,
            useAnonAuthorization = true,
        ).getOrElse { return Result.failure(it) }

        if (!signInResponse.isSuccessful()) {
            return failure(mapSignInError(signInResponse))
        }

        val session = parseStoredSession(signInResponse.value)
            ?: return failure(
                AppError(
                    code = "AUTH_SESSION_INVALID",
                    message = "Không thể đọc phiên đăng nhập từ máy chủ.",
                ),
            )

        secureSessionStorage.saveSession(session)

        return loadAuthContext(
            connectionConfig = connectionConfig,
            storedSession = session,
        ).onFailure {
            secureSessionStorage.clearSession()
        }
    }

    override suspend fun restoreSession(): Result<AuthContext?> {
        val connectionConfig = getConnectionConfig() ?: return Result.success(null)
        val storedSession = secureSessionStorage.loadSession() ?: return Result.success(null)

        val activeSession = if (storedSession.expiresAtEpochSeconds <= nowEpochSeconds() + REFRESH_LEEWAY_SECONDS) {
            refreshSession(
                connectionConfig = connectionConfig,
                session = storedSession,
            ).getOrElse { throwable ->
                val error = (throwable as? AppException)?.appError
                if (error?.code == "SESSION_EXPIRED") {
                    clearLocalSession(storedSession.userId)
                    return Result.success(null)
                }
                return Result.failure(throwable)
            }
        } else {
            storedSession
        }

        val contextResult = loadAuthContext(
            connectionConfig = connectionConfig,
            storedSession = activeSession,
        )

        return contextResult.fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                val error = (throwable as? AppException)?.appError
                if (error?.code == "SESSION_EXPIRED") {
                    clearLocalSession(activeSession.userId)
                    Result.success(null)
                } else {
                    Result.failure(throwable)
                }
            },
        )
    }

    override suspend fun saveCurrentWarehouse(
        userId: String,
        warehouseId: String,
    ) {
        appPreferences.saveSelectedWarehouseId(
            userId = userId,
            warehouseId = warehouseId,
        )
    }

    override suspend fun signOut() {
        val connectionConfig = getConnectionConfig()
        val storedSession = secureSessionStorage.loadSession()

        if (connectionConfig != null && storedSession != null) {
            requestJson(
                connectionConfig = connectionConfig,
                path = "/auth/v1/logout",
                method = HttpMethod.POST,
                authToken = storedSession.accessToken,
                body = "{}",
            )
        }

        clearLocalSession(storedSession?.userId)
    }

    private suspend fun loadAuthContext(
        connectionConfig: ConnectionConfig,
        storedSession: StoredAuthSession,
    ): Result<AuthContext> {
        val currentUser = fetchCurrentUser(
            connectionConfig = connectionConfig,
            session = storedSession,
        ).getOrElse { return Result.failure(it) }

        val directoryRecords = fetchDirectoryCandidates(
            connectionConfig = connectionConfig,
            accessToken = currentUser.session.accessToken,
            user = currentUser.user,
        )

        val permissions = resolvePermissions(
            connectionConfig = connectionConfig,
            accessToken = currentUser.session.accessToken,
            user = currentUser.user,
            directoryRecords = directoryRecords,
        ).getOrElse { return Result.failure(it) }

        val warehouses = resolveAllowedWarehouses(
            connectionConfig = connectionConfig,
            accessToken = currentUser.session.accessToken,
            user = currentUser.user,
            directoryRecords = directoryRecords,
        ).getOrElse { return Result.failure(it) }

        val selectedWarehouseId = appPreferences.getSelectedWarehouseId(currentUser.user.id)
        val currentWarehouse = warehouses.firstOrNull { it.id == selectedWarehouseId }
            ?: warehouses.firstOrNull()

        if (currentWarehouse != null) {
            appPreferences.saveSelectedWarehouseId(currentUser.user.id, currentWarehouse.id)
        } else {
            appPreferences.clearSelectedWarehouseId(currentUser.user.id)
        }

        return Result.success(
            AuthContext(
                userId = currentUser.user.id,
                email = currentUser.user.email,
                operatorCode = currentUser.user.email ?: currentUser.user.id,
                displayName = extractDisplayName(
                    user = currentUser.user,
                    directoryRecords = directoryRecords,
                ),
                tenant = extractTenantSummary(
                    user = currentUser.user,
                    directoryRecords = directoryRecords,
                ),
                currentWarehouse = currentWarehouse,
                allowedWarehouses = warehouses,
                permissions = permissions,
                connectionLabel = connectionConfig.hostLabel,
            ),
        )
    }

    private suspend fun resolveEmailByUsername(
        connectionConfig: ConnectionConfig,
        identifier: String,
    ): Result<String> {
        val response = requestJson(
            connectionConfig = connectionConfig,
            path = "/rest/v1/rpc/fn_auth_email_by_username",
            method = HttpMethod.POST,
            body = JSONObject()
                .put("p_username", identifier)
                .toString(),
            useAnonAuthorization = true,
        ).getOrElse { return Result.failure(it) }

        if (!response.isSuccessful()) {
            return failure(
                AppError(
                    code = "USERNAME_RESOLVE_FAILED",
                    message = extractMessage(response.value)
                        ?: "Không thể tra cứu email đăng nhập.",
                ),
            )
        }

        val resolvedEmail = when (val value = response.value) {
            is String -> value.trim()
            is Map<*, *> -> asString(value["email"])
            else -> null
        }

        if (resolvedEmail.isNullOrBlank()) {
            return failure(
                AppError(
                    code = "USERNAME_NOT_FOUND",
                    message = "Không tìm thấy tài khoản đăng nhập.",
                ),
            )
        }

        return Result.success(resolvedEmail)
    }

    private suspend fun refreshSession(
        connectionConfig: ConnectionConfig,
        session: StoredAuthSession,
    ): Result<StoredAuthSession> {
        val response = requestJson(
            connectionConfig = connectionConfig,
            path = "/auth/v1/token",
            method = HttpMethod.POST,
            queryParams = mapOf("grant_type" to "refresh_token"),
            body = JSONObject()
                .put("refresh_token", session.refreshToken)
                .toString(),
            useAnonAuthorization = true,
        ).getOrElse { return Result.failure(it) }

        if (!response.isSuccessful()) {
            return when (response.statusCode) {
                HTTP_BAD_REQUEST,
                HTTP_UNAUTHORIZED,
                HTTP_FORBIDDEN,
                -> failure(
                    AppError(
                        code = "SESSION_EXPIRED",
                        message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                    ),
                )

                else -> failure(
                    AppError(
                        code = "SESSION_REFRESH_FAILED",
                        message = extractMessage(response.value)
                            ?: "Không thể làm mới phiên đăng nhập.",
                        retryable = true,
                    ),
                )
            }
        }

        val refreshedSession = parseStoredSession(response.value)
            ?: return failure(
                AppError(
                    code = "SESSION_REFRESH_INVALID",
                    message = "Máy chủ trả về phiên làm việc không hợp lệ.",
                ),
            )

        secureSessionStorage.saveSession(refreshedSession)
        return Result.success(refreshedSession)
    }

    private suspend fun fetchCurrentUser(
        connectionConfig: ConnectionConfig,
        session: StoredAuthSession,
    ): Result<CurrentUserEnvelope> {
        val response = requestJson(
            connectionConfig = connectionConfig,
            path = "/auth/v1/user",
            authToken = session.accessToken,
        ).getOrElse { return Result.failure(it) }

        val activeSession = if (response.statusCode == HTTP_UNAUTHORIZED) {
            refreshSession(
                connectionConfig = connectionConfig,
                session = session,
            ).getOrElse { return Result.failure(it) }
        } else {
            session
        }

        val activeResponse = if (response.statusCode == HTTP_UNAUTHORIZED) {
            requestJson(
                connectionConfig = connectionConfig,
                path = "/auth/v1/user",
                authToken = activeSession.accessToken,
            ).getOrElse { return Result.failure(it) }
        } else {
            response
        }

        if (!activeResponse.isSuccessful()) {
            return when (activeResponse.statusCode) {
                HTTP_UNAUTHORIZED -> failure(
                    AppError(
                        code = "SESSION_EXPIRED",
                        message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                    ),
                )

                HTTP_FORBIDDEN -> failure(
                    AppError(
                        code = "PERMISSION_DENIED",
                        message = "Bạn không có quyền truy cập tài khoản này.",
                    ),
                )

                else -> failure(
                    AppError(
                        code = "GET_USER_FAILED",
                        message = extractMessage(activeResponse.value)
                            ?: "Không thể tải thông tin tài khoản.",
                        retryable = true,
                    ),
                )
            }
        }

        val userRecord = asRecord(activeResponse.value)
            ?: return failure(
                AppError(
                    code = "USER_PAYLOAD_INVALID",
                    message = "Không thể đọc thông tin người dùng hiện tại.",
                ),
            )

        val authUser = parseAuthUser(userRecord)
            ?: return failure(
                AppError(
                    code = "USER_PAYLOAD_INVALID",
                    message = "Không thể đọc thông tin người dùng hiện tại.",
                ),
            )

        val normalizedSession = activeSession.copy(
            userId = authUser.id,
            email = authUser.email,
        )
        secureSessionStorage.saveSession(normalizedSession)

        return Result.success(
            CurrentUserEnvelope(
                session = normalizedSession,
                user = authUser,
            ),
        )
    }

    private suspend fun fetchDirectoryCandidates(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        user: AuthUserData,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        buildList {
            fetchDirectoryRecord(connectionConfig, accessToken, "profiles", "email", user.email)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "profiles", "id", user.id)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "profiles", "user_id", user.id)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "users", "email", user.email)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "users", "id", user.id)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "users", "user_id", user.id)?.let(::add)
            fetchDirectoryRecord(connectionConfig, accessToken, "users", "auth_user_id", user.id)?.let(::add)
        }
    }

    private suspend fun fetchDirectoryRecord(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        table: String,
        column: String,
        value: String?,
    ): Map<String, Any?>? {
        if (value.isNullOrBlank()) {
            return null
        }

        val response = requestJson(
            connectionConfig = connectionConfig,
            path = "/rest/v1/$table",
            queryParams = mapOf(
                "select" to "*",
                column to "eq.${value.trim()}",
                "limit" to "1",
            ),
            authToken = accessToken,
        ).getOrNull() ?: return null

        if (!response.isSuccessful()) {
            return null
        }

        return asRecordList(response.value).firstOrNull()
    }

    private suspend fun resolvePermissions(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        user: AuthUserData,
        directoryRecords: List<Map<String, Any?>>,
    ): Result<Set<String>> {
        val principalIds = linkedSetOf(user.id).apply {
            addAll(extractPrincipalIds(directoryRecords))
        }.toList()

        val metadataPermissions = extractPermissionsFromMetadata(user).toMutableSet()

        val userRoles = fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "user_roles",
            filters = mapOf("user_id" to inFilter(principalIds)),
        )
        val roleIds = extractRoleIds(userRoles)

        val fromUserPermissions = fetchCodesFromMaybeJoinTable(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "user_permissions",
            filterColumn = "user_id",
            filterValues = principalIds,
        )
        val fromRolePermissions = fetchCodesFromMaybeJoinTable(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "role_permissions",
            filterColumn = "role_id",
            filterValues = roleIds,
        )

        val permissionIdsToResolve = linkedSetOf<String>().apply {
            addAll(fromUserPermissions.permissionIds)
            addAll(fromRolePermissions.permissionIds)
        }

        val resolvedByIds = if (permissionIdsToResolve.isEmpty()) {
            emptySet()
        } else {
            fetchPermissionCodesByIds(
                connectionConfig = connectionConfig,
                accessToken = accessToken,
                permissionIds = permissionIdsToResolve.toList(),
            )
        }

        metadataPermissions += fromUserPermissions.codes
        metadataPermissions += fromRolePermissions.codes
        metadataPermissions += resolvedByIds

        return Result.success(metadataPermissions)
    }

    private suspend fun resolveAllowedWarehouses(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        user: AuthUserData,
        directoryRecords: List<Map<String, Any?>>,
    ): Result<List<WarehouseSummary>> {
        val principalIds = linkedSetOf(user.id).apply {
            addAll(extractPrincipalIds(directoryRecords))
        }.toList()

        val assignmentRows = fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "user_warehouses",
            filters = mapOf("user_id" to inFilter(principalIds)),
            select = "warehouse_id,warehouses:warehouse_id(id,code,name,is_active,deleted_at)",
        )

        val warehousesFromAssignments = assignmentRows.flatMap { row ->
            val embeddedWarehouse = asRecord(row["warehouses"])
            val directWarehouse = asRecord(row["warehouse"])
            listOfNotNull(
                parseWarehouseSummary(embeddedWarehouse),
                parseWarehouseSummary(directWarehouse),
            )
        }

        if (warehousesFromAssignments.isNotEmpty()) {
            return Result.success(warehousesFromAssignments.distinctBy { it.id })
        }

        val assignmentWarehouseIds = assignmentRows.mapNotNull { normalizeId(it["warehouse_id"]) }
        if (assignmentWarehouseIds.isNotEmpty()) {
            return Result.success(
                fetchWarehousesByIds(
                    connectionConfig = connectionConfig,
                    accessToken = accessToken,
                    warehouseIds = assignmentWarehouseIds,
                ),
            )
        }

        val explicitScope = mergeWarehouseScopes(
            listOf(
                extractWarehouseScopeFromUser(user),
                mergeWarehouseScopes(directoryRecords.map(::extractWarehouseScopeFromRecord)),
            ),
        )

        val scopedWarehouses = when {
            explicitScope.warehouseIds.isNotEmpty() -> fetchWarehousesByIds(
                connectionConfig = connectionConfig,
                accessToken = accessToken,
                warehouseIds = explicitScope.warehouseIds.toList(),
            )

            explicitScope.warehouseCodes.isNotEmpty() -> fetchWarehousesByCodes(
                connectionConfig = connectionConfig,
                accessToken = accessToken,
                warehouseCodes = explicitScope.warehouseCodes.toList(),
            )

            else -> emptyList()
        }

        return Result.success(scopedWarehouses)
    }

    private suspend fun fetchPermissionCodesByIds(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        permissionIds: List<String>,
    ): Set<String> {
        val rows = fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "permissions",
            filters = mapOf("id" to inFilter(permissionIds)),
        )

        return rows.flatMap(::extractPermissionCodesFromRecord).toSet()
    }

    private suspend fun fetchCodesFromMaybeJoinTable(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        table: String,
        filterColumn: String,
        filterValues: List<String>,
    ): PermissionLookupResult {
        if (filterValues.isEmpty()) {
            return PermissionLookupResult()
        }

        val rows = fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = table,
            filters = mapOf(filterColumn to inFilter(filterValues)),
        )

        return PermissionLookupResult(
            codes = rows.flatMap(::extractPermissionCodesFromRecord).toSet(),
            permissionIds = extractPermissionIds(rows),
        )
    }

    private suspend fun fetchTableRows(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        table: String,
        filters: Map<String, String>,
        select: String = "*",
    ): List<Map<String, Any?>> {
        val response = requestJson(
            connectionConfig = connectionConfig,
            path = "/rest/v1/$table",
            queryParams = buildMap {
                put("select", select)
                putAll(filters)
            },
            authToken = accessToken,
        ).getOrNull() ?: return emptyList()

        if (!response.isSuccessful()) {
            return emptyList()
        }

        return asRecordList(response.value)
    }

    private suspend fun fetchWarehousesByIds(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        warehouseIds: List<String>,
    ): List<WarehouseSummary> {
        if (warehouseIds.isEmpty()) {
            return emptyList()
        }
        return fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "warehouses",
            filters = mapOf(
                "id" to inFilter(warehouseIds),
                "is_active" to "eq.true",
                "deleted_at" to "is.null",
            ),
        ).mapNotNull(::parseWarehouseSummary)
            .distinctBy { it.id }
    }

    private suspend fun fetchWarehousesByCodes(
        connectionConfig: ConnectionConfig,
        accessToken: String,
        warehouseCodes: List<String>,
    ): List<WarehouseSummary> {
        if (warehouseCodes.isEmpty()) {
            return emptyList()
        }
        return fetchTableRows(
            connectionConfig = connectionConfig,
            accessToken = accessToken,
            table = "warehouses",
            filters = mapOf(
                "code" to inFilter(warehouseCodes.map { it.uppercase() }),
                "is_active" to "eq.true",
                "deleted_at" to "is.null",
            ),
        ).mapNotNull(::parseWarehouseSummary)
            .distinctBy { it.id }
    }

    private suspend fun requestJson(
        connectionConfig: ConnectionConfig,
        path: String,
        method: HttpMethod = HttpMethod.GET,
        queryParams: Map<String, String> = emptyMap(),
        body: String? = null,
        authToken: String? = null,
        useAnonAuthorization: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): Result<ParsedResponse> {
        return when (
            val response = networkClient.execute(
                NetworkRequest(
                    connectionConfig = connectionConfig,
                    path = path,
                    method = method,
                    queryParams = queryParams,
                    headers = headers,
                    body = body,
                    authToken = authToken,
                    useAnonAuthorization = useAnonAuthorization,
                ),
            )
        ) {
            is NetworkResult.Success<*> -> {
                val networkResponse = response.data as NetworkResponse
                Result.success(
                    ParsedResponse(
                        statusCode = networkResponse.statusCode,
                        rawBody = networkResponse.body,
                        value = parseJsonValue(networkResponse),
                    ),
                )
            }

            is NetworkResult.Failure -> failure(response.error)
        }
    }

    private fun parseJsonValue(response: NetworkResponse): Any? {
        val rawBody = response.body?.trim()
        if (rawBody.isNullOrBlank()) {
            return null
        }
        return runCatching {
            JSONTokener(rawBody).nextValue().toPlainValue()
        }.getOrElse {
            logger.error("SupabaseAuthRepository", "Không thể phân tích phản hồi JSON", it)
            rawBody
        }
    }

    private fun parseStoredSession(value: Any?): StoredAuthSession? {
        val record = asRecord(value) ?: return null
        val accessToken = asString(record["access_token"]) ?: return null
        val refreshToken = asString(record["refresh_token"]) ?: return null
        val expiresAt = asLong(record["expires_at"])
            ?: asLong(record["expires_in"])?.let { nowEpochSeconds() + it }
            ?: return null
        val tokenType = asString(record["token_type"]) ?: "bearer"
        val user = parseAuthUser(asRecord(record["user"]))
        return StoredAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
            tokenType = tokenType,
            userId = user?.id,
            email = user?.email,
        )
    }

    private fun parseAuthUser(record: Map<String, Any?>?): AuthUserData? {
        record ?: return null
        val id = asString(record["id"]) ?: return null
        return AuthUserData(
            id = id,
            email = asString(record["email"]),
            appMetadata = asRecord(record["app_metadata"]).orEmpty(),
            userMetadata = asRecord(record["user_metadata"]).orEmpty(),
        )
    }

    private fun parseWarehouseSummary(record: Map<String, Any?>?): WarehouseSummary? {
        record ?: return null
        val id = normalizeId(record["id"]) ?: return null
        val isActive = asBoolean(record["is_active"]) ?: true
        val deletedAt = record["deleted_at"]
        if (!isActive || deletedAt != null) {
            return null
        }
        return WarehouseSummary(
            id = id,
            code = asString(record["code"]),
            name = asString(record["name"]),
        )
    }

    private fun mapSignInError(response: ParsedResponse): AppError {
        val message = extractMessage(response.value).orEmpty()
        val loweredMessage = message.lowercase()
        return when {
            loweredMessage.contains("invalid login credentials") -> AppError(
                code = "INVALID_CREDENTIALS",
                message = "Sai tài khoản hoặc mật khẩu.",
            )

            loweredMessage.contains("email not confirmed") -> AppError(
                code = "EMAIL_NOT_CONFIRMED",
                message = "Tài khoản chưa xác nhận email.",
            )

            response.statusCode == HTTP_UNAUTHORIZED || response.statusCode == HTTP_FORBIDDEN -> AppError(
                code = "INVALID_CREDENTIALS",
                message = "Sai tài khoản hoặc mật khẩu.",
            )

            else -> AppError(
                code = "LOGIN_FAILED",
                message = message.ifBlank { "Không thể đăng nhập vào hệ thống." },
            )
        }
    }

    private fun extractDisplayName(
        user: AuthUserData,
        directoryRecords: List<Map<String, Any?>>,
    ): String {
        val candidates = listOfNotNull(
            asString(user.userMetadata["full_name"]),
            asString(user.userMetadata["name"]),
            directoryRecords.firstNotNullOfOrNull { asString(it["full_name"]) },
            directoryRecords.firstNotNullOfOrNull { asString(it["name"]) },
            user.email?.substringBefore("@"),
            user.id,
        )
        return candidates.firstOrNull { it.isNotBlank() } ?: "Người dùng"
    }

    private fun extractTenantSummary(
        user: AuthUserData,
        directoryRecords: List<Map<String, Any?>>,
    ): TenantSummary? {
        val fromUser = extractTenantCandidate(
            listOf(user.appMetadata, user.userMetadata),
        )
        val fromDirectory = extractTenantCandidate(directoryRecords)
        val tenantId = fromUser.id ?: fromDirectory.id
        val tenantCode = fromUser.code ?: fromDirectory.code
        val tenantName = fromUser.name ?: fromDirectory.name

        return if (tenantId == null && tenantCode == null && tenantName == null) {
            null
        } else {
            TenantSummary(
                id = tenantId,
                code = tenantCode,
                name = tenantName,
            )
        }
    }

    private fun extractTenantCandidate(records: List<Map<String, Any?>>): TenantCandidate {
        records.forEach { record ->
            val tenantId = normalizeId(
                record["tenant_id"] ?: record["tenantId"] ?: record["tenant"],
            )
            val tenantCode = asString(record["tenant_code"] ?: record["tenantCode"])
            val tenantName = asString(
                record["tenant_name"] ?: record["tenantName"] ?: record["name"],
            )
            if (tenantId != null || tenantCode != null || tenantName != null) {
                return TenantCandidate(
                    id = tenantId,
                    code = tenantCode,
                    name = tenantName,
                )
            }
        }
        return TenantCandidate()
    }

    private fun extractPermissionsFromMetadata(user: AuthUserData): Set<String> {
        return linkedSetOf<String>().apply {
            addAll(extractPermissionCodesFromRecord(user.appMetadata))
            addAll(extractPermissionCodesFromRecord(user.userMetadata))
        }
    }

    private fun extractPermissionCodesFromRecord(record: Map<String, Any?>?): List<String> {
        record ?: return emptyList()
        return normalizeCodeSet(
            listOf(
                record["code"],
                record["permission"],
                record["permissions"],
                record["permission_code"],
                record["permission_codes"],
            ).flatMap(::normalizeCode),
        )
    }

    private fun extractPermissionIds(records: List<Map<String, Any?>>): Set<String> {
        return records.flatMap { record ->
            listOf(
                record["permission_id"],
                record["permissions_id"],
                record["perm_id"],
                record["permissionId"],
            ).mapNotNull(::normalizeId)
        }.toSet()
    }

    private fun extractRoleIds(records: List<Map<String, Any?>>): List<String> {
        return records.flatMap { record ->
            listOf(
                record["role_id"],
                record["roles_id"],
                record["role_ic"],
                record["roleId"],
                record["rolesId"],
            ).mapNotNull(::normalizeId)
        }.distinct()
    }

    private fun extractPrincipalIds(records: List<Map<String, Any?>>): List<String> {
        return records.flatMap { record ->
            listOf(
                record["id"],
                record["user_id"],
                record["profile_id"],
                record["auth_user_id"],
            ).mapNotNull(::normalizeId)
        }.distinct()
    }

    private fun normalizeCode(value: Any?, keyHint: String = ""): List<String> {
        if (value == null) {
            return emptyList()
        }

        return when (value) {
            is List<*> -> value.flatMap { normalizeCode(it, keyHint) }
            is String -> value.split(',', ' ', '\n', '\t')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            is Map<*, *> -> {
                val record = value as Map<String, Any?>
                val loweredHint = keyHint.lowercase()
                val output = mutableListOf<String>()
                if (loweredHint.contains("permission") && record["code"] is String) {
                    output += normalizeCode(record["code"], "code")
                }
                output += normalizeCode(record["permission"], "permission")
                output += normalizeCode(record["permissions"], "permissions")

                val values = record.values
                val isBooleanMap = values.isNotEmpty() && values.all { it is Boolean }
                if (isBooleanMap) {
                    output += record.entries
                        .filter { it.value == true }
                        .map { it.key.trim() }
                        .filter { it.isNotBlank() }
                }
                output
            }

            else -> emptyList()
        }
    }

    private fun normalizeCodeSet(values: List<String>): List<String> {
        return values.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun extractWarehouseScopeFromUser(user: AuthUserData): WarehouseScope {
        return mergeWarehouseScopes(
            listOf(
                extractWarehouseScopeFromRecord(user.appMetadata),
                extractWarehouseScopeFromRecord(user.userMetadata),
            ),
        )
    }

    private fun extractWarehouseScopeFromRecord(record: Map<String, Any?>?): WarehouseScope {
        record ?: return WarehouseScope()
        val ids = listOf(
            record["warehouse_id"],
            record["warehouseId"],
            record["warehouse_ids"],
            record["warehouseIds"],
            record["current_warehouse_id"],
            record["currentWarehouseId"],
            record["current_warehouse_ids"],
            record["currentWarehouseIds"],
            record["assigned_warehouse_id"],
            record["assignedWarehouseId"],
            record["assigned_warehouse_ids"],
            record["assignedWarehouseIds"],
            record["allowed_warehouse_id"],
            record["allowedWarehouseId"],
            record["allowed_warehouse_ids"],
            record["allowedWarehouseIds"],
            record["warehouse"],
            record["warehouses"],
            record["current_warehouse"],
            record["current_warehouses"],
            record["allowed_warehouse"],
            record["allowed_warehouses"],
            record["assigned_warehouse"],
            record["assigned_warehouses"],
        ).flatMap { normalizeWarehouseIdList(it) }

        val codes = listOf(
            record["warehouse_code"],
            record["warehouseCode"],
            record["warehouse_codes"],
            record["warehouseCodes"],
            record["current_warehouse_code"],
            record["currentWarehouseCode"],
            record["current_warehouse_codes"],
            record["currentWarehouseCodes"],
            record["assigned_warehouse_code"],
            record["assignedWarehouseCode"],
            record["assigned_warehouse_codes"],
            record["assignedWarehouseCodes"],
            record["allowed_warehouse_code"],
            record["allowedWarehouseCode"],
            record["allowed_warehouse_codes"],
            record["allowedWarehouseCodes"],
            record["warehouse"],
            record["warehouses"],
            record["current_warehouse"],
            record["current_warehouses"],
            record["allowed_warehouse"],
            record["allowed_warehouses"],
            record["assigned_warehouse"],
            record["assigned_warehouses"],
        ).flatMap { normalizeWarehouseCodeList(it) }

        return WarehouseScope(
            warehouseIds = ids.toSet(),
            warehouseCodes = codes.map { it.lowercase() }.toSet(),
            hasExplicitScope = ids.isNotEmpty() || codes.isNotEmpty(),
        )
    }

    private fun mergeWarehouseScopes(scopes: List<WarehouseScope>): WarehouseScope {
        return WarehouseScope(
            warehouseIds = scopes.flatMap { it.warehouseIds }.toSet(),
            warehouseCodes = scopes.flatMap { it.warehouseCodes }.toSet(),
            hasExplicitScope = scopes.any { it.hasExplicitScope },
        )
    }

    private fun normalizeWarehouseIdList(value: Any?): List<String> {
        return when (value) {
            null -> emptyList()
            is List<*> -> value.flatMap(::normalizeWarehouseIdList)
            is Map<*, *> -> {
                val record = value as Map<String, Any?>
                listOf(
                    record["id"],
                    record["warehouse_id"],
                    record["warehouseId"],
                    record["warehouse_ids"],
                    record["warehouseIds"],
                    record["current_warehouse_id"],
                    record["currentWarehouseId"],
                    record["allowed_warehouse_id"],
                    record["allowedWarehouseId"],
                    record["assigned_warehouse_id"],
                    record["assignedWarehouseId"],
                    record["warehouse"],
                    record["warehouses"],
                    record["current_warehouse"],
                    record["current_warehouses"],
                    record["allowed_warehouse"],
                    record["allowed_warehouses"],
                    record["assigned_warehouse"],
                    record["assigned_warehouses"],
                ).flatMap(::normalizeWarehouseIdList)
            }

            else -> listOfNotNull(normalizeId(value))
        }
    }

    private fun normalizeWarehouseCodeList(value: Any?): List<String> {
        return when (value) {
            null -> emptyList()
            is List<*> -> value.flatMap(::normalizeWarehouseCodeList)
            is Map<*, *> -> {
                val record = value as Map<String, Any?>
                listOf(
                    record["code"],
                    record["warehouse_code"],
                    record["warehouseCode"],
                    record["warehouse_codes"],
                    record["warehouseCodes"],
                    record["current_warehouse_code"],
                    record["currentWarehouseCode"],
                    record["allowed_warehouse_code"],
                    record["allowedWarehouseCode"],
                    record["assigned_warehouse_code"],
                    record["assignedWarehouseCode"],
                    record["warehouse"],
                    record["warehouses"],
                    record["current_warehouse"],
                    record["current_warehouses"],
                    record["allowed_warehouse"],
                    record["allowed_warehouses"],
                    record["assigned_warehouse"],
                    record["assigned_warehouses"],
                ).flatMap(::normalizeWarehouseCodeList)
            }

            is String -> value.split(',', ' ', '\n', '\t')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            else -> emptyList()
        }
    }

    private fun normalizeId(value: Any?): String? {
        return when (value) {
            is Number -> value.toLong().toString()
            is String -> value.trim().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun extractMessage(value: Any?): String? {
        val record = asRecord(value) ?: return asString(value)
        return asString(record["message"])
            ?: asString(record["msg"])
            ?: asString(record["error_description"])
            ?: asString(record["error"])
    }

    private fun asString(value: Any?): String? {
        return when (value) {
            is String -> value.trim().takeIf { it.isNotBlank() }
            is Number -> value.toString()
            else -> null
        }
    }

    private fun asLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }

    private fun asBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }

            else -> null
        }
    }

    private fun asRecord(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    private fun asRecordList(value: Any?): List<Map<String, Any?>> {
        return (value as? List<*>)?.mapNotNull { asRecord(it) }.orEmpty()
    }

    private fun Any.toPlainValue(): Any? {
        return when (this) {
            is JSONObject -> {
                val output = linkedMapOf<String, Any?>()
                val iterator = keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    output[key] = opt(key).toPlainValueOrNull()
                }
                output
            }

            is JSONArray -> buildList {
                for (index in 0 until length()) {
                    add(opt(index).toPlainValueOrNull())
                }
            }

            else -> this
        }
    }

    private fun Any?.toPlainValueOrNull(): Any? {
        return when (this) {
            null,
            JSONObject.NULL,
            -> null

            is JSONObject,
            is JSONArray,
            -> (this as Any).toPlainValue()

            else -> this
        }
    }

    private fun inFilter(values: List<String>): String {
        return "in.(${values.joinToString(",") { it.trim() }})"
    }

    private fun clearLocalSession(userId: String?) {
        userId?.let { safeUserId ->
            appPreferences.clearSelectedWarehouseId(safeUserId)
        }
        secureSessionStorage.clearSession()
    }

    private fun nowEpochSeconds(): Long = Instant.now().epochSecond

    private fun <T> failure(error: AppError): Result<T> {
        return Result.failure(AppException(error))
    }

    private data class ParsedResponse(
        val statusCode: Int,
        val rawBody: String?,
        val value: Any?,
    ) {
        fun isSuccessful(): Boolean = statusCode in HTTP_OK..HTTP_SUCCESS_MAX
    }

    private data class AuthUserData(
        val id: String,
        val email: String?,
        val appMetadata: Map<String, Any?>,
        val userMetadata: Map<String, Any?>,
    )

    private data class CurrentUserEnvelope(
        val session: StoredAuthSession,
        val user: AuthUserData,
    )

    private data class PermissionLookupResult(
        val codes: Set<String> = emptySet(),
        val permissionIds: Set<String> = emptySet(),
    )

    private data class WarehouseScope(
        val warehouseIds: Set<String> = emptySet(),
        val warehouseCodes: Set<String> = emptySet(),
        val hasExplicitScope: Boolean = false,
    )

    private data class TenantCandidate(
        val id: String? = null,
        val code: String? = null,
        val name: String? = null,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val REFRESH_LEEWAY_SECONDS = 60L
    }
}
