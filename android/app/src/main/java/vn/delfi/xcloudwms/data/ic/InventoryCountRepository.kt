package vn.delfi.xcloudwms.data.ic

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
import vn.delfi.xcloudwms.domain.model.IcCountSuccess
import vn.delfi.xcloudwms.domain.model.IcHeader
import vn.delfi.xcloudwms.domain.model.IcLine
import vn.delfi.xcloudwms.domain.model.IcLocation
import vn.delfi.xcloudwms.domain.model.IcStatus
import vn.delfi.xcloudwms.domain.model.IcTrackingType

/**
 * Repository cho luồng Inventory Count native. Đếm đi qua RPC backend (rpc_ic_*) +
 * validate serial/lot qua rpc_check_*_scan như scanner PWA. Client chỉ optimistic UI,
 * không tự điều chỉnh tồn. Kết thúc phiếu dùng action COMPLETE_ONLY (không đụng tồn);
 * cân bằng/duyệt (ADJUST) để webapp.
 */
interface InventoryCountRepository {
    /** Danh sách phiếu kiểm kê đang mở trong kho (DRAFT/CREATED/IN_PROGRESS). */
    suspend fun loadAssignedHeaders(warehouseId: String): Result<List<IcHeader>>

    suspend fun loadHeader(headerId: String): Result<IcHeader>

    suspend fun loadLines(headerId: String): Result<List<IcLine>>

    suspend fun loadLocations(warehouseId: String): Result<List<IcLocation>>

    /** Chuyển phiếu DRAFT/CREATED → IN_PROGRESS (idempotent). */
    suspend fun startCounting(headerId: String): Result<Unit>

    /** Đếm dòng NONE: đặt số lượng đếm tuyệt đối cho dòng (rpc_ic_update_count). */
    suspend fun countNone(line: IcLine, targetCountedQty: Double): Result<IcCountSuccess>

    /** Đếm dòng LOT: validate lô qua rpc_check_lot_scan rồi ghi rpc_ic_add_detail (delta). */
    suspend fun countLot(
        header: IcHeader,
        line: IcLine,
        locationId: String?,
        code: String,
        qty: Double,
    ): Result<IcCountSuccess>

    /** Đếm dòng SERIAL: validate qua rpc_check_serial_scan rồi ghi rpc_ic_add_detail (qty=1). */
    suspend fun countSerial(
        header: IcHeader,
        line: IcLine,
        locationId: String?,
        code: String,
    ): Result<IcCountSuccess>

    /** Kết thúc kiểm kê: rpc_ic_complete action COMPLETE_ONLY (không điều chỉnh tồn). */
    suspend fun finish(headerId: String, note: String): Result<Unit>
}

class DefaultInventoryCountRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : InventoryCountRepository {

