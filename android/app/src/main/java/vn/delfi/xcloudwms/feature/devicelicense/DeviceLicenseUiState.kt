package vn.delfi.xcloudwms.feature.devicelicense

import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

data class DeviceLicenseUiState(
    val isRefreshing: Boolean = false,
    val operatorName: String = "Người dùng",
    val tenantLabel: String = "Chưa xác định",
    val status: DeviceLicenseStatus = DeviceLicenseStatus.ACTIVE,
    val statusLabel: String = "Đang hoạt động",
    val message: String = "Chưa có dữ liệu trạng thái thiết bị.",
    val reasonCode: String = "UNKNOWN",
    val checkedAtLabel: String = "Chưa kiểm tra",
    val installId: String = "",
    val deviceName: String = "",
    val deviceTypeLabel: String = "",
    val brand: String = "",
    val model: String = "",
    val osLabel: String = "",
    val appVersion: String = "",
    val fingerprintPreview: String = "",
    val androidIdHashPreview: String = "",
    val vendorSerialHashPreview: String = "",
    val backendDeviceName: String = "",
    val backendDeviceCode: String = "",
    val canOperate: Boolean = true,
    val errorMessage: String? = null,
)
