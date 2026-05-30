package vn.delfi.xcloudwms.feature.warehouse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun NoWarehouseScreen(
    userLabel: String,
    onLogout: () -> Unit,
) {
    XcloudScaffold(
        title = "Chưa có kho làm việc",
        subtitle = "Tài khoản đã đăng nhập nhưng chưa đủ điều kiện vào màn hình thao tác kho.",
    ) {
        SectionCard(title = "Thông báo") {
            Text(
                text = "Người dùng: $userLabel",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Bạn chưa được phân quyền kho nào.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text("Đăng xuất")
        }
    }
}
