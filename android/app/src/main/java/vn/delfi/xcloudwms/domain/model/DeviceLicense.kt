package vn.delfi.xcloudwms.domain.model

enum class DeviceLicenseStatus {
    ACTIVE,
    PENDING,
    BLOCKED,
    REVOKED,
    EXPIRED,
}

data class DeviceLicenseState(
    val status: DeviceLicenseStatus,
    val reasonCode: String,
    val message: String,
    val checkedAtEpochMillis: Long,
    val backendDeviceName: String? = null,
    val backendDeviceCode: String? = null,
) {
    val canOperate: Boolean
        get() = status == DeviceLicenseStatus.ACTIVE
}
