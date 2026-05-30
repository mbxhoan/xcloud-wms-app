package vn.delfi.xcloudwms

import android.os.Bundle
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
}
