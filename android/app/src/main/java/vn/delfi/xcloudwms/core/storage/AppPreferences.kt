package vn.delfi.xcloudwms.core.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.delfi.xcloudwms.core.config.ConnectionConfig

class AppPreferences(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableConnectionConfig = MutableStateFlow(loadConnectionConfig())
    val connectionConfig: StateFlow<ConnectionConfig?> = mutableConnectionConfig.asStateFlow()

    fun currentConnectionConfig(): ConnectionConfig? = mutableConnectionConfig.value

    fun saveConnectionConfig(config: ConnectionConfig) {
        sharedPreferences.edit()
            .putString(KEY_CONNECTION_URL, config.normalizedUrl)
            .putString(KEY_CONNECTION_ANON_KEY, config.anonKey)
            .apply()
        mutableConnectionConfig.value = config
    }

    fun clearConnectionConfig() {
        sharedPreferences.edit()
            .remove(KEY_CONNECTION_URL)
            .remove(KEY_CONNECTION_ANON_KEY)
            .apply()
        mutableConnectionConfig.value = null
    }

    fun getSelectedWarehouseId(userId: String): String? {
        return sharedPreferences.getString(
            warehousePreferenceKey(userId),
            null,
        )?.trim()?.takeIf { it.isNotBlank() }
    }

    fun saveSelectedWarehouseId(
        userId: String,
        warehouseId: String,
    ) {
        sharedPreferences.edit()
            .putString(warehousePreferenceKey(userId), warehouseId.trim())
            .apply()
    }

    fun clearSelectedWarehouseId(userId: String) {
        sharedPreferences.edit()
            .remove(warehousePreferenceKey(userId))
            .apply()
    }

    fun clearAllWarehouseSelections() {
        val keysToClear = sharedPreferences.all.keys.filter {
            it.startsWith(KEY_SELECTED_WAREHOUSE_PREFIX)
        }
        if (keysToClear.isEmpty()) {
            return
        }
        val editor = sharedPreferences.edit()
        keysToClear.forEach(editor::remove)
        editor.apply()
    }

    private fun loadConnectionConfig(): ConnectionConfig? {
        val url = sharedPreferences.getString(KEY_CONNECTION_URL, null)
        val anonKey = sharedPreferences.getString(KEY_CONNECTION_ANON_KEY, null)
        return ConnectionConfig.create(
            url = url.orEmpty(),
            anonKey = anonKey.orEmpty(),
        )
    }

    private fun warehousePreferenceKey(userId: String): String {
        return "$KEY_SELECTED_WAREHOUSE_PREFIX${userId.trim()}"
    }

    private companion object {
        const val PREFS_NAME = "xcloud_wms_preferences"
        const val KEY_CONNECTION_URL = "connection_url"
        const val KEY_CONNECTION_ANON_KEY = "connection_anon_key"
        const val KEY_SELECTED_WAREHOUSE_PREFIX = "selected_warehouse_"
    }
}
