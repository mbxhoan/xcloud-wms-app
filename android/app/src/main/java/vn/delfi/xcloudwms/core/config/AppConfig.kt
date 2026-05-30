package vn.delfi.xcloudwms.core.config

import vn.delfi.xcloudwms.BuildConfig

data class AppConfig(
    val buildEnvironment: String,
    val baseApiUrl: String,
    val appChannel: String,
    val enableCameraScanFallback: Boolean,
    val enableDeviceLicenseCheck: Boolean,
) {
    val normalizedBaseApiUrl: String
        get() = baseApiUrl.removeSuffix("/")

    companion object {
        fun fromBuildConfig(): AppConfig {
            return AppConfig(
                buildEnvironment = BuildConfig.BUILD_ENV,
                baseApiUrl = BuildConfig.BASE_API_URL,
                appChannel = BuildConfig.APP_CHANNEL,
                enableCameraScanFallback = BuildConfig.ENABLE_CAMERA_SCAN_FALLBACK,
                enableDeviceLicenseCheck = BuildConfig.ENABLE_DEVICE_LICENSE_CHECK,
            )
        }
    }
}
