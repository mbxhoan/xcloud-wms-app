package vn.delfi.xcloudwms.data.gr

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
import vn.delfi.xcloudwms.domain.model.GrHeader
import vn.delfi.xcloudwms.domain.model.GrLine
import vn.delfi.xcloudwms.domain.model.GrLocation
import vn.delfi.xcloudwms.domain.model.GrReceiveSuccess
import vn.delfi.xcloudwms.domain.model.GrStatus
import vn.delfi.xcloudwms.domain.model.GrTrackingType

/**
 * Repository cho luồng Goods Receipt Receiving native. Mọi thay đổi tồn kho đi qua RPC
 * backend (rpc_gr_*) + ghi `gr_details` qua PostgREST đúng RLS như scanner PWA. Client chỉ
 * optimistic UI, không tự quyết tồn — backend là nguồn sự thật. Không insert thẳng vào
 * `serials`/`lots`; chỉ qua RPC resolve (SECURITY DEFINER).
 */
interface GoodsReceiptRepository {
    /** Danh sách phiếu nhập đang chờ nhận trong kho (CREATED/RECEIVING/RECEIVED). */
    suspend fun loadAssignedHeaders(warehouseId: String): Result<List<GrHeader>>

    suspend fun loadHeader(headerId: String): Result<GrHeader>

    suspend fun loadLines(headerId: String): Result<List<GrLine>>

    /** Vị trí nhập khả dụng trong kho hiện tại (đảm bảo location thuộc đúng kho). */
    suspend fun loadLocations(warehouseId: String): Result<List<GrLocation>>

    /** Chuyển phiếu CREATED → RECEIVING (idempotent: bỏ qua nếu đã RECEIVING). */
    suspend fun startReceiving(headerId: String): Result<Unit>

    /** Nhận hàng dòng NONE: chỉ số lượng + vị trí. */
    suspend fun receiveNone(line: GrLine, locationId: String, qty: Double): Result<GrReceiveSuccess>

    /** Nhận hàng dòng LOT: resolve/tạo lô qua RPC rồi ghi gr_details. */
    suspend fun receiveLot(
        line: GrLine,
        locationId: String,
        code: String,
        qty: Double,
        manufactureDate: String?,
        expiryDate: String?,
    ): Result<GrReceiveSuccess>

    /** Nhận hàng dòng SERIAL: resolve/tạo serial qua RPC rồi ghi gr_details (qty=1). */
    suspend fun receiveSerial(
        line: GrLine,
        locationId: String,
        code: String,
        manufactureDate: String?,
        expiryDate: String?,
    ): Result<GrReceiveSuccess>

    /** Chốt nhận hàng: RECEIVING → RECEIVED (hoặc COMPLETED nếu đủ toàn bộ dòng). */
    suspend fun submitReceive(headerId: String): Result<Unit>

    /** Hoàn tất nhập kho phần đã nhận: RECEIVING/RECEIVED → COMPLETED (post ledger). */
    suspend fun complete(headerId: String): Result<Unit>
}

class DefaultGoodsReceiptRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : GoodsReceiptRepository {

