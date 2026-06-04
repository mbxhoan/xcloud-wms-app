package vn.delfi.xcloudwms.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.delfi.xcloudwms.core.ui.components.BrandLoadingOverlay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.delfi.xcloudwms.core.di.AppContainer
import vn.delfi.xcloudwms.domain.model.SessionStatus
import vn.delfi.xcloudwms.feature.deviceinfo.DeviceHardwareInfoScreen
import vn.delfi.xcloudwms.feature.deviceinfo.DeviceHardwareInfoViewModel
import vn.delfi.xcloudwms.feature.goodsissue.GoodsIssueListScreen
import vn.delfi.xcloudwms.feature.goodsissue.GoodsIssueListViewModel
import vn.delfi.xcloudwms.feature.goodsissue.GoodsIssuePickScreen
import vn.delfi.xcloudwms.feature.goodsissue.GoodsIssuePickViewModel
import vn.delfi.xcloudwms.feature.goodsreceipt.GoodsReceiptListScreen
import vn.delfi.xcloudwms.feature.goodsreceipt.GoodsReceiptListViewModel
import vn.delfi.xcloudwms.feature.goodsreceipt.GoodsReceiptReceiveScreen
import vn.delfi.xcloudwms.feature.goodsreceipt.GoodsReceiptReceiveViewModel
import vn.delfi.xcloudwms.feature.inventorycount.InventoryCountListScreen
import vn.delfi.xcloudwms.feature.inventorycount.InventoryCountListViewModel
import vn.delfi.xcloudwms.feature.inventorycount.InventoryCountScreen
import vn.delfi.xcloudwms.feature.inventorycount.InventoryCountViewModel
import vn.delfi.xcloudwms.feature.home.HomeScreen
import vn.delfi.xcloudwms.feature.home.HomeViewModel
import vn.delfi.xcloudwms.feature.login.LoginScreen
import vn.delfi.xcloudwms.feature.login.LoginViewModel
import vn.delfi.xcloudwms.feature.putaway.PutawayScreen
import vn.delfi.xcloudwms.feature.putaway.PutawayViewModel
import vn.delfi.xcloudwms.feature.scannertest.ScannerTestScreen
import vn.delfi.xcloudwms.feature.scannertest.ScannerTestViewModel
import vn.delfi.xcloudwms.feature.splash.SplashScreen
import vn.delfi.xcloudwms.feature.stocklookup.StockLookupScreen
import vn.delfi.xcloudwms.feature.stocklookup.StockLookupViewModel
import vn.delfi.xcloudwms.feature.warehouse.NoWarehouseScreen
import vn.delfi.xcloudwms.feature.warehouse.WarehouseSwitchScreen
import vn.delfi.xcloudwms.feature.warehouse.WarehouseSwitchViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val session by appContainer.sessionRepository.session.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Loading thương hiệu ngắn khi chuyển chức năng (đổi route).
    var showTransition by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            showTransition = true
            delay(420)
            showTransition = false
        }
    }

    LaunchedEffect(session.status, currentRoute) {
        val shouldNavigateTo = when (session.status) {
            SessionStatus.RESTORING -> {
                if (currentRoute != AppDestination.Splash.route) AppDestination.Splash.route else null
            }

            SessionStatus.UNAUTHENTICATED -> {
                if (currentRoute != AppDestination.Login.route) AppDestination.Login.route else null
            }

            SessionStatus.WAREHOUSE_SELECTION_REQUIRED -> {
                if (currentRoute != AppDestination.WarehouseSwitch.route) {
                    AppDestination.WarehouseSwitch.route
                } else {
                    null
                }
            }

            SessionStatus.NO_WAREHOUSE_ASSIGNED -> {
                if (currentRoute != AppDestination.NoWarehouse.route) AppDestination.NoWarehouse.route else null
            }

            SessionStatus.AUTHENTICATED -> {
                if (
                    currentRoute == null ||
                    currentRoute == AppDestination.Splash.route ||
                    currentRoute == AppDestination.Login.route ||
                    currentRoute == AppDestination.WarehouseSwitch.route ||
                    currentRoute == AppDestination.NoWarehouse.route
                ) {
                    AppDestination.Home.route
                } else {
                    null
                }
            }
        }

        if (shouldNavigateTo != null) {
            navController.navigate(shouldNavigateTo) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Splash.route,
    ) {
        composable(AppDestination.Splash.route) {
            LaunchedEffect(Unit) {
                appContainer.sessionRepository.restoreSession()
            }
            SplashScreen()
        }

        composable(AppDestination.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(
                    appConfig = appContainer.appConfig,
                    sessionRepository = appContainer.sessionRepository,
                    logger = appContainer.logger,
                ),
            )
            LoginScreen(viewModel = viewModel)
        }

        composable(AppDestination.WarehouseSwitch.route) {
            val viewModel: WarehouseSwitchViewModel = viewModel(
                factory = WarehouseSwitchViewModel.factory(
                    sessionRepository = appContainer.sessionRepository,
                ),
            )
            WarehouseSwitchScreen(
                viewModel = viewModel,
                onBack = if (session.currentWarehouse != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onLogout = {
                    coroutineScope.launch {
                        appContainer.sessionRepository.signOut()
                    }
                },
            )
        }

        composable(AppDestination.NoWarehouse.route) {
            NoWarehouseScreen(
                userLabel = session.displayName ?: session.email ?: "Người dùng",
                onLogout = {
                    coroutineScope.launch {
                        appContainer.sessionRepository.signOut()
                    }
                },
            )
        }

        composable(AppDestination.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    sessionRepository = appContainer.sessionRepository,
                ),
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenWarehouseSwitch = {
                    navController.navigate(AppDestination.WarehouseSwitch.route)
                },
                onOpenDeviceHardwareInfo = {
                    navController.navigate(AppDestination.DeviceHardwareInfo.route)
                },
                onOpenScannerTest = {
                    navController.navigate(AppDestination.ScannerTest.route)
                },
                onOpenStockLookup = {
                    navController.navigate(AppDestination.StockLookup.route)
                },
                onOpenPutaway = {
                    navController.navigate(AppDestination.Putaway.route)
                },
                onOpenGoodsIssue = {
                    navController.navigate(AppDestination.GoodsIssueList.route)
                },
                onOpenGoodsReceipt = {
                    navController.navigate(AppDestination.GoodsReceiptList.route)
                },
                onOpenInventoryCount = {
                    navController.navigate(AppDestination.InventoryCountList.route)
                },
            )
        }

        composable(AppDestination.DeviceHardwareInfo.route) {
            val viewModel: DeviceHardwareInfoViewModel = viewModel(
                factory = DeviceHardwareInfoViewModel.factory(
                    repository = appContainer.deviceHardwareRepository,
                    logger = appContainer.logger,
                ),
            )
            DeviceHardwareInfoScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.StockLookup.route) {
            val viewModel: StockLookupViewModel = viewModel(
                factory = StockLookupViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    stockLookupRepository = appContainer.stockLookupRepository,
                    sessionRepository = appContainer.sessionRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            StockLookupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.Putaway.route) {
            val viewModel: PutawayViewModel = viewModel(
                factory = PutawayViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    putawayRepository = appContainer.putawayRepository,
                    sessionRepository = appContainer.sessionRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    offlineCache = appContainer.putawayOfflineCache,
                    deviceId = appContainer.deviceId,
                    logger = appContainer.logger,
                ),
            )
            PutawayScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.GoodsIssueList.route) {
            val viewModel: GoodsIssueListViewModel = viewModel(
                factory = GoodsIssueListViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    goodsIssueRepository = appContainer.goodsIssueRepository,
                    sessionRepository = appContainer.sessionRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            GoodsIssueListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenHeader = { headerId ->
                    navController.navigate(AppDestination.goodsIssuePickRoute(headerId))
                },
            )
        }

        composable(
            route = AppDestination.GoodsIssuePick.route,
            arguments = listOf(navArgument("headerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val headerId = backStackEntry.arguments?.getString("headerId").orEmpty()
            val viewModel: GoodsIssuePickViewModel = viewModel(
                factory = GoodsIssuePickViewModel.factory(
                    headerId = headerId,
                    scannerManager = appContainer.scannerManager,
                    goodsIssueRepository = appContainer.goodsIssueRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            GoodsIssuePickScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.GoodsReceiptList.route) {
            val viewModel: GoodsReceiptListViewModel = viewModel(
                factory = GoodsReceiptListViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    goodsReceiptRepository = appContainer.goodsReceiptRepository,
                    sessionRepository = appContainer.sessionRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            GoodsReceiptListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenHeader = { headerId ->
                    navController.navigate(AppDestination.goodsReceiptReceiveRoute(headerId))
                },
            )
        }

        composable(
            route = AppDestination.GoodsReceiptReceive.route,
            arguments = listOf(navArgument("headerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val headerId = backStackEntry.arguments?.getString("headerId").orEmpty()
            val viewModel: GoodsReceiptReceiveViewModel = viewModel(
                factory = GoodsReceiptReceiveViewModel.factory(
                    headerId = headerId,
                    scannerManager = appContainer.scannerManager,
                    goodsReceiptRepository = appContainer.goodsReceiptRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            GoodsReceiptReceiveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.InventoryCountList.route) {
            val viewModel: InventoryCountListViewModel = viewModel(
                factory = InventoryCountListViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    inventoryCountRepository = appContainer.inventoryCountRepository,
                    sessionRepository = appContainer.sessionRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            InventoryCountListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenHeader = { headerId ->
                    navController.navigate(AppDestination.inventoryCountRoute(headerId))
                },
            )
        }

        composable(
            route = AppDestination.InventoryCount.route,
            arguments = listOf(navArgument("headerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val headerId = backStackEntry.arguments?.getString("headerId").orEmpty()
            val viewModel: InventoryCountViewModel = viewModel(
                factory = InventoryCountViewModel.factory(
                    headerId = headerId,
                    scannerManager = appContainer.scannerManager,
                    inventoryCountRepository = appContainer.inventoryCountRepository,
                    connectivityObserver = appContainer.connectivityObserver,
                    logger = appContainer.logger,
                ),
            )
            InventoryCountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.ScannerTest.route) {
            val viewModel: ScannerTestViewModel = viewModel(
                factory = ScannerTestViewModel.factory(
                    scannerManager = appContainer.scannerManager,
                    logger = appContainer.logger,
                ),
            )
            ScannerTestScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }

        AnimatedVisibility(
            visible = showTransition,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BrandLoadingOverlay()
        }
    }
}
