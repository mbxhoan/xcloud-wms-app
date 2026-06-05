package vn.delfi.xcloudwms.core.scanner

/**
 * Cách app xử lý sau khi nhận đủ một mã quét.
 *
 * - [ENTER]: xử lý ngay mã vừa quét ở step hiện tại.
 * - [TAB]: giữ mã/giá trị hiện tại và chuyển focus hoặc bước quét sang field kế tiếp nếu màn có.
 * - [NONE]: chỉ điền giá trị, không tự thao tác thêm.
 */
enum class ScannerSubmitMode {
    ENTER,
    TAB,
    NONE,
}
