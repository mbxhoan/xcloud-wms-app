package vn.delfi.xcloudwms

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.di.AppContainer
import vn.delfi.xcloudwms.core.navigation.AppNavHost
import vn.delfi.xcloudwms.core.ui.theme.XcloudWmsTheme

@Composable
fun XcloudWmsApp(appContainer: AppContainer) {
    val blockSoftKeyboard by appContainer.appPreferences.blockSoftKeyboard
        .collectAsStateWithLifecycle()
    val session by appContainer.sessionRepository.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Chặn/cho phép bàn phím ảo của thiết bị ở cấp window. FLAG_ALT_FOCUSABLE_IM giữ window vẫn nhận
    // phím cứng (PDA/bàn phím vật lý, keyboard-wedge) nhưng không bật bàn phím ảo khi focus ô nhập.
    // Chỉ chặn khi đã đăng nhập để tránh khoá thao tác nhập tài khoản ở màn đăng nhập trên thiết bị
    // không có bàn phím cứng.
    val shouldBlock = blockSoftKeyboard && session.isAuthenticated
    LaunchedEffect(shouldBlock) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        if (shouldBlock) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    XcloudWmsTheme {
        AppNavHost(appContainer = appContainer)
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
