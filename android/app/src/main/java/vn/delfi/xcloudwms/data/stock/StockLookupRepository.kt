package vn.delfi.xcloudwms.data.stock

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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
import vn.delfi.xcloudwms.domain.model.StockActiveLpn
import vn.delfi.xcloudwms.domain.model.StockEvent
import vn.delfi.xcloudwms.domain.model.StockLookupResult
import vn.delfi.xcloudwms.domain.model.StockLpnContent
import vn.delfi.xcloudwms.domain.model.StockMatch
import vn.delfi.xcloudwms.domain.model.StockRow
import vn.delfi.xcloudwms.domain.model.StockSummary

interface StockLookupRepository {
    /** Tra cứu tồn read-only theo mã đã chuẩn hoá. */
    suspend fun lookup(query: String): Result<StockLookupResult>
}

class DefaultStockLookupRepository(
    private val networkClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val secureSessionStorage: SecureSessionStorage,
    private val logger: SafeLogger,
) : StockLookupRepository {

    override suspend fun lookup(query: String): Result<StockLookupResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return failure("LOOKUP_EMPTY", "Vui lòng nhập hoặc quét mã cần tra cứu.")
        }

        val connectionConfig = appPreferences.currentConnectionConfig()
            ?: return failure("SESSION_REQUIRED", "Chưa cấu hình kết nối. Vui lòng đăng nhập lại.")

        val accessToken = secureSessionStorage.loadSession()?.accessToken
        if (accessToken.isNullOrBlank()) {
            return failure("SESSION_REQUIRED", "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.")
        }

        val body = JSONObject().put("p_query", normalized).toString()
        val result = networkClient.execute(
            NetworkRequest(
                connectionConfig = connectionConfig,
                path = "/rest/v1/rpc/rpc_traceability_lookup",
                method = HttpMethod.POST,
                body = body,
                authToken = accessToken,
            ),
        )

        return when (result) {
            is NetworkResult.Failure -> Result.failure(AppException(result.error))
            is NetworkResult.Success -> handleResponse(normalized, result.data)
        }
    }

    private fun handleResponse(query: String, response: NetworkResponse): Result<StockLookupResult> {
        val status = response.statusCode
        if (status in HTTP_OK..HTTP_SUCCESS_MAX) {
            return runCatching { StockLookupResponseParser.parse(response.body, query) }
                .fold(
                    onSuccess = { Result.success(it) },
                    onFailure = {
                        logger.error(TAG, "Phân tích phản hồi tra cứu thất bại: ${it.message}")
                        failure("LOOKUP_PARSE_FAILED", "Dữ liệu trả về không hợp lệ.")
                    },
                )
        }

        val backendMessage = extractBackendMessage(response.body)
        logger.error(TAG, "Tra cứu lỗi HTTP $status: ${backendMessage ?: response.body}")
        return when {
            status == HTTP_UNAUTHORIZED ->
                failure("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

            status == HTTP_FORBIDDEN || LookupErrorMapper.isPermissionError(backendMessage) ->
                failure("PERMISSION_DENIED", "Bạn không có quyền tra cứu mã này.")

            else -> Result.failure(
                AppException(
                    AppError(
                        code = "LOOKUP_FAILED",
                        message = LookupErrorMapper.toUserMessage(backendMessage),
                        retryable = true,
                    ),
                ),
            )
        }
    }

    private fun extractBackendMessage(body: String?): String? {
        body ?: return null
        return runCatching {
            val obj = JSONObject(body)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: obj.optString("hint").takeIf { it.isNotBlank() }
        }.getOrNull() ?: body
    }

    private fun failure(code: String, message: String): Result<StockLookupResult> =
        Result.failure(AppException(AppError(code = code, message = message)))

    private companion object {
        const val TAG = "StockLookupRepository"
        const val HTTP_OK = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

/**
 * Phân tích jsonb của `rpc_traceability_lookup`. PostgREST trả thẳng object jsonb (đôi khi bọc mảng
 * 1 phần tử) → chấp nhận cả hai. Đọc đủ match/summary/current_rows/active_lpns/lpn_contents/events
 * để dựng màn tra cứu parity scanner PWA.
 */
private object StockLookupResponseParser {
    fun parse(rawBody: String?, fallbackQuery: String): StockLookupResult {
        val root = toObject(rawBody)
            ?: return StockLookupResult(
                query = fallbackQuery,
                match = null,
                summary = StockSummary(),
                rows = emptyList(),
                activeLpns = emptyList(),
                lpnContents = emptyList(),
                events = emptyList(),
                warnings = emptyList(),
            )

        return StockLookupResult(
            query = root.optStringOrNull("query") ?: fallbackQuery,
            match = root.optJSONObject("match")?.takeUnless { root.isNull("match") }?.let(::parseMatch),
            summary = root.optJSONObject("summary")?.let(::parseSummary) ?: StockSummary(),
            rows = root.optJSONArray("current_rows").objects().map(::parseRow),
            activeLpns = root.optJSONArray("active_lpns").objects().map(::parseActiveLpn),
            lpnContents = root.optJSONArray("lpn_contents_preview").objects().map(::parseLpnContent),
            events = root.optJSONArray("events").objects().mapNotNull(::parseEvent),
            warnings = root.optJSONArray("warnings").strings(),
        )
    }

    private fun parseMatch(obj: JSONObject): StockMatch = StockMatch(
        kind = obj.optStringOrNull("kind") ?: "UNKNOWN",
        code = obj.optStringOrNull("code"),
        label = obj.optStringOrNull("label"),
        productCode = obj.optStringOrNull("product_code"),
        productName = obj.optStringOrNull("product_name"),
        trackingType = obj.optStringOrNull("tracking_type"),
        uomCode = obj.optStringOrNull("uom_code"),
        uomName = obj.optStringOrNull("uom_name"),
        status = obj.optStringOrNull("status"),
        referenceCode = obj.optStringOrNull("reference_code"),
        lotNumber = obj.optStringOrNull("lot_number"),
        serialNumber = obj.optStringOrNull("serial_number"),
        lpnCode = obj.optStringOrNull("lpn_code"),
    )

    private fun parseSummary(obj: JSONObject): StockSummary = StockSummary(
        totalOnHand = obj.optDouble("total_on_hand", 0.0),
        totalReserved = obj.optDouble("total_reserved", 0.0),
        totalLocked = obj.optDouble("total_locked", 0.0),
        totalAvailable = obj.optDouble("total_available", 0.0),
        warehouseCount = obj.optInt("warehouse_count", 0),
        locationCount = obj.optInt("location_count", 0),
        activeLpnCount = obj.optInt("active_lpn_count", 0),
        eventCount = obj.optInt("event_count", 0),
    )

    private fun parseActiveLpn(obj: JSONObject): StockActiveLpn = StockActiveLpn(
        lpnCode = obj.optStringOrNull("lpn_code").orEmpty(),
        status = obj.optStringOrNull("status"),
        isSealed = obj.optBooleanLoose("is_sealed"),
        warehouseCode = obj.optStringOrNull("warehouse_code"),
        warehouseName = obj.optStringOrNull("warehouse_name"),
        locationCode = obj.optStringOrNull("location_code"),
        locationName = obj.optStringOrNull("location_name"),
        packedQtyBase = obj.optDouble("packed_qty_base", 0.0),
        contentsCount = obj.optInt("contents_count", 0),
    )

    private fun parseLpnContent(obj: JSONObject): StockLpnContent = StockLpnContent(
        productCode = obj.optStringOrNull("product_code"),
        productName = obj.optStringOrNull("product_name"),
        trackingType = obj.optStringOrNull("tracking_type"),
        lotNumber = obj.optStringOrNull("lot_number"),
        serialNumber = obj.optStringOrNull("serial_number"),
        qtyBase = obj.optDouble("qty_base", 0.0),
        uomCode = obj.optStringOrNull("uom_code"),
        uomName = obj.optStringOrNull("uom_name"),
    )

    private fun parseEvent(obj: JSONObject): StockEvent? {
        val source = obj.optStringOrNull("source")?.uppercase()
        if (source != "STOCK" && source != "LPN" && source != "IC") return null
        val id = obj.optStringOrNull("id") ?: return null
        val title = obj.optStringOrNull("title") ?: return null
        return StockEvent(
            id = id,
            source = source,
            time = obj.optStringOrNull("time"),
            title = title,
            subtitle = obj.optStringOrNull("subtitle"),
            quantityDelta = if (obj.isNull("quantity_delta")) null else obj.optDouble("quantity_delta").takeIf { !it.isNaN() },
            quantityText = obj.optStringOrNull("quantity_text"),
            referenceCode = obj.optStringOrNull("reference_code"),
            actorName = obj.optStringOrNull("actor_name"),
            partnerName = obj.optStringOrNull("partner_name"),
            lpnCode = obj.optStringOrNull("lpn_code"),
            locationCode = obj.optStringOrNull("location_code"),
            fromLocationCode = obj.optStringOrNull("from_location_code"),
            toLocationCode = obj.optStringOrNull("to_location_code"),
            reasonNote = obj.optStringOrNull("reason_note"),
            notes = obj.optStringOrNull("notes"),
        )
    }

    private fun parseRow(obj: JSONObject): StockRow = StockRow(
        warehouseId = obj.optIdString("warehouse_id"),
        warehouseCode = obj.optStringOrNull("warehouse_code"),
        warehouseName = obj.optStringOrNull("warehouse_name"),
        locationCode = obj.optStringOrNull("location_code"),
        locationName = obj.optStringOrNull("location_name"),
        trackingValue = obj.optStringOrNull("tracking_value"),
        quantityOnHand = obj.optDouble("quantity_on_hand", 0.0),
        quantityReserved = obj.optDouble("quantity_reserved", 0.0),
        quantityLocked = obj.optDouble("quantity_locked", 0.0),
        quantityAvailable = obj.optDouble("quantity_available", 0.0),
        inboundDate = obj.optStringOrNull("inbound_date"),
        manufactureDate = obj.optStringOrNull("manufacture_date"),
        expiryDate = obj.optStringOrNull("expiry_date"),
    )

    private fun toObject(rawBody: String?): JSONObject? {
        val trimmed = rawBody?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when (val value = JSONTokener(trimmed).nextValue()) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optBooleanLoose(key: String): Boolean {
        return when (val value = opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
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

    private fun JSONArray?.objects(): List<JSONObject> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONArray?.strings(): List<String> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
