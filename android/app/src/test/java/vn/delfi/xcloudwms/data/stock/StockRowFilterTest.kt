package vn.delfi.xcloudwms.data.stock

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.delfi.xcloudwms.domain.model.StockRow

class StockRowFilterTest {

    private fun row(warehouseId: String?): StockRow = StockRow(
        warehouseId = warehouseId,
        warehouseCode = null,
        warehouseName = null,
        locationCode = null,
        locationName = null,
        trackingValue = null,
        quantityOnHand = 0.0,
        quantityReserved = 0.0,
        quantityLocked = 0.0,
        quantityAvailable = 0.0,
        inboundDate = null,
        manufactureDate = null,
        expiryDate = null,
    )

    private val rows = listOf(row("1"), row("2"), row("1"), row(null))

    @Test
    fun `filters to current warehouse when not showing all`() {
        val result = StockRowFilter.forWarehouse(rows, currentWarehouseId = "1", showAll = false)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "1"), result.map { it.warehouseId })
    }

    @Test
    fun `show all returns every row`() {
        val result = StockRowFilter.forWarehouse(rows, currentWarehouseId = "1", showAll = true)
        assertEquals(rows.size, result.size)
    }

    @Test
    fun `null or blank current warehouse returns every row`() {
        assertEquals(rows.size, StockRowFilter.forWarehouse(rows, currentWarehouseId = null, showAll = false).size)
        assertEquals(rows.size, StockRowFilter.forWarehouse(rows, currentWarehouseId = "  ", showAll = false).size)
    }

    @Test
    fun `current warehouse id is compared trimmed`() {
        val result = StockRowFilter.forWarehouse(rows, currentWarehouseId = " 2 ", showAll = false)
        assertEquals(1, result.size)
        assertEquals("2", result.single().warehouseId)
    }
}
