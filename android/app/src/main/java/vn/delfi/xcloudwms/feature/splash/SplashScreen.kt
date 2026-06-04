package vn.delfi.xcloudwms.feature.splash

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import vn.delfi.xcloudwms.core.ui.components.BrandLoading

@Composable
fun SplashScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BrandLoading(message = "Đang kiểm tra phiên đăng nhập, tenant và kho đang thao tác…")
    }
}
