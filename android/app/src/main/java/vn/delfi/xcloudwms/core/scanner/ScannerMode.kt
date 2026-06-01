package vn.delfi.xcloudwms.core.scanner

/**
 * Ngữ cảnh quét hiện tại của màn hình. Dùng làm gợi ý cho [BarcodeParser] khi mã quét
 * không có tiền tố rõ ràng (ví dụ màn nhập serial coi mã trơn là SERIAL).
 */
enum class ScannerMode(val label: String) {
    GENERIC("Tổng quát"),
    LOCATION("Vị trí"),
    PRODUCT("Sản phẩm"),
    LOT("Lô"),
    SERIAL("Số seri"),
    DOCUMENT("Chứng từ"),
}
