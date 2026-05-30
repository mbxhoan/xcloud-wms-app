package vn.delfi.xcloudwms.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun SplashScreen() {
    XcloudScaffold(
        title = "Đang khởi tạo",
        subtitle = "Đang kiểm tra phiên đăng nhập, tenant và kho đang thao tác.",
    ) {
        SectionCard(title = "Vui lòng chờ") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ứng dụng đang đồng bộ thông tin truy cập với hệ thống.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
