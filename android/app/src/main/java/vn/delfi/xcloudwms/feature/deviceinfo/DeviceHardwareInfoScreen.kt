package vn.delfi.xcloudwms.feature.deviceinfo

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun DeviceHardwareInfoScreen(
    viewModel: DeviceHardwareInfoViewModel,
    onBack: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refresh()
    }

    XcloudScaffold(
        title = "Thông tin phần cứng",
        subtitle = "Hiển thị tối đa thông tin mà app Android có thể đọc được từ thiết bị hiện tại.",
        onBack = onBack,
    ) {
        SectionCard(title = "Trạng thái") {
            Text(
                text = state.capturedAtLabel.ifBlank { "Đang chuẩn bị dữ liệu..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.missingPermissionLabels.isNotEmpty()) {
                Text(
                    text = "Một số trường đang bị giới hạn vì thiếu quyền: ${state.missingPermissionLabels.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(deviceHardwarePermissions())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("Cấp quyền đọc thiết bị nâng cao")
                }
            } else {
                Text(
                    text = "App đã có đủ quyền bổ sung để thử đọc thông tin điện thoại và Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = viewModel::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text("Làm mới dữ liệu")
                }
            }

            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (state.isLoading) {
            SectionCard(title = "Đang tải") {
                Text(
                    text = "Ứng dụng đang đọc thông tin phần cứng và trạng thái thiết bị...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.sections.forEach { section ->
            SectionCard(title = section.title) {
                section.fields.forEach { field ->
                    DeviceFieldLine(
                        label = field.label,
                        value = field.value,
                    )
                }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun deviceHardwarePermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()
}
