package vn.delfi.xcloudwms.core.scanner

/**
 * Cấu hình adapter broadcast theo từng PDA. KHÔNG hard-code vendor — giá trị do người dùng
 * nhập trong màn Kiểm tra máy quét và lưu vào [vn.delfi.xcloudwms.core.storage.AppPreferences].
 *
 * @param action action của broadcast intent mà app scanner của PDA phát ra.
 * @param dataExtraKey extra key chứa nội dung barcode.
 * @param symbologyExtraKey extra key chứa loại mã (nếu firmware có), có thể bỏ trống.
 * @param enabled bật/tắt nhận broadcast.
 */
data class BroadcastScannerConfig(
    val action: String = "",
    val dataExtraKey: String = "",
    val symbologyExtraKey: String = "",
    val enabled: Boolean = false,
) {
    /** Chỉ đăng ký receiver khi đã bật và khai báo đủ action + data key. */
    val isUsable: Boolean
        get() = enabled && action.isNotBlank() && dataExtraKey.isNotBlank()

    companion object {
        val EMPTY = BroadcastScannerConfig()
    }
}

/**
 * Cấu hình chống trùng (debounce) cho cùng một mã quét.
 *
 * @param intervalMs khoảng thời gian (ms) coi cùng một mã là trùng và bỏ qua.
 * @param continuousSerial khi bật (màn quét serial liên tục), không chặn trùng để cho quét lặp.
 */
data class ScanDebounceConfig(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val continuousSerial: Boolean = false,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 800L
    }
}
