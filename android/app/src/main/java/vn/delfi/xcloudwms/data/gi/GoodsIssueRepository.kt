package vn.delfi.xcloudwms.data.gi

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
import vn.delfi.xcloudwms.domain.model.GiHeader
import vn.delfi.xcloudwms.domain.model.GiLine
import vn.delfi.xcloudwms.domain.model.GiPickSuccess
import vn.delfi.xcloudwms.domain.model.GiStatus
import vn.delfi.xcloudwms.domain.model.GiTrackingType

/**
 * Repository cho luồng Goods Issue Picking native. Tất cả thay đổi tồn kho đi qua RPC
 * backend (rpc_gi_*) + ghi `gi_details` qua PostgREST đúng RLS như scanner PWA. Client
 * chỉ optimistic UI, không tự quyết tồn — backend là nguồn sự thật.
 */
interface GoodsIssueRepository {
    /** Danh sách phiếu xuất đang chờ pick trong kho (CREATED/PICKING/PICKED). */
    suspend fun loadAssignedHeaders(warehouseId: String): Result<List<GiHeader>>

    suspend fun loadHeader(headerId: String): Result<GiHeader>

    suspend fun loadLines(headerId: String): Result<List<GiLine>>

    /** Chuyển phiếu CREATED → PICKING (idempotent: bỏ qua nếu đã PICKING). */
    suspend fun startPicking(headerId: String): Result<Unit>

    suspend fun pickSerial(header: GiHeader, line: GiLine, code: String): Result<GiPickSuccess>

    suspend fun pickLot(header: GiHeader, line: GiLine, code: String, qty: Double): Result<GiPickSuccess>

    suspend fun pickQuantity(line: GiLine, qty: Double): Result<GiPickSuccess>

    /** Chốt picking: PICKING → PICKED (hoặc COMPLETED nếu đủ toàn bộ dòng). */
    suspend fun submit(headerId: String): Result<Unit>

    /** Hoàn tất xuất kho phần đã pick: PICKING/PICKED → COMPLETED. */
    suspend fun complete(headerId: String): Result<Unit>
}

class DefaultGoodsIssueRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : GoodsIssueRepository {

