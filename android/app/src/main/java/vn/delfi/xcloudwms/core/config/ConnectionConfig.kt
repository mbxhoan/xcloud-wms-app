package vn.delfi.xcloudwms.core.config

import java.net.URI

data class ConnectionConfig(
    val url: String,
    val anonKey: String,
) {
    val normalizedUrl: String = url.trim().removeSuffix("/")

    val hostLabel: String = runCatching {
        URI(normalizedUrl).host?.takeIf { it.isNotBlank() } ?: normalizedUrl
    }.getOrDefault(normalizedUrl)

    companion object {
        fun create(
            url: String,
            anonKey: String,
        ): ConnectionConfig? {
            val normalizedUrl = url.trim()
            val normalizedKey = anonKey.trim()
            if (normalizedUrl.isBlank() || normalizedKey.isBlank()) {
                return null
            }
            return ConnectionConfig(
                url = normalizedUrl,
                anonKey = normalizedKey,
            )
        }
    }
}
