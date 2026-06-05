package vn.delfi.xcloudwms.core.config

import android.util.Base64
import org.json.JSONObject

private const val WMS_CONFIG_QR_PREFIX = "XCWMS1:"

fun looksLikeConnectionConfigQr(raw: String): Boolean {
    return raw.trim().startsWith(WMS_CONFIG_QR_PREFIX)
}

fun parseConnectionConfigQr(raw: String): ConnectionConfig? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith(WMS_CONFIG_QR_PREFIX)) {
        return null
    }

    return runCatching {
        val payload = trimmed.removePrefix(WMS_CONFIG_QR_PREFIX)
        val decoded = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
        val json = JSONObject(decoded)
        ConnectionConfig.create(
            url = json.optString("url").trim(),
            anonKey = json.optString("key").ifBlank {
                json.optString("anonKey")
            }.trim(),
        )
    }.getOrNull()
}
