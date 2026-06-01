package vn.delfi.xcloudwms.core.scanner

/**
 * Sự kiện quét đã được [ScannerManager] xử lý xong (chuẩn hoá + phân tích).
 * Đây là model nội bộ phía Android, không thuộc DB/RPC/API contract dùng chung.
 */
sealed interface ScanEvent {
    data class Success(
        val raw: String,
        val parsed: ParsedBarcode,
        val source: ScanSource,
        val timestamp: Long,
        val symbology: String? = null,
    ) : ScanEvent

    data class Error(
        val message: String,
        val source: ScanSource,
    ) : ScanEvent
}
