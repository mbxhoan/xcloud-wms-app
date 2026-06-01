package vn.delfi.xcloudwms.core.scanner

/**
 * Cách một mã quét được thu nhận. Feature screen không bao giờ phụ thuộc trực tiếp vào
 * nguồn này — mọi nguồn đều đi qua [ScannerManager].
 */
enum class ScanSource(val label: String) {
    SDK("Bộ SDK"),
    BROADCAST("Tín hiệu phát"),
    KEYBOARD_WEDGE("Phím quét"),
    CAMERA("Máy ảnh"),
    MANUAL("Thủ công"),
}
