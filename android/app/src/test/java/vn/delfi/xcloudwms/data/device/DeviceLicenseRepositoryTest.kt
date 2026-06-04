package vn.delfi.xcloudwms.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

class DeviceLicenseRepositoryTest {

    @Test
    fun `maps pending approval to pending status with required copy`() {
        val state = mapDeviceLicensePayloadToState(
            payload = DeviceLicenseRpcPayload(
                allowed = false,
                reason = "PENDING_APPROVAL",
                message = null,
                deviceName = null,
                deviceCode = null,
            ),
            checkedAtEpochMillis = 123L,
        )

        assertEquals(DeviceLicenseStatus.PENDING, state.status)
        assertEquals("Thiết bị đang chờ quản trị viên duyệt.", state.message)
        assertTrue(!state.canOperate)
    }

    @Test
    fun `maps suspended device to blocked state`() {
        val state = mapDeviceLicensePayloadToState(
            payload = DeviceLicenseRpcPayload(
                allowed = false,
                reason = "DEVICE_SUSPENDED",
                message = null,
                deviceName = null,
                deviceCode = null,
            ),
            checkedAtEpochMillis = 123L,
        )

        assertEquals(DeviceLicenseStatus.BLOCKED, state.status)
        assertEquals("Thiết bị này không được phép sử dụng app scanner.", state.message)
    }

    @Test
    fun `maps scanner quota exceeded to required copy`() {
        val state = mapDeviceLicensePayloadToState(
            payload = DeviceLicenseRpcPayload(
                allowed = false,
                reason = "SCANNER_LIMIT_EXCEEDED",
                message = null,
                deviceName = null,
                deviceCode = null,
            ),
            checkedAtEpochMillis = 123L,
        )

        assertEquals(DeviceLicenseStatus.BLOCKED, state.status)
        assertEquals("Số lượng thiết bị scanner đã vượt giới hạn gói.", state.message)
    }

    @Test
    fun `maps expired reason to expired status`() {
        val state = mapDeviceLicensePayloadToState(
            payload = DeviceLicenseRpcPayload(
                allowed = false,
                reason = "SUBSCRIPTION_EXPIRED",
                message = null,
                deviceName = null,
                deviceCode = null,
            ),
            checkedAtEpochMillis = 123L,
        )

        assertEquals(DeviceLicenseStatus.EXPIRED, state.status)
        assertEquals("Thiết bị này đã hết hạn sử dụng trên hệ thống.", state.message)
    }

    @Test
    fun `keeps active state when backend disables device enforcement`() {
        val state = mapDeviceLicensePayloadToState(
            payload = DeviceLicenseRpcPayload(
                allowed = true,
                reason = "DISABLED",
                message = null,
                deviceName = "Zebra TC26",
                deviceCode = "DEV-001",
            ),
            checkedAtEpochMillis = 123L,
        )

        assertEquals(DeviceLicenseStatus.ACTIVE, state.status)
        assertEquals("Đơn vị hiện chưa bật kiểm soát thiết bị scanner.", state.message)
        assertEquals("Zebra TC26", state.backendDeviceName)
        assertEquals("DEV-001", state.backendDeviceCode)
    }
}
