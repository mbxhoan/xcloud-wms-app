package vn.delfi.xcloudwms.feature.devicelicense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

@Composable
fun DeviceLicenseScreen(
    viewModel: DeviceLicenseViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val accentColor = when (state.status) {
        DeviceLicenseStatus.ACTIVE -> Color(0xFF2E7D32)
        DeviceLicenseStatus.PENDING -> Color(0xFFB26A00)
        DeviceLicenseStatus.BLOCKED,
        DeviceLicenseStatus.REVOKED,
        DeviceLicenseStatus.EXPIRED,
        -> MaterialTheme.colorScheme.error
    }

    XcloudScaffold(
        title = "Trạng thái thiết bị",
        subtitle = "Thiết bị chỉ được thao tác khi hệ thống xác nhận quyền sử dụng còn hiệu lực.",
        onBack = onBack,
    ) {
        SectionCard(title = "Trạng thái cấp phép") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    ),
            ) {
                Text(
                    text = state.statusLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .background(
                            color = Color.Transparent,
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }

            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.checkedAtLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Mã phản hồi: ${state.reasonCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = "Thiết bị hiện tại") {
            DeviceFieldLine(label = "Mã cài đặt", value = state.installId)
            DeviceFieldLine(label = "Tên hiển thị", value = state.deviceName)
            DeviceFieldLine(label = "Loại thiết bị", value = state.deviceTypeLabel)
            DeviceFieldLine(label = "Hãng thiết bị", value = state.brand)
            DeviceFieldLine(label = "Dòng máy", value = state.model)
            DeviceFieldLine(label = "Hệ điều hành", value = state.osLabel)
            DeviceFieldLine(label = "Phiên bản ứng dụng", value = state.appVersion)
            DeviceFieldLine(label = "Dấu vân tay thiết bị", value = state.fingerprintPreview)
            if (state.androidIdHashPreview.isNotBlank()) {
                DeviceFieldLine(label = "Android ID (băm)", value = state.androidIdHashPreview)
            }
            if (state.vendorSerialHashPreview.isNotBlank()) {
                DeviceFieldLine(label = "Serial nhà sản xuất (băm)", value = state.vendorSerialHashPreview)
            }
            if (state.backendDeviceName.isNotBlank()) {
                DeviceFieldLine(label = "Tên thiết bị trên hệ thống", value = state.backendDeviceName)
            }
            if (state.backendDeviceCode.isNotBlank()) {
                DeviceFieldLine(label = "Mã thiết bị trên hệ thống", value = state.backendDeviceCode)
            }
        }

        SectionCard(title = "Ngữ cảnh đăng nhập") {
            DeviceFieldLine(label = "Nhân viên", value = state.operatorName)
            DeviceFieldLine(label = "Đơn vị", value = state.tenantLabel)
            Text(
                text = "Đăng xuất chỉ xóa phiên cục bộ. Đăng ký thiết bị vẫn được giữ trên hệ thống.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = viewModel::refreshStatus,
                enabled = !state.isRefreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (state.isRefreshing) "Đang kiểm tra..." else "Kiểm tra lại")
            }

            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Đăng xuất")
            }
        }
    }
}

@Composable
private fun DeviceFieldLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "Không có" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