    override suspend fun loadAssignedHeaders(warehouseId: String): Result<List<GrHeader>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("GR_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        val rows = restGet(
            table = "gr_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "warehouse_id" to "eq.$warehouseIdInt",
                "status" to "in.(CREATED,RECEIVING,RECEIVED)",
                "order" to "updated_at.desc",
                "limit" to "200",
            ),
        ).getOrElse { return Result.failure(it) }
        val headers = rows.mapNotNull(::parseHeader)
        return Result.success(resolvePartnerLabels(headers))
    }

    override suspend fun loadHeader(headerId: String): Result<GrHeader> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GR_HEADER_INVALID", "ID phiếu nhập không hợp lệ.")
        val rows = restGet(
            table = "gr_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "id" to "eq.$headerIdInt",
                "limit" to "1",
            ),
        ).getOrElse { return Result.failure(it) }
        val header = rows.firstNotNullOfOrNull(::parseHeader)
            ?: return failure("GR_NOT_FOUND", "Không tìm thấy phiếu nhập.")
        return Result.success(resolvePartnerLabels(listOf(header)).first())
    }

    override suspend fun loadLines(headerId: String): Result<List<GrLine>> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GR_HEADER_INVALID", "ID phiếu nhập không hợp lệ.")
        val lineRows = restGet(
            table = "gr_lines",
            queryParams = mapOf(
                "select" to LINE_SELECT,
                "gr_header_id" to "eq.$headerIdInt",
                "order" to "id.asc",
            ),
        ).getOrElse { return Result.failure(it) }

        val parsed = lineRows.mapNotNull { row -> parseLine(row) }
        if (parsed.isEmpty()) return Result.success(emptyList())

        // received_quantity chuẩn từ tổng gr_details.quantity (quantity_received có thể trễ).
        val receivedByLine = loadReceivedTotals(parsed.map { it.id })
        val merged = parsed.map { line ->
            val received = receivedByLine[line.id]
            if (received != null) line.copy(receivedQty = received) else line
        }
        return Result.success(merged.sortedBy { it.productCode.lowercase() })
    }

    private suspend fun loadReceivedTotals(lineIds: List<String>): Map<String, Double> {
        val ids = lineIds.mapNotNull { it.toIntId() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = restGet(
            table = "gr_details",
            queryParams = mapOf(
                "select" to "gr_line_id,quantity",
                "gr_line_id" to inFilter(ids.map { it.toString() }),
                "limit" to "5000",
            ),
        ).getOrElse {
            logger.error(TAG, "Tải gr_details để tính received lỗi: ${it.message}")
            return emptyMap()
        }
        val totals = HashMap<String, Double>()
        for (row in rows) {
            val lineId = row.optIdString("gr_line_id") ?: continue
            val qty = row.optDoubleOrNull("quantity") ?: 0.0
            totals[lineId] = (totals[lineId] ?: 0.0) + qty
        }
        return totals
    }

    override suspend fun loadLocations(warehouseId: String): Result<List<GrLocation>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("GR_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        return restGet(
            table = "locations",
            queryParams = mapOf(
                "select" to "id,code,name",
                "warehouse_id" to "eq.$warehouseIdInt",
                "is_active" to "eq.true",
                "deleted_at" to "is.null",
                "order" to "code.asc",
                "limit" to "1000",
            ),
        ).map { rows ->
            rows.mapNotNull { row ->
                val id = row.optIdString("id") ?: return@mapNotNull null
                GrLocation(
                    id = id,
                    code = row.optStringOrNull("code") ?: id,
                    name = row.optStringOrNull("name"),
                )
            }
        }
    }

    override suspend fun startReceiving(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GR_HEADER_INVALID", "ID phiếu nhập không hợp lệ.")
        val result = rpc("rpc_gr_start_receiving", JSONObject().put("p_gr_id", headerIdInt)).map { }
        // Idempotent: phiếu đã RECEIVING thì coi như đã bắt đầu.
        return result.recoverCatching { throwable ->
            val message = throwable.userMessage().lowercase()
            if (message.contains("already") || message.contains("đã") || message.contains("receiving")) {
                Unit
            } else {
                throw throwable
            }
        }
    }

    override suspend fun receiveNone(line: GrLine, locationId: String, qty: Double): Result<GrReceiveSuccess> {
        val lineIdInt = line.id.toIntId()
            ?: return failure("GR_RECEIVE_INVALID", "Thiếu gr_line_id để nhận hàng.")
        val locationIdInt = locationId.toIntId()
            ?: return failure("GR_LOCATION_INVALID", "Chọn vị trí nhập hợp lệ trong kho.")
        val receiveQty = clampQty(qty)
        if (receiveQty <= 0) return failure("GR_QTY_INVALID", "Số lượng phải lớn hơn 0.")

        val payload = JSONObject()
            .put("gr_line_id", lineIdInt)
            .put("quantity", receiveQty)
            .put("location_id", locationIdInt)
        return insertDetail(payload)
            .map { GrReceiveSuccess(line.id, receiveQty, "Đã nhận ${formatQty(receiveQty)}.") }
    }

    override suspend fun receiveLot(
        line: GrLine,
        locationId: String,
        code: String,
        qty: Double,
        manufactureDate: String?,
        expiryDate: String?,
    ): Result<GrReceiveSuccess> {
        val lineIdInt = line.id.toIntId()
            ?: return failure("GR_RECEIVE_INVALID", "Thiếu gr_line_id để nhận lô.")
        val locationIdInt = locationId.toIntId()
            ?: return failure("GR_LOCATION_INVALID", "Chọn vị trí nhập hợp lệ trong kho.")
        val normalized = code.trim()
        val receiveQty = clampQty(qty)
        if (normalized.isEmpty()) return failure("GR_LOT_EMPTY", "Mã lô rỗng.")
        if (receiveQty <= 0) return failure("GR_LOT_QTY_INVALID", "Số lượng phải lớn hơn 0.")

        val body = JSONObject()
            .put("p_gr_line_id", lineIdInt)
            .put("p_lot_number", normalized)
            .put("p_allow_create", true)
            .put("p_manufacture_date", manufactureDate ?: JSONObject.NULL)
            .put("p_expiry_date", expiryDate ?: JSONObject.NULL)
        val response = rpc("rpc_gr_resolve_lot_scan", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
            ?: return failure("GR_LOT_INVALID_RESP", "API trả về dữ liệu lô không hợp lệ.")

        val lot = response.optJSONObject("lot")
        val reasonCode = response.optStringOrNull("reason")
            ?: response.optStringOrNull("message")
            ?: response.optStringOrNull("error")
        val okFlag = response.optBooleanOrNull("ok")
        val canReceive = response.optBooleanOrNull("can_receive")
        val lotId = response.optIdString("lot_id") ?: lot?.optIdString("id")
        if (okFlag == false || canReceive == false || lotId == null) {
            return failure("GR_LOT_NOT_RECEIVABLE", GoodsReceiptErrorMapper.toUserMessage(reasonCode))
        }

        val effectiveMfg = manufactureDate ?: lot?.optStringOrNull("manufacture_date")
        val effectiveExpiry = expiryDate ?: lot?.optStringOrNull("expiry_date")
        val payload = JSONObject()
            .put("gr_line_id", lineIdInt)
            .put("quantity", receiveQty)
            .put("location_id", locationIdInt)
            .put("lot_id", lotId.toIntId() ?: lotId)
            .applyDates(effectiveMfg, effectiveExpiry)
        return insertDetail(payload)
            .map { GrReceiveSuccess(line.id, receiveQty, "Đã nhận lô $normalized × ${formatQty(receiveQty)}.") }
    }

    override suspend fun receiveSerial(
        line: GrLine,
        locationId: String,
        code: String,
        manufactureDate: String?,
        expiryDate: String?,
    ): Result<GrReceiveSuccess> {
        val lineIdInt = line.id.toIntId()
            ?: return failure("GR_RECEIVE_INVALID", "Thiếu gr_line_id để nhận serial.")
        val locationIdInt = locationId.toIntId()
            ?: return failure("GR_LOCATION_INVALID", "Chọn vị trí nhập hợp lệ trong kho.")
        val normalized = code.trim()
        if (normalized.isEmpty()) return failure("GR_SERIAL_EMPTY", "Serial rỗng.")

        val body = JSONObject()
            .put("p_gr_line_id", lineIdInt)
            .put("p_serial_number", normalized)
            .put("p_allow_create", true)
            .put("p_manufacture_date", manufactureDate ?: JSONObject.NULL)
            .put("p_expiry_date", expiryDate ?: JSONObject.NULL)
        val response = rpc("rpc_gr_resolve_serial_scan", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
            ?: return failure("GR_SERIAL_INVALID_RESP", "API trả về dữ liệu serial không hợp lệ.")

        val serial = response.optJSONObject("serial")
        val reasonCode = response.optStringOrNull("reason")
            ?: response.optStringOrNull("message")
            ?: response.optStringOrNull("error")
        val okFlag = response.optBooleanOrNull("ok")
        val canReceive = response.optBooleanOrNull("can_receive")
        val serialId = response.optIdString("serial_id") ?: serial?.optIdString("id")
        if (okFlag == false || canReceive == false || serialId == null) {
            return failure("GR_SERIAL_NOT_RECEIVABLE", GoodsReceiptErrorMapper.toUserMessage(reasonCode))
        }

        val effectiveMfg = manufactureDate ?: serial?.optStringOrNull("manufacture_date")
        val effectiveExpiry = expiryDate ?: serial?.optStringOrNull("expiry_date")
        val payload = JSONObject()
            .put("gr_line_id", lineIdInt)
            .put("quantity", 1)
            .put("location_id", locationIdInt)
            .put("serial_id", serialId.toIntId() ?: serialId)
            .applyDates(effectiveMfg, effectiveExpiry)
        return insertDetail(payload)
            .map { GrReceiveSuccess(line.id, 1.0, "Đã nhận serial $normalized.") }
    }

    override suspend fun submitReceive(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GR_HEADER_INVALID", "ID phiếu nhập không hợp lệ.")
        return rpc("rpc_gr_submit_receive", JSONObject().put("p_gr_id", headerIdInt)).map { }
    }

    override suspend fun complete(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GR_HEADER_INVALID", "ID phiếu nhập không hợp lệ.")
        return rpc("rpc_gr_complete", JSONObject().put("p_gr_id", headerIdInt)).map { }
    }

    // region gr_details writes (PostgREST, theo RLS gr.scan như scanner PWA)

    private fun JSONObject.applyDates(manufactureDate: String?, expiryDate: String?): JSONObject {
        if (!manufactureDate.isNullOrBlank()) {
            // Gửi cả manufacture_date + manufactured_date (legacy) để tương thích schema.
            put("manufacture_date", manufactureDate)
            put("manufactured_date", manufactureDate)
        }
        if (!expiryDate.isNullOrBlank()) {
            put("expiry_date", expiryDate)
        }
        return this
    }

    private suspend fun insertDetail(payload: JSONObject): Result<Unit> {
        return restWrite(
            table = "gr_details",
            method = HttpMethod.POST,
            queryParams = emptyMap(),
            body = payload.toString(),
        ).map { }
    }

    // endregion

    // region HTTP helpers (mirror DefaultGoodsIssueRepository)

    private suspend fun rpc(function: String, body: JSONObject): Result<String?> {
        val ctx = requireContext() ?: return sessionFailure()
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
        val ctx = requireContext() ?: return sessionFailure()
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

    private suspend fun restWrite(
        table: String,
        method: HttpMethod,
        queryParams: Map<String, String>,
        body: String,
    ): Result<String?> {
        val ctx = requireContext() ?: return sessionFailure()
        val result = networkClient.execute(
            NetworkRequest(
                connectionConfig = ctx.first,
                path = "/rest/v1/$table",
                method = method,
                queryParams = queryParams,
                headers = mapOf("Prefer" to "return=minimal"),
                body = body,
                authToken = ctx.second,
            ),
        )
        return result.toResult(table)
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
                    logger.error(TAG, "GR '$label' lỗi HTTP $status: ${backendMessage ?: data.body}")
                    when {
                        status == HTTP_UNAUTHORIZED -> failure(
                            "SESSION_EXPIRED",
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                        )

                        status == HTTP_FORBIDDEN || GoodsReceiptErrorMapper.isPermissionError(backendMessage) -> failure(
                            "PERMISSION_DENIED",
                            "Bạn không có quyền thao tác phiếu nhập hoặc kho đã chọn.",
                        )

                        else -> Result.failure(
                            AppException(
                                AppError(
                                    code = "GR_REQUEST_FAILED",
                                    message = GoodsReceiptErrorMapper.toUserMessage(backendMessage),
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

    private fun parseHeader(row: JSONObject): GrHeader? {
        val id = row.optIdString("id") ?: return null
        val warehouse = row.optJSONObject("warehouse") ?: row.optJSONObject("warehouses")
        return GrHeader(
            id = id,
            tenantId = row.optIdString("tenant_id"),
            warehouseId = row.optIdString("warehouse_id"),
            code = row.optStringOrNull("code"),
            status = GrStatus.parse(row.optStringOrNull("status")),
            warehouseLabel = warehouse?.let { w -> w.optStringOrNull("name") ?: w.optStringOrNull("code") },
            partnerLabel = row.optStringOrNull("partner_label"),
            referenceType = row.optStringOrNull("reference_type"),
            referenceNumber = row.optStringOrNull("reference_number"),
            note = null,
            updatedAt = row.optStringOrNull("updated_at") ?: row.optStringOrNull("created_at"),
        ).withPartnerIdFallback(row.optIdString("partner_id"))
    }

    private fun GrHeader.withPartnerIdFallback(partnerId: String?): GrHeader =
        if (partnerLabel == null && partnerId != null) copy(partnerLabel = partnerId) else this

    private suspend fun resolvePartnerLabels(headers: List<GrHeader>): List<GrHeader> {
        val partnerIds = headers.mapNotNull { it.partnerLabel?.toIntId()?.toString() }.distinct()
        if (partnerIds.isEmpty()) return headers
        val rows = restGet(
            table = "partners",
            queryParams = mapOf(
                "select" to "id,code,name",
                "id" to inFilter(partnerIds),
                "limit" to "500",
            ),
        ).getOrElse {
            logger.error(TAG, "Resolve partner labels lỗi: ${it.message}")
            return headers
        }
        val labelById = HashMap<String, String>()
        for (row in rows) {
            val id = row.optIdString("id") ?: continue
            val label = row.optStringOrNull("name") ?: row.optStringOrNull("code") ?: continue
            labelById[id] = label
        }
        if (labelById.isEmpty()) return headers
        return headers.map { header ->
            val pid = header.partnerLabel
            if (pid != null && labelById.containsKey(pid)) header.copy(partnerLabel = labelById[pid]) else header
        }
    }

    private fun parseLine(row: JSONObject): GrLine? {
        val id = row.optIdString("id") ?: return null
        val product = row.optJSONObject("product") ?: row.optJSONObject("products")
        val productId = row.optIdString("product_id") ?: product?.optIdString("id")
        val code = product?.optStringOrNull("code")
            ?: row.optStringOrNull("product_code")
            ?: productId
            ?: id
        val name = product?.optStringOrNull("name") ?: row.optStringOrNull("product_name")
        val tracking = GrTrackingType.parse(
            row.optStringOrNull("tracking_type") ?: product?.optStringOrNull("tracking_type"),
        )
        val lineUom = row.optJSONObject("uom") ?: row.optJSONObject("uoms")
        val productUom = product?.optJSONObject("uom") ?: product?.optJSONObject("uoms")
        val expected = row.optDoubleOrNull("quantity_expected")
            ?: row.optDoubleOrNull("quantity")
            ?: 0.0
        val received = row.optDoubleOrNull("quantity_received") ?: 0.0
        return GrLine(
            id = id,
            productId = productId,
            productCode = code,
            productName = name,
            trackingType = tracking,
            expectedQty = expected,
            receivedQty = received,
            requireMfgDate = product?.optBooleanOrNull("require_manufacture_date") ?: false,
            requireExpiryDate = product?.optBooleanOrNull("require_expiry_date") ?: false,
            pickingStrategy = product?.optStringOrNull("picking_strategy"),
            uomId = row.optIdString("uom_id") ?: product?.optIdString("uom_id"),
            uomLabel = lineUom?.let { it.optStringOrNull("code") ?: it.optStringOrNull("name") }
                ?: productUom?.let { it.optStringOrNull("code") ?: it.optStringOrNull("name") },
        )
    }

    private fun parseArray(body: String?): List<JSONObject> {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        return when (val value = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> listOf(value)
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

    private fun clampQty(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val normalized = value.coerceIn(0.0, 1_000_000.0)
        return Math.round(normalized * 1000.0) / 1000.0
    }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')

    private fun Throwable.userMessage(): String =
        (this as? AppException)?.appError?.message ?: message ?: "Lỗi không xác định."

    private fun <T> failure(code: String, message: String): Result<T> =
        Result.failure(AppException(AppError(code = code, message = message)))

    private fun <T> sessionFailure(): Result<T> =
        failure("SESSION_REQUIRED", "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.")

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

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Boolean -> value
            is String -> value.trim().lowercase().let { if (it == "true") true else if (it == "false") false else null }
            else -> null
        }
    }

    private companion object {
        const val TAG = "GoodsReceiptRepository"
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SERVER_ERROR = 500
        const val HEADER_SELECT =
            "id,tenant_id,warehouse_id,partner_id,code,status,reference_type,reference_number," +
                "expected_date,ref_gt_id,updated_at,created_at,warehouse:warehouses(id,code,name)"
        const val LINE_SELECT =
            "id,gr_header_id,product_id,uom_id,quantity_expected,quantity_received," +
                "product:products(id,code,name,tracking_type,picking_strategy," +
                "require_manufacture_date,require_expiry_date,uom_id,uom:uoms(id,code,name))," +
                "uom:uoms(id,code,name)"
    }
}
