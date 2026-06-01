package vn.delfi.xcloudwms.data.putaway

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
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.domain.model.PaDraftLine
import vn.delfi.xcloudwms.domain.model.PaLiveStock
import vn.delfi.xcloudwms.domain.model.PaLocation
import vn.delfi.xcloudwms.domain.model.PaProduct
import vn.delfi.xcloudwms.domain.model.PaResolvedCode
import vn.delfi.xcloudwms.domain.model.PaSession
import vn.delfi.xcloudwms.domain.model.PaSessionStatus
import vn.delfi.xcloudwms.domain.model.PaTrackingType

data class PaAddLineRequest(
    val headerId: String,
    val fromLocationId: String,
    val toLocationId: String,
    val productId: String,
    val quantity: Double,
    val uomId: String?,
    val lotId: String?,
    val serialId: String?,
    val notes: String?,
)

interface PutawayRepository {
    suspend fun loadLocations(warehouseId: String): Result<List<PaLocation>>

    suspend fun loadProducts(warehouseId: String): Result<List<PaProduct>>

    /** Lấy phiên DRAFT gần nhất của kho để tiếp tục (nếu có). */
    suspend fun loadActiveDraft(warehouseId: String): Result<PaSession?>

    suspend fun loadDraftLines(headerId: String): Result<List<PaDraftLine>>

    suspend fun startSession(warehouseId: String, notes: String): Result<PaSession>

    /**
     * Phân giải mã quét (serial/lot) tại vị trí nguồn. [trackingHint] giúp giới hạn:
     * SERIAL → chỉ serial, LOT → chỉ lot, null → thử serial rồi lot.
     */
    suspend fun resolveScannedCode(
        warehouseId: String,
        fromLocationId: String,
        code: String,
        productId: String?,
        trackingHint: PaTrackingType?,
    ): PaResolvedCode

    suspend fun liveStockCheck(
        warehouseId: String,
        fromLocationId: String,
        productId: String,
        lotId: String?,
        serialId: String?,
    ): Result<PaLiveStock>

    suspend fun addLine(request: PaAddLineRequest): Result<Unit>

    suspend fun deleteLine(detailId: String): Result<Unit>

    suspend fun submit(headerId: String): Result<Unit>
}

class DefaultPutawayRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : PutawayRepository {

