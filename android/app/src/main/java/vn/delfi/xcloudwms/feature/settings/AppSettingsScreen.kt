package vn.delfi.xcloudwms.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    onOpenDeviceLicense: () -> Unit,
    onOpenDeviceHardwareInfo: () -> Unit,
    onOpenScannerTest: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    XcloudScaffold(
        title = "Cài đặt",
        subtitle = "Gom cấu hình quét, trạng thái thiết bị và thông tin máy ra khỏi trang chủ để thao tác gọn hơn.",
        onBack = onBack,
    ) {
        SectionCard(title = "Cài đặt quét") {
            SettingSwitchRow(
                title = "Ẩn bàn phím ảo",
                subtitle = "Phù hợp PDA có cò quét hoặc bàn phím cứng. Tắt nếu cần nhập tay bằng bàn phím mềm.",
                checked = state.blockSoftKeyboard,
                onCheckedChange = viewModel::setBlockSoftKeyboard,
            )
            SettingSwitchRow(
                title = "Tự động Enter / Tab",
                subtitle = "Bật để quét xong là nhận ngay. Tắt để chỉ đưa mã vào ô quét và chờ bấm nút “Nhận theo mã quét”.",
                checked = state.autoSubmitScanInput,
                onCheckedChange = viewModel::setAutoSubmitScanInput,
            )
            SettingsActionRow(
                icon = Icons.Filled.QrCodeScanner,
                title = "Kiểm tra máy quét",
                subtitle = "Mở màn quét thử, kiểm tra wedge/broadcast và cấu hình phản hồi beep/rung.",
                onClick = onOpenScannerTest,
            )
        }

        SectionCard(title = "Thiết bị đang dùng") {
            SettingsActionRow(
                icon = Icons.Filled.Settings,
                title = "Trạng thái cấp phép",
                subtitle = "Tình trạng hiện tại: ${state.deviceStatusLabel}. Kiểm tra lại sau khi quản trị viên đổi quyền hoặc khi đổi máy.",
                onClick = onOpenDeviceLicense,
            )
            SettingsActionRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "Thông tin phần cứng",
                subtitle = "Xem model máy, Android, IP, MAC, Bluetooth, IMEI, serial và dữ liệu thiết bị.",
                onClick = onOpenDeviceHardwareInfo,
            )
        }

        SectionCard(title = "Ngữ cảnh hiện tại") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(text = state.buildEnvironment)
                Text(
                    text = "Nhân viên: ${state.operatorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Đơn vị: ${state.tenantLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Kho đang làm việc: ${state.warehouseLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Kết nối: ${state.connectionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.roleLabels.isNotEmpty()) {
                    Text(
                        text = "Vai trò: ${state.roleLabels.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = viewModel::logout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("  Đăng xuất")
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
