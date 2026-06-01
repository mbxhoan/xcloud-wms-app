package vn.delfi.xcloudwms.core.scanner

import java.text.Normalizer

/**
 * Phân tích mã quét thô thành [ParsedBarcode]. Chỉ là gợi ý phía client; backend lookup
 * vẫn là nguồn phân loại chính thức (xem [BarcodeType]).
 */
interface BarcodeParser {
    fun parse(raw: String, mode: ScannerMode): ParsedBarcode

    /** Chuẩn hoá mã (NFKC, gộp dấu gạch & khoảng trắng) — đồng bộ với PWA `normalizeLookupCode`. */
    fun normalize(raw: String): String
}

/**
 * Parser mặc định: chuẩn hoá rồi khớp tiền tố (doc 03 §8), nếu không có tiền tố thì suy ra theo
 * [ScannerMode] hiện tại, cuối cùng trả [BarcodeType.UNKNOWN].
 */
class DefaultBarcodeParser : BarcodeParser {

    override fun normalize(raw: String): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        return nfkc
            .replace(DASH_REGEX, "-")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    override fun parse(raw: String, mode: ScannerMode): ParsedBarcode {
        val normalized = normalize(raw)

        val prefixMatch = matchPrefix(normalized)
        if (prefixMatch != null) {
            return ParsedBarcode(
                raw = raw,
                normalized = normalized,
                type = prefixMatch.type,
                payload = prefixMatch.payload,
                matchedPrefix = prefixMatch.prefix,
            )
        }

        return ParsedBarcode(
            raw = raw,
            normalized = normalized,
            type = typeForMode(mode),
            payload = normalized,
            matchedPrefix = null,
        )
    }

    private fun matchPrefix(normalized: String): PrefixMatch? {
        val separatorIndex = normalized.indexOf(':')
        if (separatorIndex <= 0) {
            return null
        }
        val prefix = normalized.substring(0, separatorIndex).trim().uppercase()
        val type = PREFIX_TO_TYPE[prefix] ?: return null
        val payload = normalized.substring(separatorIndex + 1).trim()
        return PrefixMatch(prefix = prefix, type = type, payload = payload)
    }

    private fun typeForMode(mode: ScannerMode): BarcodeType = when (mode) {
        ScannerMode.LOCATION -> BarcodeType.LOCATION
        ScannerMode.PRODUCT -> BarcodeType.PRODUCT
        ScannerMode.LOT -> BarcodeType.LOT
        ScannerMode.SERIAL -> BarcodeType.SERIAL
        ScannerMode.GENERIC, ScannerMode.DOCUMENT -> BarcodeType.UNKNOWN
    }

    private data class PrefixMatch(
        val prefix: String,
        val type: BarcodeType,
        val payload: String,
    )

    private companion object {
        // Bao gồm các dấu gạch unicode hay gặp khi quét + dấu trừ toán học.
        val DASH_REGEX = Regex("[‐-―−]")
        val WHITESPACE_REGEX = Regex("\\s+")

        val PREFIX_TO_TYPE: Map<String, BarcodeType> = mapOf(
            "LOC" to BarcodeType.LOCATION,
            "LOCATION" to BarcodeType.LOCATION,
            "SKU" to BarcodeType.PRODUCT,
            "PRD" to BarcodeType.PRODUCT,
            "PRODUCT" to BarcodeType.PRODUCT,
            "LOT" to BarcodeType.LOT,
            "SN" to BarcodeType.SERIAL,
            "SERIAL" to BarcodeType.SERIAL,
            "GR" to BarcodeType.DOCUMENT_GR,
            "GI" to BarcodeType.DOCUMENT_GI,
        )
    }
}
