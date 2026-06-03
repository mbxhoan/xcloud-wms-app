package vn.delfi.xcloudwms.feature.deviceinfo

import vn.delfi.xcloudwms.data.device.DeviceHardwareSection

data class DeviceHardwareInfoUiState(
    val isLoading: Boolean = true,
    val capturedAtLabel: String = "",
    val missingPermissionLabels: List<String> = emptyList(),
    val sections: List<DeviceHardwareSection> = emptyList(),
    val errorMessage: String? = null,
)
