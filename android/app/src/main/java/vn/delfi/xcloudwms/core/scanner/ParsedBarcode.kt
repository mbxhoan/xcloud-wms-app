package vn.delfi.xcloudwms.core.scanner

/**
 * Kết quả phân tích một mã quét thô.
 *
 * @param raw giá trị gốc nhận từ adapter (chỉ trim control char).
 * @param normalized giá trị đã chuẩn hoá (NFKC, gộp dấu gạch/khoảng trắng) — dùng để so trùng/lookup.
 * @param type loại gợi ý phía client (xem [BarcodeType]).
 * @param payload phần dữ liệu sau khi bỏ tiền tố (nếu có), bằng [normalized] khi không có tiền tố.
 * @param matchedPrefix tiền tố đã khớp (ví dụ "SKU"), null nếu không có.
 */
data class ParsedBarcode(
    val raw: String,
    val normalized: String,
    val type: BarcodeType,
    val payload: String,
    val matchedPrefix: String? = null,
)
