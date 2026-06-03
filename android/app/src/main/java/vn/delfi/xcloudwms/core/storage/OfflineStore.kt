package vn.delfi.xcloudwms.core.storage

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Lưu cache danh mục nhẹ + draft local chưa submit (xem `docs/06` mục 2-3) dưới dạng JSON string.
 *
 * KHÔNG lưu token/secret ở đây — token để [vn.delfi.xcloudwms.core.security.SecureSessionStorage].
 * Đây chỉ là cache tiện ích để thao tác mượt khi mạng yếu, backend vẫn là nguồn sự thật.
 */
class OfflineStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(key: String, json: String) {
        prefs.edit().putString(key, json).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Id cài đặt ổn định (không phải PII) để gắn vào request id idempotency. Tạo một lần rồi giữ. */
    fun deviceInstallId(): String {
        prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        return generated
    }

    private companion object {
        const val PREFS_NAME = "xcloud_wms_offline"
        const val KEY_INSTALL_ID = "device_install_id"
    }
}