    override suspend fun loadAssignedHeaders(warehouseId: String): Result<List<IcHeader>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("IC_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        val rows = restGet(
            table = "ic_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "warehouse_id" to "eq.$warehouseIdInt",
                "status" to "in.(DRAFT,CREATED,IN_PROGRESS,COUNTING)",
                "order" to "updated_at.desc",
                "limit" to "200",
            ),
        ).getOrElse { return Result.failure(it) }
        return Result.success(rows.mapNotNull(::parseHeader))
    }

    override suspend fun loadHeader(headerId: String): Result<IcHeader> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("IC_HEADER_INVALID", "ID phiếu kiểm kê không hợp lệ.")
        val rows = restGet(
            table = "ic_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "id" to "eq.$headerIdInt",
                "limit" to "1",
            ),
        ).getOrElse { return Result.failure(it) }
        val header = rows.firstNotNullOfOrNull(::parseHeader)
            ?: return failure("IC_NOT_FOUND", "Không tìm thấy phiếu kiểm kê.")
        return Result.success(header)
    }

    override suspend fun loadLines(headerId: String): Result<List<IcLine>> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("IC_HEADER_INVALID", "ID phiếu kiểm kê không hợp lệ.")
        // Scope dòng tổng (serial_id null) như scanner; serial đếm vào ic_details.
        val lineRows = restGet(
            table = "ic_lines",
            queryParams = mapOf(
                "select" to LINE_SELECT,
                "ic_header_id" to "eq.$headerIdInt",
                "serial_id" to "is.null",
                "order" to "id.asc",
            ),
        ).getOrElse { return Result.failure(it) }

        val parsed = lineRows.mapNotNull { row -> parseLine(row) }
        if (parsed.isEmpty()) return Result.success(emptyList())

        val countedByLine = loadCountedTotals(parsed.map { it.id })
        val merged = parsed.map { line ->
            val counted = countedByLine[line.id]
            if (counted != null) line.copy(countedQty = counted) else line
        }
        return Result.success(merged.sortedBy { it.productCode.lowercase() })
    }

    private suspend fun loadCountedTotals(lineIds: List<String>): Map<String, Double> {
        val ids = lineIds.mapNotNull { it.toIntId() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = restGet(
            table = "ic_details",
            queryParams = mapOf(
                "select" to "ic_line_id,qty_counted",
                "ic_line_id" to inFilter(ids.map { it.toString() }),
                "limit" to "5000",
            ),
        ).getOrElse {
            logger.error(TAG, "Tải ic_details để tính counted lỗi: ${it.message}")
            return emptyMap()
        }
        val totals = HashMap<String, Double>()
        for (row in rows) {
            val lineId = row.optIdString("ic_line_id") ?: continue
            val qty = row.optDoubleOrNull("qty_counted") ?: 0.0
            totals[lineId] = (totals[lineId] ?: 0.0) + qty
        }
        return totals
    }

    override suspend fun loadLocations(warehouseId: String): Result<List<IcLocation>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("IC_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
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
                IcLocation(
                    id = id,
                    code = row.optStringOrNull("code") ?: id,
                    name = row.optStringOrNull("name"),
                )
            }
        }
    }

    override suspend fun startCounting(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("IC_HEADER_INVALID", "ID phiếu kiểm kê không hợp lệ.")
        val result = rpc("rpc_ic_start_counting", JSONObject().put("p_id", headerIdInt)).map { }
        return result.recoverCatching { throwable ->
            val message = throwable.userMessage().lowercase()
            if (message.contains("already") || message.contains("đã") || message.contains("in_progress")) {
                Unit
            } else {
                throw throwable
            }
        }
    }

    override suspend fun countNone(line: IcLine, targetCountedQty: Double): Result<IcCountSuccess> {
        val lineIdInt = line.id.toIntId()
            ?: return failure("IC_COUNT_INVALID", "Thiếu ic_line_id để đếm.")
        val target = clampQty(targetCountedQty)
        if (target < 0) return failure("IC_QTY_INVALID", "Số lượng không hợp lệ.")
        val body = JSONObject()
            .put("p_line_id", lineIdInt)
            .put("p_qty_counted", target)
            .put("p_note", "scan from app")
        val response = rpc("rpc_ic_update_count", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
        val reason = response?.failureReason()
        if (reason != null) return failure("IC_COUNT_FAILED", InventoryCountErrorMapper.toUserMessage(reason))
        return Result.success(IcCountSuccess(line.id, 0.0, "Đã cập nhật số đếm dòng \"${line.productCode}\"."))
    }

    override suspend fun countLot(
        header: IcHeader,
        line: IcLine,
        locationId: String?,
        code: String,
        qty: Double,
    ): Result<IcCountSuccess> {
        val headerIdInt = header.id.toIntId()
        val productIdInt = line.productId?.toIntId()
        val normalized = code.trim()
        val countQty = clampQty(qty)
        if (headerIdInt == null || productIdInt == null) {
            return failure("IC_COUNT_INVALID", "Thiếu thông tin phiếu/sản phẩm để đếm lô.")
        }
        if (normalized.isEmpty()) return failure("IC_LOT_EMPTY", "Mã lô rỗng.")
        if (countQty <= 0) return failure("IC_LOT_QTY_INVALID", "Số lượng phải lớn hơn 0.")

        val lotId = resolveLotId(header, line, normalized).getOrElse { return Result.failure(it) }
        val payload = JSONObject()
            .put("p_header_id", headerIdInt)
            .put("p_product_id", productIdInt)
            .put("p_location_id", (locationId ?: line.locationId)?.toIntId() ?: JSONObject.NULL)
            .put("p_lot_id", lotId.toIntId() ?: lotId)
            .put("p_serial_id", JSONObject.NULL)
            .put("p_qty_counted", countQty)
            .put("p_note", "scan from app")
        return addDetail(payload)
            .map { IcCountSuccess(line.id, countQty, "Đã đếm lô $normalized × ${formatQty(countQty)}.") }
    }

    override suspend fun countSerial(
        header: IcHeader,
        line: IcLine,
        locationId: String?,
        code: String,
    ): Result<IcCountSuccess> {
        val headerIdInt = header.id.toIntId()
        val productIdInt = line.productId?.toIntId()
        val normalized = code.trim()
        if (headerIdInt == null) return failure("IC_COUNT_INVALID", "Thiếu id phiếu để đếm serial.")
        if (normalized.isEmpty()) return failure("IC_SERIAL_EMPTY", "Serial rỗng.")

        val resolved = resolveSerial(header, line, normalized).getOrElse { return Result.failure(it) }
        val payload = JSONObject()
            .put("p_header_id", headerIdInt)
            .put("p_product_id", resolved.productId?.toIntId() ?: productIdInt ?: JSONObject.NULL)
            .put("p_location_id", (locationId ?: line.locationId)?.toIntId() ?: JSONObject.NULL)
            .put("p_lot_id", JSONObject.NULL)
            .put("p_serial_id", resolved.serialId.toIntId() ?: resolved.serialId)
            .put("p_qty_counted", 1)
            .put("p_note", "scan from app")
        return addDetail(payload)
            .map { IcCountSuccess(line.id, 1.0, "Đã đếm serial $normalized.") }
    }

    override suspend fun finish(headerId: String, note: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("IC_HEADER_INVALID", "ID phiếu kiểm kê không hợp lệ.")
        val trimmedNote = note.trim()
        if (trimmedNote.isEmpty()) return failure("IC_COMPLETE_NOTE_REQUIRED", "Cần ghi chú khi kết thúc phiếu kiểm kê.")
        // COMPLETE_ONLY: chỉ đóng phiếu, KHÔNG điều chỉnh tồn (cân bằng để webapp).
        val body = JSONObject()
            .put("p_id", headerIdInt)
            .put("p_action", "COMPLETE_ONLY")
            .put("p_note", trimmedNote)
        val response = rpc("rpc_ic_complete", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
        val reason = response?.failureReason()
        if (reason != null) return failure("IC_COMPLETE_FAILED", InventoryCountErrorMapper.toUserMessage(reason))
        return Result.success(Unit)
    }

    // region resolve serial / lot (rpc_check_*_scan, fallback như scanner)

    private data class ResolvedSerial(val serialId: String, val productId: String?)

    private suspend fun resolveSerial(header: IcHeader, line: IcLine, code: String): Result<ResolvedSerial> {
        val tenantIdInt = header.tenantId?.toIntId()
        val warehouseIdInt = header.warehouseId?.toIntId()
        val headerIdInt = header.id.toIntId()
        val lineIdInt = line.id.toIntId()
        if (tenantIdInt == null || warehouseIdInt == null || headerIdInt == null) {
            return failure("IC_SERIAL_CTX", "Thiếu tenant/kho/phiếu để kiểm tra serial.")
        }
        val body = JSONObject()
            .put("p_tenant_id", tenantIdInt)
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_code", code)
            .put("p_ic_header_id", headerIdInt)
            .put("p_ic_line_id", lineIdInt ?: JSONObject.NULL)
        val response = rpcWithContextFallback("rpc_check_serial_scan", body, "p_ic_header_id", "p_ic_line_id")
            .getOrElse { return Result.failure(it) }
            ?: return failure("IC_SERIAL_INVALID_RESP", "API trả về dữ liệu serial không hợp lệ.")

        val ok = response.optBooleanOrNull("ok")
        val exists = response.optBooleanOrNull("exists")
        val reason = response.optStringOrNull("reason")
        val serial = response.optJSONObject("serial")
        val serialId = serial?.optIdString("id")
        val state = response.optJSONObject("state")
        val alreadyScanned = state?.optBooleanOrNull("already_scanned_in_ic") == true
        val canCount = response.optBooleanOrNull("can_count")
            ?: response.optBooleanOrNull("can_receive")
            ?: response.optBooleanOrNull("can_pick")
        val canCreateDetail = response.optBooleanOrNull("can_create_detail")
        val conflict = response.optJSONObject("ic_conflict")

        if (conflict != null || alreadyScanned) {
            return failure("IC_SERIAL_DUPLICATE", "Serial \"$code\" đã được quét trong một phiếu kiểm kê.")
        }
        if (ok != true || exists != true || serialId == null) {
            return failure("IC_SERIAL_INVALID", InventoryCountErrorMapper.toUserMessage(reason))
        }
        if (canCreateDetail == false || canCount == false) {
            return failure("IC_SERIAL_NOT_COUNTABLE", InventoryCountErrorMapper.toUserMessage(reason))
        }
        return Result.success(ResolvedSerial(serialId, serial.optIdString("product_id")))
    }

    private suspend fun resolveLotId(header: IcHeader, line: IcLine, code: String): Result<String> {
        val tenantIdInt = header.tenantId?.toIntId()
        val warehouseIdInt = header.warehouseId?.toIntId()
        val headerIdInt = header.id.toIntId()
        val lineIdInt = line.id.toIntId()
        if (tenantIdInt == null || warehouseIdInt == null || headerIdInt == null) {
            return failure("IC_LOT_CTX", "Thiếu tenant/kho/phiếu để kiểm tra lô.")
        }
        val body = JSONObject()
            .put("p_tenant_id", tenantIdInt)
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_code", code)
            .put("p_ic_header_id", headerIdInt)
            .put("p_ic_line_id", lineIdInt ?: JSONObject.NULL)
        val response = rpcWithContextFallback("rpc_check_lot_scan", body, "p_ic_header_id", "p_ic_line_id")
            .getOrElse { return Result.failure(it) }
            ?: return failure("IC_LOT_INVALID_RESP", "API trả về dữ liệu lô không hợp lệ.")

        val ok = response.optBooleanOrNull("ok")
        val exists = response.optBooleanOrNull("exists")
        val reason = response.optStringOrNull("reason")
        if (ok != true || exists != true) {
            return failure("IC_LOT_INVALID", InventoryCountErrorMapper.toUserMessage(reason))
        }
        val lot = response.optJSONObject("lot")
        val lotProductId = lot?.optIdString("product_id")
        var lotId = lot?.optIdString("id")

        // Fallback: lô không khớp sản phẩm hoặc thiếu id → tra bảng lots theo lot_number.
        val expectedProductId = line.productId
        if (expectedProductId != null && (lotId == null || (lotProductId != null && lotProductId != expectedProductId))) {
            val rows = restGet(
                table = "lots",
                queryParams = mapOf(
                    "select" to "id,product_id",
                    "lot_number" to "eq.$code",
                    "limit" to "50",
                ),
            ).getOrElse { return Result.failure(it) }
            val match = rows.firstOrNull { it.optIdString("product_id") == expectedProductId }
                ?: return if (rows.isNotEmpty()) {
                    failure("IC_LOT_PRODUCT_MISMATCH", "Mã lô không thuộc sản phẩm đang kiểm kê.")
                } else {
                    failure("IC_LOT_NOT_FOUND", InventoryCountErrorMapper.toUserMessage(reason ?: "lot_not_found"))
                }
            lotId = match.optIdString("id") ?: lotId
        }

        return lotId?.let { Result.success(it) }
            ?: failure("IC_LOT_NOT_FOUND", InventoryCountErrorMapper.toUserMessage(reason ?: "lot_not_found"))
    }

    // endregion

    // region ic_details write (rpc_ic_add_detail)

    private suspend fun addDetail(payload: JSONObject): Result<Unit> {
        val response = rpc("rpc_ic_add_detail", payload)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
        val reason = response?.failureReason()
        if (reason != null) return failure("IC_ADD_DETAIL_FAILED", InventoryCountErrorMapper.toUserMessage(reason))
        return Result.success(Unit)
    }

    /** Nếu RPC không nhận param context (signature cũ) thì gọi lại không kèm các key đó. */
    private suspend fun rpcWithContextFallback(
        function: String,
        body: JSONObject,
        vararg contextKeys: String,
    ): Result<JSONObject?> {
        val first = rpc(function, body).mapCatching { unwrapObject(it) }
        if (first.isSuccess) return first
        val err = first.exceptionOrNull()
        if (err != null && InventoryCountErrorMapper.isMissingRpcFunction(err.userMessage())) {
            val legacy = JSONObject(body.toString())
            contextKeys.forEach { legacy.remove(it) }
            return rpc(function, legacy).mapCatching { unwrapObject(it) }
        }
        return first
    }

    // endregion

    // region HTTP helpers (mirror DefaultGoodsReceiptRepository)

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
                    logger.error(TAG, "IC '$label' lỗi HTTP $status: ${backendMessage ?: data.body}")
                    when {
                        status == HTTP_UNAUTHORIZED -> failure(
                            "SESSION_EXPIRED",
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                        )

                        status == HTTP_FORBIDDEN || InventoryCountErrorMapper.isPermissionError(backendMessage) -> failure(
                            "PERMISSION_DENIED",
                            "Bạn không có quyền thao tác phiếu kiểm kê hoặc kho đã chọn.",
                        )

                        else -> Result.failure(
                            AppException(
                                AppError(
                                    code = "IC_REQUEST_FAILED",
                                    message = InventoryCountErrorMapper.toUserMessage(backendMessage),
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

    private fun parseHeader(row: JSONObject): IcHeader? {
        val id = row.optIdString("id") ?: return null
        val warehouse = row.optJSONObject("warehouse") ?: row.optJSONObject("warehouses")
        return IcHeader(
            id = id,
            tenantId = row.optIdString("tenant_id"),
            warehouseId = row.optIdString("warehouse_id"),
            code = row.optStringOrNull("code") ?: row.optStringOrNull("doc_no"),
            status = IcStatus.parse(row.optStringOrNull("status")),
            warehouseLabel = warehouse?.let { w -> w.optStringOrNull("name") ?: w.optStringOrNull("code") },
            countMode = row.optStringOrNull("count_mode"),
            note = row.optStringOrNull("notes") ?: row.optStringOrNull("note"),
            updatedAt = row.optStringOrNull("updated_at") ?: row.optStringOrNull("created_at"),
        )
    }

    private fun parseLine(row: JSONObject): IcLine? {
        val id = row.optIdString("id") ?: return null
        val product = row.optJSONObject("product") ?: row.optJSONObject("products")
        val productId = row.optIdString("product_id") ?: product?.optIdString("id")
        val code = product?.optStringOrNull("code")
            ?: row.optStringOrNull("product_code")
            ?: productId
            ?: id
        val name = product?.optStringOrNull("name") ?: row.optStringOrNull("product_name")
        val tracking = IcTrackingType.parse(
            row.optStringOrNull("tracking_type") ?: product?.optStringOrNull("tracking_type"),
        )
        val lineUom = row.optJSONObject("uom") ?: row.optJSONObject("uoms")
        val productUom = product?.optJSONObject("uom") ?: product?.optJSONObject("uoms")
        val location = row.optJSONObject("location") ?: row.optJSONObject("locations")
        val expected = row.optDoubleOrNull("qty_snapshot")
            ?: row.optDoubleOrNull("quantity_expected")
            ?: row.optDoubleOrNull("quantity_system")
            ?: 0.0
        val counted = row.optDoubleOrNull("qty_counted")
            ?: row.optDoubleOrNull("quantity_counted")
            ?: row.optDoubleOrNull("counted_qty")
            ?: 0.0
        return IcLine(
            id = id,
            productId = productId,
            productCode = code,
            productName = name,
            trackingType = tracking,
            expectedQty = expected,
            countedQty = counted,
            locationId = row.optIdString("location_id") ?: location?.optIdString("id"),
            locationLabel = location?.let { it.optStringOrNull("code") ?: it.optStringOrNull("name") },
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

    /** Trả reason nếu RPC báo thất bại (success=false / ok=false), ngược lại null. */
    private fun JSONObject.failureReason(): String? {
        val success = optBooleanOrNull("success")
        val ok = optBooleanOrNull("ok")
        if (success == false || ok == false) {
            return optStringOrNull("message")
                ?: optStringOrNull("error")
                ?: optStringOrNull("reason")
                ?: "unknown_error"
        }
        return null
    }

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
        const val TAG = "InventoryCountRepository"
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SERVER_ERROR = 500
        const val HEADER_SELECT =
            "id,tenant_id,warehouse_id,code,status,count_mode,count_date,notes,updated_at,created_at," +
                "warehouse:warehouses(id,code,name)"
        const val LINE_SELECT =
            "id,ic_header_id,product_id,location_id,lot_id,qty_snapshot,qty_counted,qty_diff," +
                "product:products(id,code,name,tracking_type,picking_strategy," +
                "require_manufacture_date,require_expiry_date,uom_id,uom:uoms(id,code,name))," +
                "location:locations(id,code,name)"
    }
}
