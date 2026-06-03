package vn.delfi.xcloudwms.data.putaway

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.storage.OfflineStore
import vn.delfi.xcloudwms.domain.model.PaDraftBuffer
import vn.delfi.xcloudwms.domain.model.PaLocation
import vn.delfi.xcloudwms.domain.model.PaProduct
import vn.delfi.xcloudwms.domain.model.PaTrackingType

/**
 * Cache PA offline-lite (xem `docs/06`): danh mục vị trí/sản phẩm theo kho để xem được khi mất
 * mạng, và buffer nhập liệu đang soạn dở. Backend vẫn là nguồn sự thật; cache chỉ phục vụ đọc/UX.
 */
class PaOfflineCache(
    private val store: OfflineStore,
    private val logger: SafeLogger,
) {
    fun saveLocations(warehouseId: String, locations: List<PaLocation>) {
        runCatching {
            val array = JSONArray()
            locations.forEach { loc ->
                array.put(
                    JSONObject()
                        .put("id", loc.id)
                        .put("code", loc.code)
                        .putOpt("name", loc.name),
                )
            }
            store.put(locationsKey(warehouseId), array.toString())
        }.onFailure { logger.error(TAG, "Lưu cache vị trí PA lỗi: ${it.message}") }
    }

    fun loadLocations(warehouseId: String): List<PaLocation>? {
        val raw = store.get(locationsKey(warehouseId)) ?: return null
        return runCatching {
            parseArray(raw).mapNotNull { row ->
                val id = row.optStringOrNull("id") ?: return@mapNotNull null
                PaLocation(
                    id = id,
                    code = row.optStringOrNull("code") ?: id,
                    name = row.optStringOrNull("name"),
                )
            }
        }.getOrNull()
    }

    fun saveProducts(warehouseId: String, products: List<PaProduct>) {
        runCatching {
            val array = JSONArray()
            products.forEach { p ->
                array.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("code", p.code)
                        .putOpt("name", p.name)
                        .put("tracking_type", p.trackingType.name)
                        .putOpt("available_qty", p.availableQty)
                        .putOpt("uom_id", p.uomId)
                        .putOpt("uom_label", p.uomLabel),
                )
            }
            store.put(productsKey(warehouseId), array.toString())
        }.onFailure { logger.error(TAG, "Lưu cache sản phẩm PA lỗi: ${it.message}") }
    }

    fun loadProducts(warehouseId: String): List<PaProduct>? {
        val raw = store.get(productsKey(warehouseId)) ?: return null
        return runCatching {
            parseArray(raw).mapNotNull { row ->
                val id = row.optStringOrNull("id") ?: return@mapNotNull null
                PaProduct(
                    id = id,
                    code = row.optStringOrNull("code") ?: id,
                    name = row.optStringOrNull("name"),
                    trackingType = PaTrackingType.parse(row.optStringOrNull("tracking_type")),
                    availableQty = row.optDoubleOrNull("available_qty"),
                    uomId = row.optStringOrNull("uom_id"),
                    uomLabel = row.optStringOrNull("uom_label"),
                )
            }
        }.getOrNull()
    }

    fun saveDraft(buffer: PaDraftBuffer) {
        runCatching {
            val obj = JSONObject()
                .put("warehouse_id", buffer.warehouseId)
                .putOpt("session_id", buffer.sessionId)
                .put("from_location_id", buffer.fromLocationId)
                .put("to_location_id", buffer.toLocationId)
                .putOpt("selected_product_id", buffer.selectedProductId)
                .put("scanned_code", buffer.scannedCode)
                .put("qty_text", buffer.qtyText)
                .put("line_notes", buffer.lineNotes)
                .put("session_notes", buffer.sessionNotes)
                .put("updated_at", buffer.updatedAt)
            store.put(draftKey(buffer.warehouseId), obj.toString())
        }.onFailure { logger.error(TAG, "Lưu draft PA lỗi: ${it.message}") }
    }

    fun loadDraft(warehouseId: String): PaDraftBuffer? {
        val raw = store.get(draftKey(warehouseId)) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            PaDraftBuffer(
                warehouseId = obj.optStringOrNull("warehouse_id") ?: warehouseId,
                sessionId = obj.optStringOrNull("session_id"),
                fromLocationId = obj.optString("from_location_id", ""),
                toLocationId = obj.optString("to_location_id", ""),
                selectedProductId = obj.optStringOrNull("selected_product_id"),
                scannedCode = obj.optString("scanned_code", ""),
                qtyText = obj.optString("qty_text", "1"),
                lineNotes = obj.optString("line_notes", ""),
                sessionNotes = obj.optString("session_notes", ""),
                updatedAt = obj.optLong("updated_at", 0L),
            )
        }.getOrNull()
    }

    fun clearDraft(warehouseId: String) {
        store.remove(draftKey(warehouseId))
    }

    private fun parseArray(raw: String): List<JSONObject> {
        return when (val value = JSONTokener(raw).nextValue()) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> listOf(value)
            else -> emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun locationsKey(warehouseId: String) = "pa_locations_$warehouseId"
    private fun productsKey(warehouseId: String) = "pa_products_$warehouseId"
    private fun draftKey(warehouseId: String) = "pa_draft_$warehouseId"

    private companion object {
        const val TAG = "PaOfflineCache"
    }
}
