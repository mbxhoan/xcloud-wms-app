package vn.delfi.xcloudwms.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.ClearableOutlinedTextField
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
    val connState = viewModel.connConfigState.collectAsStateWithLifecycle().value

    XcloudScaffold(
        title = "Cài đặt",
        subtitle = "Gom cấu hình quét, trạng thái thiết bị và thông tin máy ra khỏi trang chủ để thao tác gọn hơn.",
        onBack = onBack,
    ) {
        SectionCard(title = "Kết nối & đồng bộ") {
            SettingSwitchRow(
                title = "Chế độ Offline",
                subtitle = "Lưu mã quét cục bộ khi mất/yếu mạng. Đồng bộ thủ công khi có kết nối trở lại.",
                checked = state.manualOffline,
                onCheckedChange = viewModel::setManualOffline,
            )
            SettingSwitchRow(
                title = "Tự động đồng bộ",
                subtitle = "Khi bật, mỗi lần quét sẽ ghi nhận lên server ngay. Khi tắt, dữ liệu chỉ lưu local và phải bấm \"Đồng bộ\" để gửi lên.",
                checked = state.autoSync,
                onCheckedChange = viewModel::setAutoSync,
            )
            SettingsActionRow(
                icon = Icons.Filled.Dns,
                title = "Cấu hình kết nối",
                subtitle = "Hiện tại: ${state.connectionLabel}. Đổi địa chỉ/khóa Supabase và kiểm tra kết nối. Lưu cấu hình mới sẽ đăng xuất.",
                onClick = viewModel::openConnConfig,
            )
        }

        SectionCard(title = "Cài đặt quét") {
            SettingSwitchRow(
                title = "Ẩn bàn phím ảo",
                subtitle = "Phù hợp PDA có cò quét hoặc bàn phím cứng. Tắt nếu cần nhập tay bằng bàn phím mềm.",
                checked = state.blockSoftKeyboard,
                onCheckedChange = viewModel::setBlockSoftKeyboard,
            )
            ScannerSubmitModeRow(
                selectedMode = state.scannerSubmitMode,
                onSelectMode = viewModel::setScannerSubmitMode,
            )
            SettingSwitchRow(
                title = "Cho phép nhập tay ở ô quét",
                subtitle = "Bật cho emulator/dev hoặc khi cần gõ thử bằng bàn phím mềm. PDA production nên tắt để ô quét chỉ nhận scan target.",
                checked = state.allowManualInputFallback,
                onCheckedChange = viewModel::setAllowManualInputFallback,
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

    if (connState.open) {
        ConnectionConfigDialog(
            state = connState,
            onDismiss = viewModel::closeConnConfig,
            onPasswordChange = viewModel::updateConnPassword,
            onPasswordSubmit = viewModel::submitConnPassword,
            onUrlChange = viewModel::updateConnUrl,
            onKeyChange = viewModel::updateConnKey,
            onToggleKeyVisible = viewModel::toggleConnKeyVisible,
            onTest = viewModel::testConnConfig,
            onSave = viewModel::saveConnConfig,
        )
    }
}

@Composable
private fun ConnectionConfigDialog(
    state: ConnConfigDialogState,
    onDismiss: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordSubmit: () -> Unit,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onToggleKeyVisible: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "Cấu hình kết nối",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (state.phase == ConnConfigPhase.PASSWORD) {
                    ConnPasswordPhase(
                        state = state,
                        onPasswordChange = onPasswordChange,
                        onSubmit = onPasswordSubmit,
                    )
                } else {
                    ConnConfigPhaseContent(
                        state = state,
                        onUrlChange = onUrlChange,
                        onKeyChange = onKeyChange,
                        onToggleKeyVisible = onToggleKeyVisible,
                        onTest = onTest,
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnPasswordPhase(
    state: ConnConfigDialogState,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Nhập mật khẩu xác thực để mở cấu hình kết nối. Chỉ dành cho quản trị/IT.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClearableOutlinedTextField(
            value = state.passwordInput,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Mật khẩu xác thực") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (state.passwordError != null) {
                { Text(state.passwordError, color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
        )
        Button(
            onClick = onSubmit,
            enabled = state.passwordInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
        ) {
            Text("Xác nhận")
        }
    }
}

@Composable
private fun ConnConfigPhaseContent(
    state: ConnConfigDialogState,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onToggleKeyVisible: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val busy = state.testState == ConnTestState.TESTING || state.saving
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnNoticeBox(
            icon = Icons.Filled.ErrorOutline,
            text = "Sau khi lưu cấu hình mới, bạn sẽ bị đăng xuất tự động để áp dụng thay đổi.",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        ClearableOutlinedTextField(
            value = state.urlInput,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            label = { Text("Địa chỉ kết nối (URL)") },
            placeholder = { Text("https://xxxxxxxx.supabase.co") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        ClearableOutlinedTextField(
            value = state.keyInput,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            label = { Text("Khóa truy cập công khai (Key)") },
            placeholder = { Text("sb_publishable_… hoặc eyJhbGci…") },
            visualTransformation = if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingContent = {
                IconButton(onClick = onToggleKeyVisible) {
                    Icon(
                        imageVector = if (state.showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (state.showKey) "Ẩn khóa" else "Hiện khóa",
                    )
                }
            },
        )

        OutlinedButton(
            onClick = onTest,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
        ) {
            if (state.testState == ConnTestState.TESTING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Đang kiểm tra…")
            } else {
                Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Kiểm tra kết nối")
            }
        }

        if (state.testState == ConnTestState.OK) {
            ConnNoticeBox(
                icon = Icons.Filled.CheckCircle,
                text = "Kết nối thành công. Bạn có thể lưu cấu hình.",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (state.testState == ConnTestState.ERROR && state.testError != null) {
            ConnNoticeBox(
                icon = Icons.Filled.ErrorOutline,
                text = state.testError,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        Button(
            onClick = onSave,
            enabled = state.testState == ConnTestState.OK && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text("Lưu & Đăng xuất")
        }
    }
}

@Composable
private fun ConnNoticeBox(
    icon: ImageVector,
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = container, shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
        )
    }
}

@Composable
private fun ScannerSubmitModeRow(
    selectedMode: ScannerSubmitMode,
    onSelectMode: (ScannerSubmitMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Sau khi nhận đủ mã quét",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "ENTER: xử lý ngay. TAB: chuyển sang step/field quét kế tiếp nếu màn có. NONE: chỉ điền mã và chờ người dùng bấm nút.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubmitModeChip(
                label = "ENTER",
                selected = selectedMode == ScannerSubmitMode.ENTER,
                modifier = Modifier.weight(1f),
            ) { onSelectMode(ScannerSubmitMode.ENTER) }
            SubmitModeChip(
                label = "TAB",
                selected = selectedMode == ScannerSubmitMode.TAB,
                modifier = Modifier.weight(1f),
            ) { onSelectMode(ScannerSubmitMode.TAB) }
            SubmitModeChip(
                label = "NONE",
                selected = selectedMode == ScannerSubmitMode.NONE,
                modifier = Modifier.weight(1f),
            ) { onSelectMode(ScannerSubmitMode.NONE) }
        }
    }
}

@Composable
private fun SubmitModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
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
