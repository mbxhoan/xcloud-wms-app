package vn.delfi.xcloudwms.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultBarcodeParserTest {
    private val parser = DefaultBarcodeParser()

    @Test
    fun `product prefix classified`() {
        val result = parser.parse("SKU:SP001", ScannerMode.GENERIC)
        assertEquals(BarcodeType.PRODUCT, result.type)
        assertEquals("SP001", result.payload)
        assertEquals("SKU", result.matchedPrefix)
    }

    @Test
    fun `each documented prefix maps to expected type`() {
        assertEquals(BarcodeType.LOCATION, parser.parse("LOC:A1-01-02", ScannerMode.GENERIC).type)
        assertEquals(BarcodeType.LOT, parser.parse("LOT:LOT202605", ScannerMode.GENERIC).type)
        assertEquals(BarcodeType.SERIAL, parser.parse("SN:TEST0001", ScannerMode.GENERIC).type)
        assertEquals(BarcodeType.DOCUMENT_GR, parser.parse("GR:GR-2605-0001", ScannerMode.GENERIC).type)
        assertEquals(BarcodeType.DOCUMENT_GI, parser.parse("GI:GI-2605-0001", ScannerMode.GENERIC).type)
    }

    @Test
    fun `prefix match is case insensitive`() {
        assertEquals(BarcodeType.PRODUCT, parser.parse("sku:sp001", ScannerMode.GENERIC).type)
    }

    @Test
    fun `no prefix falls back to scanner mode hint`() {
        assertEquals(BarcodeType.SERIAL, parser.parse("ABC123", ScannerMode.SERIAL).type)
        assertEquals(BarcodeType.UNKNOWN, parser.parse("ABC123", ScannerMode.GENERIC).type)

        val result = parser.parse("ABC123", ScannerMode.SERIAL)
        assertNull(result.matchedPrefix)
        assertEquals("ABC123", result.payload)
    }

    @Test
    fun `unknown prefix is not treated as a prefix`() {
        val result = parser.parse("FOO:BAR", ScannerMode.LOCATION)
        assertEquals(BarcodeType.LOCATION, result.type)
        assertNull(result.matchedPrefix)
        assertEquals("FOO:BAR", result.payload)
    }

    @Test
    fun `normalize unifies dashes and collapses whitespace`() {
        // U+2014 em dash + leading/trailing/multiple spaces.
        assertEquals("SP-001 X", parser.normalize("  SP—001   X "))
    }
}
