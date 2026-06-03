package vn.delfi.xcloudwms.core.config

import vn.delfi.xcloudwms.BuildConfig

data class AppConfig(
    val buildEnvironment: String,
    val baseApiUrl: String,
    val defaultConnectionUrl: String,
    val defaultConnectionAnonKey: String,
    val appChannel: String,
    val enableCameraScanFallback: Boolean,
    val enableDeviceLicenseCheck: Boolean,
) {
    val normalizedBaseApiUrl: String
        get() = baseApiUrl.removeSuffix("/")

    val defaultConnectionConfig: ConnectionConfig?
        get() = ConnectionConfig.create(
            url = defaultConnectionUrl,
            anonKey = defaultConnectionAnonKey,
        )

    companion object {
        fun fromBuildConfig(): AppConfig {
            return AppConfig(
                buildEnvironment = BuildConfig.BUILD_ENV,
                baseApiUrl = BuildConfig.BASE_API_URL,
                defaultConnectionUrl = BuildConfig.DEFAULT_CONNECTION_URL,
                defaultConnectionAnonKey = BuildConfig.DEFAULT_CONNECTION_ANON_KEY,
                appChannel = BuildConfig.APP_CHANNEL,
                enableCameraScanFallback = BuildConfig.ENABLE_CAMERA_SCAN_FALLBACK,
                enableDeviceLicenseCheck = BuildConfig.ENABLE_DEVICE_LICENSE_CHECK,
            )
        }
    }
}
