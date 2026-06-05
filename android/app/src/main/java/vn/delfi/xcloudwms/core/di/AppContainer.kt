package vn.delfi.xcloudwms.core.di

import android.app.Application
import vn.delfi.xcloudwms.BuildConfig
import vn.delfi.xcloudwms.core.config.AppConfig
import vn.delfi.xcloudwms.core.logging.LogLevel
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.network.ConnectivityObserver
import vn.delfi.xcloudwms.core.network.DefaultNetworkClient
import vn.delfi.xcloudwms.core.network.NetworkClient
import vn.delfi.xcloudwms.core.scanner.DefaultBarcodeParser
import vn.delfi.xcloudwms.core.scanner.DefaultFeedbackManager
import vn.delfi.xcloudwms.core.scanner.DefaultScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.adapter.BroadcastScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.CameraScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.KeyboardWedgeScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.ManualScannerAdapter
import vn.delfi.xcloudwms.core.security.SecureSessionStorage
import vn.delfi.xcloudwms.core.storage.AppPreferences
import vn.delfi.xcloudwms.core.storage.OfflineStore
import vn.delfi.xcloudwms.data.auth.AuthRepository
import vn.delfi.xcloudwms.data.auth.SupabaseAuthRepository
import vn.delfi.xcloudwms.data.device.DefaultDeviceHardwareRepository
import vn.delfi.xcloudwms.data.device.DefaultDeviceLicenseRepository
import vn.delfi.xcloudwms.data.device.DeviceHardwareRepository
import vn.delfi.xcloudwms.data.device.DeviceLicenseRepository
import vn.delfi.xcloudwms.data.session.DefaultSessionRepository
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.data.gi.DefaultGoodsIssueRepository
import vn.delfi.xcloudwms.data.gi.GoodsIssueRepository
import vn.delfi.xcloudwms.data.gr.DefaultGoodsReceiptRepository
import vn.delfi.xcloudwms.data.gr.GoodsReceiptRepository
import vn.delfi.xcloudwms.data.ic.DefaultInventoryCountRepository
import vn.delfi.xcloudwms.data.ic.InventoryCountRepository
import vn.delfi.xcloudwms.data.putaway.DefaultPutawayRepository
import vn.delfi.xcloudwms.data.putaway.PaOfflineCache
import vn.delfi.xcloudwms.data.putaway.PutawayRepository
import vn.delfi.xcloudwms.data.stock.DefaultStockLookupRepository
import vn.delfi.xcloudwms.data.stock.LookupHistoryStore
import vn.delfi.xcloudwms.data.stock.StockLookupRepository

interface AppContainer {
    val appConfig: AppConfig
    val logger: SafeLogger
    val networkClient: NetworkClient
    val scannerManager: ScannerManager
    val sessionRepository: SessionRepository
    val stockLookupRepository: StockLookupRepository
    val lookupHistoryStore: LookupHistoryStore
    val putawayRepository: PutawayRepository
    val goodsIssueRepository: GoodsIssueRepository
    val goodsReceiptRepository: GoodsReceiptRepository
    val inventoryCountRepository: InventoryCountRepository
    val deviceHardwareRepository: DeviceHardwareRepository
    val deviceLicenseRepository: DeviceLicenseRepository
    val connectivityObserver: ConnectivityObserver
    val putawayOfflineCache: PaOfflineCache
    val appPreferences: AppPreferences
    val deviceId: String
}

class DefaultAppContainer(
    application: Application,
) : AppContainer {
    override val appConfig: AppConfig = AppConfig.fromBuildConfig()

    override val logger: SafeLogger = SafeLogger(
        isDebug = BuildConfig.DEBUG,
        minimumLevel = if (appConfig.buildEnvironment == "prod") LogLevel.INFO else LogLevel.DEBUG,
    )

    override val networkClient: NetworkClient = DefaultNetworkClient(
        appConfig = appConfig,
        logger = logger,
    )

    override val appPreferences: AppPreferences = AppPreferences(
        context = application,
        defaultConnectionConfig = appConfig.defaultConnectionConfig,
    )
    private val secureSessionStorage = SecureSessionStorage(application)
    private val offlineStore = OfflineStore(application)

    override val putawayOfflineCache: PaOfflineCache = PaOfflineCache(
        store = offlineStore,
        logger = logger,
    )

    override val deviceId: String = offlineStore.deviceInstallId()

    override val deviceLicenseRepository: DeviceLicenseRepository = DefaultDeviceLicenseRepository(
        context = application,
        appConfig = appConfig,
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        offlineStore = offlineStore,
        logger = logger,
    )

    private val authRepository: AuthRepository = SupabaseAuthRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        logger = logger,
    )

    override val scannerManager: ScannerManager = run {
        val broadcastAdapter = BroadcastScannerAdapter(
            context = application,
            logger = logger,
            configProvider = { appPreferences.currentBroadcastScannerConfig() },
        )
        DefaultScannerManager(
            manualAdapter = ManualScannerAdapter(),
            keyboardWedgeAdapter = KeyboardWedgeScannerAdapter(),
            broadcastAdapter = broadcastAdapter,
            cameraAdapter = CameraScannerAdapter(
                enabled = appConfig.enableCameraScanFallback,
                logger = logger,
            ),
            parser = DefaultBarcodeParser(),
            feedback = DefaultFeedbackManager(application = application, logger = logger),
            logger = logger,
            initialBroadcastConfig = appPreferences.currentBroadcastScannerConfig(),
            onBroadcastConfigChanged = { appPreferences.saveBroadcastScannerConfig(it) },
        )
    }

    override val sessionRepository: SessionRepository = DefaultSessionRepository(
        appConfig = appConfig,
        authRepository = authRepository,
        deviceLicenseRepository = deviceLicenseRepository,
        logger = logger,
    )

    override val stockLookupRepository: StockLookupRepository = DefaultStockLookupRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        logger = logger,
    )

    override val lookupHistoryStore: LookupHistoryStore = LookupHistoryStore(offlineStore)

    override val putawayRepository: PutawayRepository = DefaultPutawayRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        offlineCache = putawayOfflineCache,
        logger = logger,
    )

    override val goodsIssueRepository: GoodsIssueRepository = DefaultGoodsIssueRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        logger = logger,
    )

    override val goodsReceiptRepository: GoodsReceiptRepository = DefaultGoodsReceiptRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        logger = logger,
    )

    override val inventoryCountRepository: InventoryCountRepository = DefaultInventoryCountRepository(
        networkClient = networkClient,
        appPreferences = appPreferences,
        secureSessionStorage = secureSessionStorage,
        logger = logger,
    )

    override val deviceHardwareRepository: DeviceHardwareRepository = DefaultDeviceHardwareRepository(
        context = application,
    )

    override val connectivityObserver: ConnectivityObserver = ConnectivityObserver(application)
}
