package vn.delfi.xcloudwms.data.stock

import org.json.JSONArray
import org.json.JSONObject
import vn.delfi.xcloudwms.core.storage.OfflineStore
import vn.delfi.xcloudwms.domain.model.LookupHistoryItem
import vn.delfi.xcloudwms.domain.model.StockLookupResult

/**
 * Lưu lịch sử tra cứu gần đây trên thiết bị (JSON trong [OfflineStore]). Parity scanner PWA
 * (`xcloud_lookup_history`, mới nhất lên đầu, tối đa [LIMIT]). Chỉ đẩy mã có match.
 */
class LookupHistoryStore(
    private val offlineStore: OfflineStore,
) {
    fun load(): List<LookupHistoryItem> {
        val raw = offlineStore.get(KEY) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.let(::parseItem) }
                .sortedByDescending { it.updatedAt }
                .take(LIMIT)
        }.getOrDefault(emptyList())
    }

    /** Thêm/đưa lên đầu mã vừa tra (có match). Trả về danh sách mới đã lưu. */
    fun push(result: StockLookupResult): List<LookupHistoryItem> {
        val match = result.match ?: return load()
        val code = result.query.trim()
        if (code.isBlank()) return load()

        val next = LookupHistoryItem(
            code = code,
            updatedAt = System.currentTimeMillis(),
            matchKind = match.kind,
            productLabel = buildProductLabel(match.productCode, match.productName, match.label, match.code),
        )
        val deduped = load().filterNot { it.code.equals(code, ignoreCase = true) }
        val merged = (listOf(next) + deduped).take(LIMIT)
        save(merged)
        return merged
    }

    private fun save(items: List<LookupHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("code", item.code)
                    .put("updatedAt", item.updatedAt)
                    .put("matchKind", item.matchKind ?: JSONObject.NULL)
                    .put("productLabel", item.productLabel ?: JSONObject.NULL),
            )
        }
        offlineStore.put(KEY, array.toString())
    }

    private fun parseItem(obj: JSONObject): LookupHistoryItem? {
        val code = obj.optString("code").trim().takeIf { it.isNotBlank() } ?: return null
        return LookupHistoryItem(
            code = code,
            updatedAt = obj.optLong("updatedAt", 0L),
            matchKind = obj.optString("matchKind").takeIf { it.isNotBlank() && it != "null" },
            productLabel = obj.optString("productLabel").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun buildProductLabel(
        productCode: String?,
        productName: String?,
        label: String?,
        code: String?,
    ): String? = when {
        !productCode.isNullOrBlank() && !productName.isNullOrBlank() -> "$productCode — $productName"
        !productCode.isNullOrBlank() -> productCode
        !productName.isNullOrBlank() -> productName
        else -> label ?: code
    }

    private companion object {
        const val KEY = "xcloud_lookup_history"
        const val LIMIT = 80
    }
}