    override suspend fun loadLocations(warehouseId: String): Result<List<PaLocation>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("PA_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        return restGet(
            table = "locations",
            queryParams = mapOf(
                "select" to "id,code,name",
                "warehouse_id" to "eq.$warehouseIdInt",
                "is_active" to "eq.true",
                "deleted_at" to "is.null",
                "order" to "code.asc",
                "limit" to "500",
            ),
        ).map { rows ->
            rows.mapNotNull { row ->
                val id = row.optIdString("id") ?: return@mapNotNull null
                PaLocation(
                    id = id,
                    code = row.optStringOrNull("code") ?: id,
                    name = row.optStringOrNull("name"),
                )
            }
        }
    }

    override suspend fun loadProducts(warehouseId: String): Result<List<PaProduct>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("PA_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        return rpc("rpc_pa_list_products", JSONObject().put("p_warehouse_id", warehouseIdInt))
            .map { body -> parseProducts(body) }
    }

    override suspend fun loadActiveDraft(warehouseId: String): Result<PaSession?> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("PA_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        return restGet(
            table = "pa_headers",
            queryParams = mapOf(
                "select" to PA_HEADER_SELECT,
                "warehouse_id" to "eq.$warehouseIdInt",
                "order" to "updated_at.desc",
                "limit" to "200",
            ),
        ).map { rows ->
            rows.asSequence()
                .map(::parseSession)
                .firstOrNull { it != null && it.status == PaSessionStatus.DRAFT }
        }
    }

    override suspend fun loadDraftLines(headerId: String): Result<List<PaDraftLine>> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("PA_HEADER_INVALID", "ID phiên PA không hợp lệ.")
        return restGet(
            table = "pa_details",
            queryParams = mapOf(
                "select" to PA_DETAIL_SELECT,
                "pa_header_id" to "eq.$headerIdInt",
                "order" to "created_at.desc",
            ),
        ).map { rows -> rows.mapNotNull(::parseDraftLine) }
    }

    override suspend fun startSession(warehouseId: String, notes: String): Result<PaSession> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("PA_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        val body = JSONObject()
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_notes", notes.trim())
        return rpc("rpc_pa_start_session", body).mapCatching { responseBody ->
            parseSession(unwrapObject(responseBody))
                ?: throw AppException(
                    AppError(
                        code = "PA_START_NO_HEADER",
                        message = "Không thể xác định phiên vừa tạo từ phản hồi (thiếu pa_header_id).",
                    ),
                )
        }
    }

    override suspend fun resolveScannedCode(
        warehouseId: String,
        fromLocationId: String,
        code: String,
        productId: String?,
        trackingHint: PaTrackingType?,
    ): PaResolvedCode {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.NOT_FOUND, "Vui lòng quét serial/lot.")
        }
        val warehouseIdInt = warehouseId.toIntId()
        val fromLocationIdInt = fromLocationId.toIntId()
        if (warehouseIdInt == null || fromLocationIdInt == null) {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.QUERY_ERROR, "Thiếu kho hoặc vị trí nguồn hợp lệ.")
        }

        return when (trackingHint) {
            PaTrackingType.SERIAL -> resolveSerial(warehouseIdInt, fromLocationIdInt, trimmed, productId)
            PaTrackingType.LOT -> resolveLot(warehouseIdInt, fromLocationIdInt, trimmed, productId)
            else -> {
                val serial = resolveSerial(warehouseIdInt, fromLocationIdInt, trimmed, productId)
                if (serial is PaResolvedCode.Ok) return serial
                if (serial is PaResolvedCode.Fail && serial.reason == PaResolvedCode.Reason.QUERY_ERROR) return serial

                val lot = resolveLot(warehouseIdInt, fromLocationIdInt, trimmed, productId)
                if (lot is PaResolvedCode.Ok) return lot
                if (lot is PaResolvedCode.Fail && lot.reason == PaResolvedCode.Reason.QUERY_ERROR) return lot

                val serialNotInSource = serial is PaResolvedCode.Fail && serial.reason == PaResolvedCode.Reason.NOT_IN_SOURCE
                val lotNotInSource = lot is PaResolvedCode.Fail && lot.reason == PaResolvedCode.Reason.NOT_IN_SOURCE
                if (serialNotInSource || lotNotInSource) {
                    PaResolvedCode.Fail(
                        PaResolvedCode.Reason.NOT_IN_SOURCE,
                        "Mã vừa quét không thuộc vị trí nguồn đã chọn hoặc không còn tồn khả dụng.",
                    )
                } else {
                    PaResolvedCode.Fail(
                        PaResolvedCode.Reason.NOT_FOUND,
                        "Không tìm thấy serial/lot hợp lệ cho mã vừa quét.",
                    )
                }
            }
        }
    }

    private suspend fun resolveSerial(
        warehouseIdInt: Int,
        fromLocationIdInt: Int,
        code: String,
        productId: String?,
    ): PaResolvedCode {
        val productIdInt = productId?.toIntId()
        val candidateParams = buildMap {
            put("select", "id,product_id")
            put("serial_number", "eq.$code")
            put("limit", "50")
            if (productIdInt != null) put("product_id", "eq.$productIdInt")
        }
        val candidates = restGet("serials", candidateParams).getOrElse {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.QUERY_ERROR, it.userMessage())
        }.mapNotNull { row ->
            val serialId = row.optIdString("id") ?: return@mapNotNull null
            val pid = row.optIdString("product_id") ?: return@mapNotNull null
            serialId to pid
        }
        if (candidates.isEmpty()) {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.NOT_FOUND, "Không tìm thấy serial hợp lệ.")
        }

        val serialIds = candidates.map { it.first }.distinct()
        val stockParams = buildMap {
            put("select", "serial_id,product_id,quantity_available")
            put("warehouse_id", "eq.$warehouseIdInt")
            put("location_id", "eq.$fromLocationIdInt")
            put("serial_id", inFilter(serialIds))
            put("quantity_available", "gt.0")
            put("limit", "50")
            if (productIdInt != null) put("product_id", "eq.$productIdInt")
        }
        val stockRows = restGet("stock_summary", stockParams).getOrElse {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.QUERY_ERROR, it.userMessage())
        }.mapNotNull { row ->
            val serialId = row.optIdString("serial_id") ?: return@mapNotNull null
            val pid = row.optIdString("product_id") ?: return@mapNotNull null
            serialId to pid
        }

        val matched = candidates.firstOrNull { candidate ->
            stockRows.any { it.first == candidate.first && it.second == candidate.second }
        } ?: return PaResolvedCode.Fail(
            PaResolvedCode.Reason.NOT_IN_SOURCE,
            "Serial không thuộc vị trí nguồn đã chọn hoặc không còn tồn khả dụng.",
        )

        return PaResolvedCode.Ok(
            trackingType = PaTrackingType.SERIAL,
            productId = matched.second,
            serialId = matched.first,
            lotId = null,
        )
    }

    private suspend fun resolveLot(
        warehouseIdInt: Int,
        fromLocationIdInt: Int,
        code: String,
        productId: String?,
    ): PaResolvedCode {
        val productIdInt = productId?.toIntId()
        val candidateParams = buildMap {
            put("select", "id,product_id")
            put("lot_number", "eq.$code")
            put("limit", "50")
            if (productIdInt != null) put("product_id", "eq.$productIdInt")
        }
        val candidates = restGet("lots", candidateParams).getOrElse {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.QUERY_ERROR, it.userMessage())
        }.mapNotNull { row ->
            val lotId = row.optIdString("id") ?: return@mapNotNull null
            val pid = row.optIdString("product_id") ?: return@mapNotNull null
            lotId to pid
        }
        if (candidates.isEmpty()) {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.NOT_FOUND, "Không tìm thấy lot hợp lệ.")
        }

        val lotIds = candidates.map { it.first }.distinct()
        val stockParams = buildMap {
            put("select", "lot_id,product_id,quantity_available")
            put("warehouse_id", "eq.$warehouseIdInt")
            put("location_id", "eq.$fromLocationIdInt")
            put("lot_id", inFilter(lotIds))
            put("quantity_available", "gt.0")
            put("limit", "50")
            if (productIdInt != null) put("product_id", "eq.$productIdInt")
        }
        val stockRows = restGet("stock_summary", stockParams).getOrElse {
            return PaResolvedCode.Fail(PaResolvedCode.Reason.QUERY_ERROR, it.userMessage())
        }.mapNotNull { row ->
            val lotId = row.optIdString("lot_id") ?: return@mapNotNull null
            val pid = row.optIdString("product_id") ?: return@mapNotNull null
            lotId to pid
        }

        val matched = candidates.firstOrNull { candidate ->
            stockRows.any { it.first == candidate.first && it.second == candidate.second }
        } ?: return PaResolvedCode.Fail(
            PaResolvedCode.Reason.NOT_IN_SOURCE,
            "Lot không thuộc vị trí nguồn đã chọn hoặc không còn tồn khả dụng.",
        )

        return PaResolvedCode.Ok(
            trackingType = PaTrackingType.LOT,
            productId = matched.second,
            serialId = null,
            lotId = matched.first,
        )
    }

    override suspend fun liveStockCheck(
        warehouseId: String,
        fromLocationId: String,
        productId: String,
        lotId: String?,
        serialId: String?,
    ): Result<PaLiveStock> {
        val warehouseIdInt = warehouseId.toIntId()
        val fromLocationIdInt = fromLocationId.toIntId()
        val productIdInt = productId.toIntId()
        if (warehouseIdInt == null || fromLocationIdInt == null || productIdInt == null) {
            return failure("PA_LIVE_CHECK_INVALID", "Thiếu dữ liệu để kiểm tra tồn khả dụng.")
        }
        val body = JSONObject()
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_from_location_id", fromLocationIdInt)
            .put("p_product_id", productIdInt)
            .put("p_lot_id", lotId?.toIntId() ?: JSONObject.NULL)
            .put("p_serial_id", serialId?.toIntId() ?: JSONObject.NULL)
        return rpc("rpc_pa_live_stock_check", body).mapCatching { responseBody ->
            val record = unwrapObject(responseBody)
            if (record != null && record.has("ok") && !record.optBoolean("ok", true)) {
                val reason = record.optStringOrNull("reason")
                    ?: record.optStringOrNull("message")
                    ?: "live_check_failed"
                throw AppException(
                    AppError(
                        code = "PA_LIVE_CHECK_FAILED",
                        message = PutawayErrorMapper.toUserMessage(reason),
                    ),
                )
            }
            PaLiveStock(
                availableQty = record?.optDoubleOrNull("available_qty")
                    ?: record?.optDoubleOrNull("qty_available")
                    ?: record?.optDoubleOrNull("available"),
            )
        }
    }

    override suspend fun addLine(request: PaAddLineRequest): Result<Unit> {
        val headerIdInt = request.headerId.toIntId()
        val fromIdInt = request.fromLocationId.toIntId()
        val toIdInt = request.toLocationId.toIntId()
        val productIdInt = request.productId.toIntId()
        if (headerIdInt == null || fromIdInt == null || toIdInt == null || productIdInt == null) {
            return failure("PA_ADD_LINE_INVALID", "Thiếu dữ liệu hợp lệ để lưu dòng sắp xếp.")
        }
        val body = JSONObject()
            .put("p_pa_header_id", headerIdInt)
            .put("p_from_location_id", fromIdInt)
            .put("p_to_location_id", toIdInt)
            .put("p_product_id", productIdInt)
            .put("p_qty", request.quantity)
            .put("p_uom_id", request.uomId?.toIntId() ?: JSONObject.NULL)
            .put("p_lot_id", request.lotId?.toIntId() ?: JSONObject.NULL)
            .put("p_serial_id", request.serialId?.toIntId() ?: JSONObject.NULL)
            .put("p_notes", request.notes?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        return rpc("rpc_pa_add_line", body).map { }
    }

    override suspend fun deleteLine(detailId: String): Result<Unit> {
        val detailIdInt = detailId.toIntId()
            ?: return failure("PA_DELETE_INVALID", "ID dòng nháp không hợp lệ.")
        return rpc("rpc_pa_delete_line", JSONObject().put("p_detail_id", detailIdInt)).map { }
    }

    override suspend fun submit(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("PA_HEADER_INVALID", "ID phiên PA không hợp lệ.")
        val result = rpc("rpc_pa_submit", JSONObject().put("p_pa_header_id", headerIdInt)).map { }
        if (result.isSuccess) {
            // Hậu xử lý threshold events giống scanner PWA; best-effort, không chặn kết quả submit.
            rpc("rpc_process_inventory_threshold_events", JSONObject().put("p_limit", THRESHOLD_LIMIT))
                .onFailure { logger.error(TAG, "Xử lý threshold events sau submit thất bại: ${it.message}") }
        }
        return result
    }

    // region HTTP helpers

    private suspend fun rpc(function: String, body: JSONObject): Result<String?> {
        val ctx = requireContext() ?: return failure(
            "SESSION_REQUIRED",
            "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
        )
        val result = networkClient.execute(
            NetworkRequest(
                connectionConfig = ctx.first,
                path = "/rest/v1/rpc/$function",
                method = HttpMethod.POST,
                body = body.toString(),
                authToken = ctx.second,
            ),
        )
        return result.toResult(function)
    }

    private suspend fun restGet(table: String, queryParams: Map<String, String>): Result<List<JSONObject>> {
        val ctx = requireContext() ?: return failure(
            "SESSION_REQUIRED",
            "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
        )
        val result = networkClient.execute(
            NetworkRequest(
                connectionConfig = ctx.first,
                path = "/rest/v1/$table",
                method = HttpMethod.GET,
                queryParams = queryParams,
                authToken = ctx.second,
            ),
        )
        return result.toResult(table).map { body -> parseArray(body) }
    }

    private fun requireContext(): Pair<ConnectionConfig, String>? {
        val connectionConfig = appPreferences.currentConnectionConfig() ?: return null
        val accessToken = secureSessionStorage.loadSession()?.accessToken
        if (accessToken.isNullOrBlank()) return null
        return connectionConfig to accessToken
    }

    private fun NetworkResult<NetworkResponse>.toResult(label: String): Result<String?> {
        return when (this) {
            is NetworkResult.Failure -> Result.failure(AppException(error))
            is NetworkResult.Success -> {
                val status = data.statusCode
                if (status in HTTP_OK..HTTP_SUCCESS_MAX) {
                    Result.success(data.body)
                } else {
                    val backendMessage = extractBackendMessage(data.body)
                    logger.error(TAG, "PA '$label' lỗi HTTP $status: ${backendMessage ?: data.body}")
                    when {
                        status == HTTP_UNAUTHORIZED -> failure(
                            "SESSION_EXPIRED",
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                        )

                        status == HTTP_FORBIDDEN || PutawayErrorMapper.isPermissionError(backendMessage) -> failure(
                            "PERMISSION_DENIED",
                            "Bạn không có quyền thao tác sắp xếp cho kho hoặc phiếu đã chọn.",
                        )

                        else -> Result.failure(
                            AppException(
                                AppError(
                                    code = "PA_REQUEST_FAILED",
                                    message = PutawayErrorMapper.toUserMessage(backendMessage),
                                    retryable = status >= HTTP_SERVER_ERROR,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun extractBackendMessage(body: String?): String? {
        body ?: return null
        return runCatching {
            val obj = JSONObject(body)
            obj.optStringOrNull("message")
                ?: obj.optStringOrNull("error")
                ?: obj.optStringOrNull("hint")
                ?: obj.optStringOrNull("details")
        }.getOrNull() ?: body
    }

    // endregion

    // region parsing

    private fun parseProducts(body: String?): List<PaProduct> {
        val rows = parseFlexibleArray(body)
        return rows.mapNotNull { row ->
            val productObj = row.optJSONObject("product")
            val productId = row.optIdString("product_id")
                ?: row.optIdString("id")
                ?: productObj?.optIdString("id")
                ?: return@mapNotNull null
            val code = row.optStringOrNull("product_code")
                ?: row.optStringOrNull("code")
                ?: productObj?.optStringOrNull("code")
                ?: productId
            val name = row.optStringOrNull("product_name")
                ?: row.optStringOrNull("name")
                ?: productObj?.optStringOrNull("name")
            val tracking = PaTrackingType.parse(
                row.optStringOrNull("tracking_type")
                    ?: row.optStringOrNull("product_tracking_type")
                    ?: productObj?.optStringOrNull("tracking_type"),
            )
            val uomObj = row.optJSONObject("uom") ?: row.optJSONObject("uoms")
            PaProduct(
                id = productId,
                code = code,
                name = name,
                trackingType = tracking,
                availableQty = row.optDoubleOrNull("available_qty")
                    ?: row.optDoubleOrNull("qty_available")
                    ?: row.optDoubleOrNull("quantity_available"),
                uomId = row.optIdString("uom_id") ?: productObj?.optIdString("uom_id"),
                uomLabel = row.optStringOrNull("uom_code")
                    ?: row.optStringOrNull("uom_name")
                    ?: row.optStringOrNull("unit_code")
                    ?: row.optStringOrNull("unit_name")
                    ?: uomObj?.optStringOrNull("code")
                    ?: uomObj?.optStringOrNull("name"),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.code })
    }

    private fun parseSession(row: JSONObject?): PaSession? {
        row ?: return null
        val nested = row.optJSONObject("pa_header")
            ?: row.optJSONObject("header")
            ?: row.optJSONObject("data")
        val source = nested ?: row
        val id = source.optIdString("pa_header_id")
            ?: source.optIdString("id")
            ?: row.optIdString("pa_header_id")
            ?: row.optIdString("id")
            ?: return null
        return PaSession(
            id = id,
            code = source.optStringOrNull("code")
                ?: source.optStringOrNull("pa_code")
                ?: row.optStringOrNull("code"),
            status = PaSessionStatus.parse(source.optStringOrNull("status") ?: row.optStringOrNull("status")),
            createdAt = source.optStringOrNull("created_at"),
            updatedAt = source.optStringOrNull("updated_at"),
            completedAt = source.optStringOrNull("completed_at"),
        )
    }

    private fun parseDraftLine(row: JSONObject): PaDraftLine? {
        val id = row.optIdString("id") ?: return null
        val fromLocation = row.optJSONObject("from_location")
        val toLocation = row.optJSONObject("to_location")
        val product = row.optJSONObject("product")
        val uom = row.optJSONObject("uom")
        val lot = row.optJSONObject("lot")
        val serial = row.optJSONObject("serial")
        val tracking = PaTrackingType.parse(
            row.optStringOrNull("tracking_type") ?: product?.optStringOrNull("tracking_type"),
        )
        return PaDraftLine(
            id = id,
            fromLocationLabel = locationLabel(fromLocation) ?: row.optIdString("from_location_id") ?: "—",
            toLocationLabel = locationLabel(toLocation) ?: row.optIdString("to_location_id") ?: "—",
            productLabel = product?.let { p ->
                val code = p.optStringOrNull("code")
                val name = p.optStringOrNull("name")
                when {
                    code != null && name != null -> "$code — $name"
                    code != null -> code
                    else -> name
                }
            } ?: row.optIdString("product_id") ?: "—",
            trackingType = tracking,
            quantity = row.optDoubleOrNull("quantity") ?: 0.0,
            uomLabel = uom?.optStringOrNull("code") ?: uom?.optStringOrNull("name"),
            lotNumber = lot?.optStringOrNull("lot_number"),
            serialNumber = serial?.optStringOrNull("serial_number"),
            notes = row.optStringOrNull("notes"),
            fromLocationId = row.optIdString("from_location_id"),
            lotId = row.optIdString("lot_id"),
            serialId = row.optIdString("serial_id"),
            productId = row.optIdString("product_id"),
        )
    }

    private fun locationLabel(obj: JSONObject?): String? {
        obj ?: return null
        val code = obj.optStringOrNull("code")
        val name = obj.optStringOrNull("name")
        return when {
            code != null && name != null -> "$code — $name"
            code != null -> code
            else -> name
        }
    }

    /** Mảng từ REST hoặc RPC trả về object/array trực tiếp. */
    private fun parseArray(body: String?): List<JSONObject> {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        return when (val value = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> listOf(value)
            else -> emptyList()
        }
    }

    /** RPC có thể trả mảng, object trực tiếp, hoặc object bọc `data/items/products`. */
    private fun parseFlexibleArray(body: String?): List<JSONObject> {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        return when (val value = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> {
                val nested = value.optJSONArray("data")
                    ?: value.optJSONArray("items")
                    ?: value.optJSONArray("products")
                if (nested != null) {
                    (0 until nested.length()).mapNotNull { nested.optJSONObject(it) }
                } else {
                    listOf(value)
                }
            }

            else -> emptyList()
        }
    }

    private fun unwrapObject(body: String?): JSONObject? {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when (val value = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        }
    }

    // endregion

    private fun inFilter(values: List<String>): String =
        "in.(${values.joinToString(",") { it.trim() }})"

    private fun Throwable.userMessage(): String =
        (this as? AppException)?.appError?.message ?: message ?: "Lỗi không xác định."

    private fun <T> failure(code: String, message: String): Result<T> =
        Result.failure(AppException(AppError(code = code, message = message)))

    private fun String.toIntId(): Int? = trim().toIntOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIdString(key: String): String? {
        if (isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> {
                val asLong = value.toLong()
                if (value.toDouble() == asLong.toDouble()) asLong.toString() else value.toString()
            }

            is String -> value.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private companion object {
        const val TAG = "PutawayRepository"
        const val THRESHOLD_LIMIT = 200
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SERVER_ERROR = 500
        const val PA_HEADER_SELECT =
            "id,code,status,warehouse_id,pic_id,notes,completed_at,created_at,updated_at"
        const val PA_DETAIL_SELECT =
            "id,tenant_id,pa_header_id,product_id,uom_id,tracking_type,from_location_id,to_location_id," +
                "lot_id,serial_id,quantity,notes,moved_at,created_at,updated_at," +
                "from_location:locations!pa_details_from_location_id_fkey(id,code,name)," +
                "to_location:locations!pa_details_to_location_id_fkey(id,code,name)," +
                "product:products(id,code,name,tracking_type),uom:uoms(id,code,name)," +
                "lot:lots(id,lot_number),serial:serials(id,serial_number)"
    }
}
