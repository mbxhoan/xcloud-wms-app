package vn.delfi.xcloudwms

import android.app.Application
import vn.delfi.xcloudwms.core.di.AppContainer
import vn.delfi.xcloudwms.core.di.DefaultAppContainer

class XcloudWmsApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(application = this)
    }
}
