package vn.delfi.xcloudwms.core.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.delfi.xcloudwms.core.config.ConnectionConfig
import vn.delfi.xcloudwms.core.scanner.BroadcastScannerConfig

class AppPreferences(
    context: Context,
    private val defaultConnectionConfig: ConnectionConfig? = null,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableConnectionConfig = MutableStateFlow(loadConnectionConfig())
    val connectionConfig: StateFlow<ConnectionConfig?> = mutableConnectionConfig.asStateFlow()

    private val mutableBroadcastScannerConfig = MutableStateFlow(loadBroadcastScannerConfig())
    val broadcastScannerConfig: StateFlow<BroadcastScannerConfig> =
        mutableBroadcastScannerConfig.asStateFlow()

    private val mutableBlockSoftKeyboard = MutableStateFlow(loadBlockSoftKeyboard())
    private val mutableAutoSubmitScanInput = MutableStateFlow(loadAutoSubmitScanInput())

    /**
     * Bật/tắt việc chặn bàn phím ảo của thiết bị trong toàn app. Mặc định BẬT để tối ưu thao tác
     * trên PDA/thiết bị có bàn phím cứng; tắt khi muốn nhập tay bằng bàn phím ảo như bình thường.
     */
    val blockSoftKeyboard: StateFlow<Boolean> = mutableBlockSoftKeyboard.asStateFlow()

    /**
     * Khi BẬT, mã quét từ PDA sẽ được xử lý ngay như thao tác Enter/Tab sau khi nhận đủ chuỗi.
     * Khi TẮT, app chỉ đưa mã vào ô quét và chờ người dùng bấm nút xác nhận.
     */
    val autoSubmitScanInput: StateFlow<Boolean> = mutableAutoSubmitScanInput.asStateFlow()

    fun setBlockSoftKeyboard(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BLOCK_SOFT_KEYBOARD, enabled)
            .apply()
        mutableBlockSoftKeyboard.value = enabled
    }

    fun setAutoSubmitScanInput(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTO_SUBMIT_SCAN_INPUT, enabled)
            .apply()
        mutableAutoSubmitScanInput.value = enabled
    }

    fun currentConnectionConfig(): ConnectionConfig? = mutableConnectionConfig.value

    fun currentBroadcastScannerConfig(): BroadcastScannerConfig = mutableBroadcastScannerConfig.value

    fun saveBroadcastScannerConfig(config: BroadcastScannerConfig) {
        sharedPreferences.edit()
            .putString(KEY_BROADCAST_ACTION, config.action)
            .putString(KEY_BROADCAST_DATA_KEY, config.dataExtraKey)
            .putString(KEY_BROADCAST_SYMBOLOGY_KEY, config.symbologyExtraKey)
            .putBoolean(KEY_BROADCAST_ENABLED, config.enabled)
            .apply()
        mutableBroadcastScannerConfig.value = config
    }

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
        mutableConnectionConfig.value = defaultConnectionConfig
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
        ) ?: defaultConnectionConfig
    }

    private fun warehousePreferenceKey(userId: String): String {
        return "$KEY_SELECTED_WAREHOUSE_PREFIX${userId.trim()}"
    }

    private fun loadBlockSoftKeyboard(): Boolean {
        return sharedPreferences.getBoolean(KEY_BLOCK_SOFT_KEYBOARD, true)
    }

    private fun loadAutoSubmitScanInput(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_SUBMIT_SCAN_INPUT, true)
    }

    private fun loadBroadcastScannerConfig(): BroadcastScannerConfig {
        return BroadcastScannerConfig(
            action = sharedPreferences.getString(KEY_BROADCAST_ACTION, "").orEmpty(),
            dataExtraKey = sharedPreferences.getString(KEY_BROADCAST_DATA_KEY, "").orEmpty(),
            symbologyExtraKey = sharedPreferences.getString(KEY_BROADCAST_SYMBOLOGY_KEY, "").orEmpty(),
            enabled = sharedPreferences.getBoolean(KEY_BROADCAST_ENABLED, false),
        )
    }

    private companion object {
        const val PREFS_NAME = "xcloud_wms_preferences"
        const val KEY_CONNECTION_URL = "connection_url"
        const val KEY_CONNECTION_ANON_KEY = "connection_anon_key"
        const val KEY_SELECTED_WAREHOUSE_PREFIX = "selected_warehouse_"
        const val KEY_BROADCAST_ACTION = "broadcast_scanner_action"
        const val KEY_BROADCAST_DATA_KEY = "broadcast_scanner_data_key"
        const val KEY_BROADCAST_SYMBOLOGY_KEY = "broadcast_scanner_symbology_key"
        const val KEY_BROADCAST_ENABLED = "broadcast_scanner_enabled"
        const val KEY_BLOCK_SOFT_KEYBOARD = "block_soft_keyboard"
        const val KEY_AUTO_SUBMIT_SCAN_INPUT = "auto_submit_scan_input"
    }
}
