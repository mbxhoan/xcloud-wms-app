package vn.delfi.xcloudwms.core.di

import android.app.Application
import vn.delfi.xcloudwms.BuildConfig
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.logging.LogLevel
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.network.NetworkClient
import vn.delfi.xcloudwms.core.network.PlaceholderNetworkClient
import vn.delfi.xcloudwms.core.scanner.ManualScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.data.session.InMemorySessionRepository
import vn.delfi.xcloudwms.data.session.SessionRepository

interface AppContainer {
    val appConfig: AppConfig
    val logger: SafeLogger
    val networkClient: NetworkClient
    val scannerManager: ScannerManager
    val sessionRepository: SessionRepository
}

class DefaultAppContainer(
    application: Application,
) : AppContainer {
    override val appConfig: AppConfig = AppConfig.fromBuildConfig()

    override val logger: SafeLogger = SafeLogger(
        isDebug = BuildConfig.DEBUG,
        minimumLevel = if (appConfig.buildEnvironment == "prod") LogLevel.INFO else LogLevel.DEBUG,
    )

    override val networkClient: NetworkClient = PlaceholderNetworkClient(
        config = appConfig,
        logger = logger,
    )

    override val scannerManager: ScannerManager = ManualScannerManager(
        logger = logger,
        application = application,
    )

    override val sessionRepository: SessionRepository = InMemorySessionRepository(
        appConfig = appConfig,
        logger = logger,
    )
}
