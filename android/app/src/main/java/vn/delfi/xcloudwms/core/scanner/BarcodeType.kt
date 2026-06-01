package vn.delfi.xcloudwms.core.scanner

/**
 * Phân loại mã quét theo gợi ý phía client (dựa tiền tố hoặc [ScannerMode]).
 *
 * Lưu ý: đây CHỈ là gợi ý hiển thị/định tuyến tại client. Nguồn phân loại chính thức vẫn là
 * backend lookup (rpc_traceability_lookup) ở phase Stock Lookup — không tự quyết nghiệp vụ ở client.
 */
enum class BarcodeType(val label: String) {
    LOCATION("Vị trí"),
    PRODUCT("Sản phẩm"),
    LOT("Lô"),
    SERIAL("Số seri"),
    DOCUMENT_GR("Phiếu nhập"),
    DOCUMENT_GI("Phiếu xuất"),
    UNKNOWN("Chưa xác định"),
}
