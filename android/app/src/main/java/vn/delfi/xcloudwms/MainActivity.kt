package vn.delfi.xcloudwms

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        (application as XcloudWmsApplication).container
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XcloudWmsApp(appContainer = appContainer)
        }
    }

    /**
     * Chuyển mọi key event cho keyboard-wedge adapter trước. Adapter chỉ "nuốt" khi scanner đang
     * bật và nhận diện được chuỗi quét máy; còn lại trả về dispatch bình thường để bàn phím/ô nhập
     * hoạt động như thường.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (appContainer.scannerManager.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