    override suspend fun loadAssignedHeaders(warehouseId: String): Result<List<GiHeader>> {
        val warehouseIdInt = warehouseId.toIntId()
            ?: return failure("GI_WAREHOUSE_INVALID", "Kho hiện tại không hợp lệ.")
        val rows = restGet(
            table = "gi_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "warehouse_id" to "eq.$warehouseIdInt",
                "status" to "in.(CREATED,PICKING,PICKED)",
                "order" to "updated_at.desc",
                "limit" to "200",
            ),
        ).getOrElse { return Result.failure(it) }
        val headers = rows.mapNotNull(::parseHeader)
        return Result.success(resolvePartnerLabels(headers))
    }

    override suspend fun loadHeader(headerId: String): Result<GiHeader> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GI_HEADER_INVALID", "ID phiếu xuất không hợp lệ.")
        val rows = restGet(
            table = "gi_headers",
            queryParams = mapOf(
                "select" to HEADER_SELECT,
                "id" to "eq.$headerIdInt",
                "limit" to "1",
            ),
        ).getOrElse { return Result.failure(it) }
        val header = rows.firstNotNullOfOrNull(::parseHeader)
            ?: return failure("GI_NOT_FOUND", "Không tìm thấy phiếu xuất.")
        return Result.success(resolvePartnerLabels(listOf(header)).first())
    }

    override suspend fun loadLines(headerId: String): Result<List<GiLine>> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GI_HEADER_INVALID", "ID phiếu xuất không hợp lệ.")
        val lineRows = restGet(
            table = "gi_lines",
            queryParams = mapOf(
                "select" to LINE_SELECT,
                "gi_header_id" to "eq.$headerIdInt",
                "order" to "id.asc",
            ),
        ).getOrElse { return Result.failure(it) }

        val parsed = lineRows.mapNotNull { row -> parseLine(row) }
        if (parsed.isEmpty()) return Result.success(emptyList())

        // picked_quantity chuẩn từ tổng gi_details.picked_quantity (qty_issued có thể trễ).
        val pickedByLine = loadPickedTotals(parsed.map { it.id })
        val merged = parsed.map { line ->
            val picked = pickedByLine[line.id]
            if (picked != null) line.copy(pickedQty = picked) else line
        }
        return Result.success(merged.sortedBy { it.productCode.lowercase() })
    }

    private suspend fun loadPickedTotals(lineIds: List<String>): Map<String, Double> {
        val ids = lineIds.mapNotNull { it.toIntId() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = restGet(
            table = "gi_details",
            queryParams = mapOf(
                "select" to "gi_line_id,picked_quantity",
                "gi_line_id" to inFilter(ids.map { it.toString() }),
                "limit" to "5000",
            ),
        ).getOrElse {
            logger.error(TAG, "Tải gi_details để tính picked lỗi: ${it.message}")
            return emptyMap()
        }
        val totals = HashMap<String, Double>()
        for (row in rows) {
            val lineId = row.optIdString("gi_line_id") ?: continue
            val picked = row.optDoubleOrNull("picked_quantity") ?: 0.0
            totals[lineId] = (totals[lineId] ?: 0.0) + picked
        }
        return totals
    }

    override suspend fun startPicking(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GI_HEADER_INVALID", "ID phiếu xuất không hợp lệ.")
        val result = rpc("rpc_gi_start_picking", JSONObject().put("p_gi_id", headerIdInt)).map { }
        // Idempotent: phiếu đã PICKING/PICKED thì coi như đã bắt đầu.
        return result.recoverCatching { throwable ->
            val message = throwable.userMessage().lowercase()
            if (message.contains("already") || message.contains("đã") || message.contains("picking")) {
                Unit
            } else {
                throw throwable
            }
        }
    }

    override suspend fun pickSerial(header: GiHeader, line: GiLine, code: String): Result<GiPickSuccess> {
        val tenantIdInt = header.tenantId?.toIntId()
        val warehouseIdInt = header.warehouseId?.toIntId()
        val headerIdInt = header.id.toIntId()
        val lineIdInt = line.id.toIntId()
        val normalized = code.trim()
        if (tenantIdInt == null || warehouseIdInt == null || headerIdInt == null || lineIdInt == null) {
            return failure("GI_PICK_INVALID", "Thiếu thông tin phiếu/kho/dòng để pick serial.")
        }
        if (normalized.isEmpty()) return failure("GI_SERIAL_EMPTY", "Serial rỗng.")

        val body = JSONObject()
            .put("p_tenant_id", tenantIdInt)
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_code", normalized)
            .put("p_gi_header_id", headerIdInt)
            .put("p_gi_line_id", lineIdInt)
        val response = rpc("rpc_gi_check_serial_scan", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
            ?: return failure("GI_SERIAL_INVALID_RESP", "API trả về dữ liệu serial không hợp lệ.")

        val pickedQty = response.optDoubleOrNull("picked_quantity")
            ?: response.optJSONObject("gi_detail")?.optDoubleOrNull("picked_quantity")
            ?: 0.0
        if (pickedQty > 0) {
            return failure("GI_SERIAL_ALREADY", "Serial này đã được đồng bộ trước đó.")
        }

        val reasonCode = response.optStringOrNull("reason")
            ?: response.optStringOrNull("message")
            ?: response.optStringOrNull("error")
        val action = response.optStringOrNull("action")?.trim()?.lowercase().orEmpty()
        val canPick = response.optBooleanOrNull("can_pick")
        val okFlag = response.optBooleanOrNull("ok")
        val existsFlag = response.optBooleanOrNull("exists")
        val giDetailId = response.optIdString("gi_detail_id")
            ?: response.optStringOrNull("detail_id")
            ?: response.optJSONObject("gi_detail")?.optIdString("id")

        val allowReservedFallback = reasonCode?.trim()?.lowercase() == "serial_reserved_in_other_gi" &&
            giDetailId != null
        val notPickable = (okFlag == false || existsFlag == false || canPick != true) && !allowReservedFallback
        if (notPickable) {
            return failure("GI_SERIAL_NOT_PICKABLE", GoodsIssueErrorMapper.toUserMessage(reasonCode))
        }

        if (action == "bind_serial_to_summary_line") {
            val bindBody = JSONObject()
                .put("p_tenant_id", tenantIdInt)
                .put("p_warehouse_id", warehouseIdInt)
                .put("p_gi_header_id", headerIdInt)
                .put("p_gi_line_id", lineIdInt)
                .put("p_code", normalized)
            return rpc("rpc_gi_bind_serial_to_summary_line", bindBody)
                .map { GiPickSuccess(lineId = line.id, appliedQty = 1.0, message = "Đã pick serial $normalized.") }
        }

        if (giDetailId == null) {
            return failure(
                "GI_SERIAL_NOT_RESERVED",
                GoodsIssueErrorMapper.toUserMessage(reasonCode ?: "serial_not_reserved_in_this_gi"),
            )
        }
        return updateDetailPicked(giDetailId, pickedQuantity = 1.0)
            .map { GiPickSuccess(lineId = line.id, appliedQty = 1.0, message = "Đã pick serial $normalized.") }
    }

    override suspend fun pickLot(
        header: GiHeader,
        line: GiLine,
        code: String,
        qty: Double,
    ): Result<GiPickSuccess> {
        val tenantIdInt = header.tenantId?.toIntId()
        val warehouseIdInt = header.warehouseId?.toIntId()
        val headerIdInt = header.id.toIntId()
        val lineIdInt = line.id.toIntId()
        val normalized = code.trim()
        val pickQty = clampQty(qty)
        if (tenantIdInt == null || warehouseIdInt == null || headerIdInt == null || lineIdInt == null) {
            return failure("GI_PICK_INVALID", "Thiếu thông tin phiếu/kho/dòng để pick lot.")
        }
        if (normalized.isEmpty()) return failure("GI_LOT_EMPTY", "Mã lot rỗng.")
        if (pickQty <= 0) return failure("GI_LOT_QTY_INVALID", "Số lượng phải lớn hơn 0.")

        val body = JSONObject()
            .put("p_tenant_id", tenantIdInt)
            .put("p_warehouse_id", warehouseIdInt)
            .put("p_code", normalized)
            .put("p_gi_header_id", headerIdInt)
            .put("p_gi_line_id", lineIdInt)
        val response = rpc("rpc_gi_check_lot_scan", body)
            .mapCatching { unwrapObject(it) }
            .getOrElse { return Result.failure(it) }
            ?: return failure("GI_LOT_INVALID_RESP", "API trả về dữ liệu lot không hợp lệ.")

        val detail = response.optJSONObject("gi_detail") ?: response.optJSONObject("detail")
        val reasonCode = response.optStringOrNull("reason")
            ?: response.optStringOrNull("message")
            ?: response.optStringOrNull("error")
        val canPick = response.optBooleanOrNull("can_pick")
        val okFlag = response.optBooleanOrNull("ok")
        val existsFlag = response.optBooleanOrNull("exists")
        val giDetailId = response.optIdString("gi_detail_id")
            ?: response.optStringOrNull("detail_id")
            ?: detail?.optIdString("id")
        val giDetailPicked = response.optDoubleOrNull("picked_quantity")
            ?: detail?.optDoubleOrNull("picked_quantity")
            ?: 0.0
        val giDetailQty = response.optDoubleOrNull("quantity") ?: detail?.optDoubleOrNull("quantity")

        val allowReservedFallback = reasonCode?.trim()?.lowercase() == "lot_reserved_in_other_gi" &&
            giDetailId != null && giDetailPicked <= 0
        val notPickable = (okFlag == false || existsFlag == false || canPick != true) && !allowReservedFallback
        if (notPickable) {
            return failure("GI_LOT_NOT_PICKABLE", GoodsIssueErrorMapper.toUserMessage(reasonCode))
        }
        if (giDetailId == null) {
            return failure(
                "GI_LOT_NOT_RESERVED",
                GoodsIssueErrorMapper.toUserMessage(reasonCode ?: "lot_not_reserved_in_this_gi"),
            )
        }
        val remaining = if (giDetailQty != null) giDetailQty - giDetailPicked else null
        if (remaining != null && remaining <= 0) {
            return failure("GI_LOT_FULL", "Lot \"$normalized\" đã được pick đủ trong phiếu hiện tại.")
        }
        if (remaining != null && pickQty > remaining + 1e-9) {
            return failure(
                "GI_LOT_OVER",
                "Số lượng lot vượt phần còn lại trên phiếu (cần ${formatQty(pickQty)}, còn ${formatQty(remaining)}).",
            )
        }

        return updateDetailPicked(giDetailId, pickedQuantity = giDetailPicked + pickQty)
            .map { GiPickSuccess(lineId = line.id, appliedQty = pickQty, message = "Đã pick lot $normalized × ${formatQty(pickQty)}.") }
    }

    override suspend fun pickQuantity(line: GiLine, qty: Double): Result<GiPickSuccess> {
        val lineIdInt = line.id.toIntId()
            ?: return failure("GI_PICK_INVALID", "Thiếu gi_line_id để pick.")
        val pickQty = clampQty(qty)
        if (pickQty <= 0) return failure("GI_QTY_INVALID", "Số lượng phải lớn hơn 0.")

        // Lấy toàn bộ detail NONE của dòng (lot/serial null) để cập nhật đúng phần reserved.
        val rows = restGet(
            table = "gi_details",
            queryParams = mapOf(
                "select" to "id,quantity,picked_quantity,location_id",
                "gi_line_id" to "eq.$lineIdInt",
                "lot_id" to "is.null",
                "serial_id" to "is.null",
                "limit" to "500",
            ),
        ).getOrElse { return Result.failure(it) }

        val totalPicked = rows.sumOf { it.optDoubleOrNull("picked_quantity") ?: 0.0 }
        val totalReserved = rows.sumOf { it.optDoubleOrNull("quantity") ?: 0.0 }
        val remainingToPick = (line.plannedQty - totalPicked).coerceAtLeast(0.0)
        if (pickQty > remainingToPick + 1e-9) {
            return failure(
                "GI_QTY_OVER",
                "Số lượng vượt phần còn cần pick (cần ${formatQty(pickQty)}, còn ${formatQty(remainingToPick)}).",
            )
        }

        // Ưu tiên cập nhật một detail reserved còn chỗ pick (không tăng quantity → tránh overpick trigger).
        val reservedRow = rows.firstOrNull { row ->
            val q = row.optDoubleOrNull("quantity") ?: 0.0
            val p = row.optDoubleOrNull("picked_quantity") ?: 0.0
            q - p >= pickQty - 1e-9
        }
        if (reservedRow != null) {
            val detailId = reservedRow.optIdString("id")
                ?: return failure("GI_DETAIL_NO_ID", "Dòng chi tiết thiếu id, không thể cập nhật.")
            val current = reservedRow.optDoubleOrNull("picked_quantity") ?: 0.0
            return updateDetailPicked(detailId, pickedQuantity = current + pickQty)
                .map { GiPickSuccess(line.id, pickQty, "Đã pick ${formatQty(pickQty)}.") }
        }

        // Không còn reserved → tạo detail mới nếu vẫn trong sức chứa dòng.
        if (totalReserved + pickQty <= line.plannedQty + 1e-9 || line.plannedQty <= 0) {
            val payload = JSONObject()
                .put("gi_line_id", lineIdInt)
                .put("location_id", JSONObject.NULL)
                .put("lot_id", JSONObject.NULL)
                .put("serial_id", JSONObject.NULL)
                .put("quantity", pickQty)
                .put("picked_quantity", pickQty)
            return insertDetail(payload)
                .map { GiPickSuccess(line.id, pickQty, "Đã pick ${formatQty(pickQty)}.") }
        }

        return failure("GI_QTY_NO_CAPACITY", "Không còn sức chứa để pick thêm cho dòng này.")
    }

    override suspend fun submit(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GI_HEADER_INVALID", "ID phiếu xuất không hợp lệ.")
        return rpc("rpc_gi_submit", JSONObject().put("p_gi_id", headerIdInt)).map { }
    }

    override suspend fun complete(headerId: String): Result<Unit> {
        val headerIdInt = headerId.toIntId()
            ?: return failure("GI_HEADER_INVALID", "ID phiếu xuất không hợp lệ.")
        return rpc("rpc_gi_complete", JSONObject().put("p_gi_id", headerIdInt)).map { }
    }

    // region gi_details writes (PostgREST, theo RLS gi.scan/gi.update như scanner PWA)

    private suspend fun updateDetailPicked(detailId: String, pickedQuantity: Double): Result<Unit> {
        val detailIdInt = detailId.toIntId()
            ?: return failure("GI_DETAIL_INVALID", "ID chi tiết xuất không hợp lệ.")
        val body = JSONObject().put("picked_quantity", pickedQuantity)
        return restWrite(
            table = "gi_details",
            method = HttpMethod.PATCH,
            queryParams = mapOf("id" to "eq.$detailIdInt"),
            body = body.toString(),
        ).map { }
    }

    private suspend fun insertDetail(payload: JSONObject): Result<Unit> {
        return restWrite(
            table = "gi_details",
            method = HttpMethod.POST,
            queryParams = emptyMap(),
            body = payload.toString(),
        ).map { }
    }

    // endregion

    // region HTTP helpers (mirror DefaultPutawayRepository)

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
                    logger.error(TAG, "GI '$label' lỗi HTTP $status: ${backendMessage ?: data.body}")
                    when {
                        status == HTTP_UNAUTHORIZED -> failure(
                            "SESSION_EXPIRED",
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                        )

                        status == HTTP_FORBIDDEN || GoodsIssueErrorMapper.isPermissionError(backendMessage) -> failure(
                            "PERMISSION_DENIED",
                            "Bạn không có quyền thao tác phiếu xuất hoặc kho đã chọn.",
                        )

                        else -> Result.failure(
                            AppException(
                                AppError(
                                    code = "GI_REQUEST_FAILED",
                                    message = GoodsIssueErrorMapper.toUserMessage(backendMessage),
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

    private fun parseHeader(row: JSONObject): GiHeader? {
        val id = row.optIdString("id") ?: return null
        val warehouse = row.optJSONObject("warehouse") ?: row.optJSONObject("warehouses")
        return GiHeader(
            id = id,
            tenantId = row.optIdString("tenant_id"),
            warehouseId = row.optIdString("warehouse_id"),
            code = row.optStringOrNull("code"),
            status = GiStatus.parse(row.optStringOrNull("status")),
            warehouseLabel = warehouse?.let { w -> w.optStringOrNull("name") ?: w.optStringOrNull("code") },
            partnerLabel = row.optStringOrNull("partner_label"),
            referenceType = row.optStringOrNull("reference_type"),
            referenceNumber = row.optStringOrNull("reference_number"),
            note = row.optStringOrNull("notes") ?: row.optStringOrNull("note"),
            updatedAt = row.optStringOrNull("updated_at") ?: row.optStringOrNull("created_at"),
        ).withPartnerIdFallback(row.optIdString("partner_id"))
    }

    private fun GiHeader.withPartnerIdFallback(partnerId: String?): GiHeader =
        if (partnerLabel == null && partnerId != null) copy(partnerLabel = partnerId) else this

    private suspend fun resolvePartnerLabels(headers: List<GiHeader>): List<GiHeader> {
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

    private fun parseLine(row: JSONObject): GiLine? {
        val id = row.optIdString("id") ?: return null
        val product = row.optJSONObject("product") ?: row.optJSONObject("products")
        val productId = row.optIdString("product_id") ?: product?.optIdString("id")
        val code = product?.optStringOrNull("code")
            ?: row.optStringOrNull("product_code")
            ?: productId
            ?: id
        val name = product?.optStringOrNull("name") ?: row.optStringOrNull("product_name")
        val tracking = GiTrackingType.parse(
            row.optStringOrNull("tracking_type") ?: product?.optStringOrNull("tracking_type"),
        )
        val lineUom = row.optJSONObject("uom") ?: row.optJSONObject("uoms")
        val productUom = product?.optJSONObject("uom") ?: product?.optJSONObject("uoms")
        val planned = row.optDoubleOrNull("quantity_needed")
            ?: row.optDoubleOrNull("quantity_need")
            ?: row.optDoubleOrNull("quantity")
            ?: 0.0
        val picked = row.optDoubleOrNull("qty_issued")
            ?: row.optDoubleOrNull("picked_quantity")
            ?: 0.0
        return GiLine(
            id = id,
            productId = productId,
            productCode = code,
            productName = name,
            trackingType = tracking,
            plannedQty = planned,
            pickedQty = picked,
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
        const val TAG = "GoodsIssueRepository"
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SERVER_ERROR = 500
        const val HEADER_SELECT =
            "id,tenant_id,warehouse_id,partner_id,code,status,reference_type,reference_number,notes," +
                "ref_gt_id,updated_at,created_at,warehouse:warehouses(id,code,name)"
        const val LINE_SELECT =
            "id,gi_header_id,product_id,uom_id,quantity_needed,qty_issued," +
                "product:products(id,code,name,tracking_type,uom_id,uom:uoms(id,code,name))," +
                "uom:uoms(id,code,name)"
    }
}
