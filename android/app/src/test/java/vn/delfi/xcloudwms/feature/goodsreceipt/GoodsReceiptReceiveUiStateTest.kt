package vn.delfi.xcloudwms.feature.goodsreceipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.delfi.xcloudwms.domain.model.GrHeader
import vn.delfi.xcloudwms.domain.model.GrLine
import vn.delfi.xcloudwms.domain.model.GrStatus
import vn.delfi.xcloudwms.domain.model.GrTrackingType

class GoodsReceiptReceiveUiStateTest {

    @Test
    fun `scan action asks for location before submit`() {
        val state = state(
            line = line(trackingType = GrTrackingType.SERIAL),
            scannedCode = "SN-260604-000001",
        )

        assertTrue(state.requiresLocationSelection)
        assertFalse(state.canSubmitScannedCode)
        assertEquals("Chọn vị trí nhập trước", state.scanButtonLabel)
    }

    @Test
    fun `scan action enables after location selected`() {
        val state = state(
            line = line(trackingType = GrTrackingType.SERIAL),
            scannedCode = "SN-260604-000001",
            selectedLocationId = "11",
        )

        assertFalse(state.requiresLocationSelection)
        assertTrue(state.canSubmitScannedCode)
        assertEquals("Nhận theo mã quét", state.scanButtonLabel)
    }

    @Test
    fun `receive quantity button for none tracking still requires location`() {
        val blocked = state(line = line(trackingType = GrTrackingType.NONE))
        val allowed = state(
            line = line(trackingType = GrTrackingType.NONE),
            selectedLocationId = "11",
        )

        assertFalse(blocked.canReceiveNoneQuantity)
        assertTrue(allowed.canReceiveNoneQuantity)
    }

    private fun state(
        line: GrLine,
        scannedCode: String = "",
        selectedLocationId: String? = null,
    ) = GoodsReceiptReceiveUiState(
        header = header(),
        lines = listOf(line),
        activeLineId = line.id,
        scannedCode = scannedCode,
        selectedLocationId = selectedLocationId,
    )

    private fun header(status: GrStatus = GrStatus.RECEIVING) = GrHeader(
        id = "1",
        tenantId = "1",
        warehouseId = "1",
        code = "GR-TEST",
        status = status,
        warehouseLabel = "Kho test",
        partnerLabel = "NCC test",
        referenceType = null,
        referenceNumber = null,
        note = null,
    )

    private fun line(trackingType: GrTrackingType) = GrLine(
        id = "10",
        productId = "20",
        productCode = "BIXOLON-SLP-TX400",
        productName = "Bixolon SLP-TX400",
        trackingType = trackingType,
        expectedQty = 3.0,
        receivedQty = 0.0,
    )
}
