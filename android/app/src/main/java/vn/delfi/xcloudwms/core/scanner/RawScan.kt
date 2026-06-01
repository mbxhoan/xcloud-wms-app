package vn.delfi.xcloudwms.core.scanner

/**
 * Mã quét thô do một [vn.delfi.xcloudwms.core.scanner.adapter.ScannerAdapter] phát ra,
 * trước khi [ScannerManager] chuẩn hoá / chống trùng / phân tích.
 */
data class RawScan(
    val raw: String,
    val source: ScanSource,
    val symbology: String? = null,
)
