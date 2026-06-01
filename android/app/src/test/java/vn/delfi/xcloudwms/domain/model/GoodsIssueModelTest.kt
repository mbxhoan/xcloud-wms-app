package vn.delfi.xcloudwms.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsIssueModelTest {

    @Test
    fun `status parsing covers backend strings and aliases`() {
        assertEquals(GiStatus.CREATED, GiStatus.parse("CREATED"))
        assertEquals(GiStatus.PICKING, GiStatus.parse("PICKING"))
        assertEquals(GiStatus.PICKING, GiStatus.parse("in_progress"))
        assertEquals(GiStatus.PICKED, GiStatus.parse("PICKED"))
        assertEquals(GiStatus.COMPLETED, GiStatus.parse("completed"))
        assertEquals(GiStatus.CANCELLED, GiStatus.parse("CANCELED"))
        assertEquals(GiStatus.OTHER, GiStatus.parse("WHATEVER"))
    }

    @Test
    fun `status flags drive picking workflow`() {
        assertTrue(GiStatus.CREATED.needsStartPicking)
        assertTrue(GiStatus.CREATED.isScannable)
        assertTrue(GiStatus.PICKING.isScannable)
        assertTrue(GiStatus.PICKING.canSubmit)
        assertFalse(GiStatus.PICKED.canSubmit)
        assertTrue(GiStatus.PICKING.canComplete)
        assertTrue(GiStatus.PICKED.canComplete)
        assertFalse(GiStatus.PICKED.isScannable)
        assertTrue(GiStatus.COMPLETED.isTerminal)
        assertTrue(GiStatus.CANCELLED.isTerminal)
    }

    @Test
    fun `tracking type parsing handles synonyms`() {
        assertEquals(GiTrackingType.SERIAL, GiTrackingType.parse("serial"))
        assertEquals(GiTrackingType.SERIAL, GiTrackingType.parse("SERIALIZED"))
        assertEquals(GiTrackingType.LOT, GiTrackingType.parse("BATCH"))
        assertEquals(GiTrackingType.NONE, GiTrackingType.parse(null))
        assertEquals(GiTrackingType.NONE, GiTrackingType.parse("none"))
    }

    @Test
    fun `line completion and remaining are computed from planned vs picked`() {
        val line = GiLine(
            id = "1",
            productId = "10",
            productCode = "SP01",
            productName = "Sản phẩm 01",
            trackingType = GiTrackingType.NONE,
            plannedQty = 5.0,
            pickedQty = 2.0,
        )
        assertEquals(3.0, line.remainingQty, 1e-9)
        assertFalse(line.isComplete)
        assertTrue(line.copy(pickedQty = 5.0).isComplete)
        assertEquals("SP01 — Sản phẩm 01", line.productLabel)
    }

    @Test
    fun `header display code falls back to id`() {
        val header = GiHeader(
            id = "42",
            tenantId = "1",
            warehouseId = "3",
            code = null,
            status = GiStatus.PICKING,
            warehouseLabel = "Kho A",
            partnerLabel = null,
            referenceType = null,
            referenceNumber = null,
            note = null,
        )
        assertEquals("#42", header.displayCode)
        assertEquals("GI-001", header.copy(code = "GI-001").displayCode)
    }
}
