package vn.delfi.xcloudwms.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vn.delfi.xcloudwms.core.di.AppContainer
import vn.delfi.xcloudwms.feature.home.HomeScreen
import vn.delfi.xcloudwms.feature.home.HomeViewModel
import vn.delfi.xcloudwms.feature.login.LoginScreen
import vn.delfi.xcloudwms.feature.login.LoginViewModel
import vn.delfi.xcloudwms.feature.scannertest.ScannerTestScreen
import vn.delfi.xcloudwms.feature.scannertest.ScannerTestViewModel

@Composable
fun AppNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val startDestination = if (appContainer.sessionRepository.session.value.isAuthenticated) {
        AppDestination.Home.route
    } else {
        AppDestination.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(AppDestination.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(
                    sessionRepository = appContainer.sessionRepository,
                    logger = appContainer.logger,
                ),
            )
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.value.isAuthenticated) {
                if (uiState.value.isAuthenticated) {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo(AppDestination.Login.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            LoginScreen(viewModel = viewModel)
        }

        composable(AppDestination.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    appConfig = appContainer.appConfig,
                    sessionRepository = appContainer.sessionRepository,
                    networkClient = appContainer.networkClient,
                ),
            )
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.value.isAuthenticated) {
                if (!uiState.value.isAuthenticated) {
                    navController.navigate(AppDestination.Login.route) {
                        popUpTo(AppDestination.Home.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onOpenScannerTest = {
                    navController.navigate(AppDestination.ScannerTest.route)
                },
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
}
