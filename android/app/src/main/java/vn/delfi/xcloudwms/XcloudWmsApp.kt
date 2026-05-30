package vn.delfi.xcloudwms

import androidx.compose.runtime.Composable
import vn.delfi.xcloudwms.core.di.AppContainer
import vn.delfi.xcloudwms.core.navigation.AppNavHost
import vn.delfi.xcloudwms.core.ui.theme.XcloudWmsTheme

@Composable
fun XcloudWmsApp(appContainer: AppContainer) {
    XcloudWmsTheme {
        AppNavHost(appContainer = appContainer)
    }
}
