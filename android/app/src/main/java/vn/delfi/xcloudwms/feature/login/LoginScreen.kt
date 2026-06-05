package vn.delfi.xcloudwms.feature.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.config.looksLikeConnectionConfigQr
import vn.delfi.xcloudwms.core.scanner.ScanEvent
import vn.delfi.xcloudwms.core.scanner.ScannerManager
import vn.delfi.xcloudwms.core.scanner.ScannerMode
import vn.delfi.xcloudwms.core.ui.components.ClearableOutlinedTextField
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.PdaScanField
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    scannerManager: ScannerManager,
    cameraScanEnabled: Boolean,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value
    var showManualConnectionForm by rememberSaveable { mutableStateOf(false) }

    if (state.showConnectionSection) {
        DisposableEffect(scannerManager) {
            scannerManager.setMode(ScannerMode.GENERIC)
            scannerManager.start()
            onDispose { scannerManager.stop() }
        }

        LaunchedEffect(scannerManager) {
            scannerManager.scanEvents.collect { event ->
                if (event is ScanEvent.Success && looksLikeConnectionConfigQr(event.raw)) {
                    viewModel.applyConnectionQr(
                        raw = event.raw,
                        sourceLabel = event.source.label.lowercase(),
                    )
                }
            }
        }
    }

    XcloudScaffold(
        title = "Đăng nhập",
        subtitle = "Đăng nhập để tải đơn vị, kho đang thao tác và quyền truy cập từ hệ thống.",
    ) {
        if (state.showConnectionSection) {
            SectionCard(title = "Kết nối hệ thống") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.connectionConfigured && !state.connectionLabel.isNullOrBlank()) {
                        InfoPill(text = "Đã cấu hình: ${state.connectionLabel}")
                    } else {
                        Text(
                            text = "Quét mã QR cài đặt do admin cung cấp, hoặc nhập thủ công thông tin kết nối để bắt đầu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ConnectionQrSetupCard(
                        state = state,
                        cameraScanEnabled = cameraScanEnabled,
                        onQrInputChange = viewModel::updateConnectionQrInput,
                        onApplyQr = { viewModel.applyConnectionQr(state.connectionQrInput) },
                        onCameraDetected = { raw -> viewModel.applyConnectionQr(raw, sourceLabel = "camera") },
                        onCameraError = viewModel::showConnectionError,
                    )

                    OutlinedButton(
                        onClick = { showManualConnectionForm = !showManualConnectionForm },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showManualConnectionForm) {
                                "Ẩn nhập thủ công"
                            } else {
                                "Nhập thủ công"
                            },
                        )
                    }

                    AnimatedVisibility(visible = showManualConnectionForm) {
                        ManualConnectionForm(
                            state = state,
                            onConnectionUrlChange = viewModel::updateConnectionUrl,
                            onAnonKeyChange = viewModel::updateAnonKey,
                            onTestConnection = viewModel::testConnection,
                            onSaveConnection = viewModel::saveConnection,
                        )
                    }

                    state.connectionErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    state.connectionSuccessMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        SectionCard(title = "Thông tin đăng nhập") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.connectionConfigured && !state.connectionLabel.isNullOrBlank()) {
                    InfoPill(text = "Máy chủ: ${state.connectionLabel}")
                }

                ClearableOutlinedTextField(
                    value = state.operatorCode,
                    onValueChange = viewModel::updateOperatorCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã đăng nhập hoặc email") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    colors = TextFieldDefaults.colors(),
                )

                ClearableOutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::updatePassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    visualTransformation = if (state.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingContent = {
                        TextButton(onClick = viewModel::togglePasswordVisibility) {
                            Text(if (state.isPasswordVisible) "Ẩn" else "Hiện")
                        }
                    },
                    colors = TextFieldDefaults.colors(),
                )

                if (!state.connectionConfigured) {
                    Text(
                        text = "Hãy lưu cấu hình kết nối trước khi đăng nhập.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.loginErrorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isLoading && state.connectionConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = if (state.isLoading) {
                            "Đang đăng nhập..."
                        } else {
                            "Đăng nhập"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionQrSetupCard(
    state: LoginUiState,
    cameraScanEnabled: Boolean,
    onQrInputChange: (String) -> Unit,
    onApplyQr: () -> Unit,
    onCameraDetected: (String) -> Unit,
    onCameraError: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quét mã QR cài đặt",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Dùng cò scan 2D của PDA hoặc camera để tự động điền và lưu cấu hình kết nối.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PdaScanField(
                value = state.connectionQrInput,
                onValueChange = onQrInputChange,
                label = "Mã QR cài đặt",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isConnectionBusy,
                keepFocused = false,
                onSubmit = onApplyQr,
                supportingText = "Có thể dán mã XCWMS1 vào đây nếu không dùng camera.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onApplyQr,
                    enabled = !state.isConnectionBusy && state.connectionQrInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (state.isApplyingConnectionQr) {
                            "Đang áp dụng..."
                        } else {
                            "Áp dụng mã QR"
                        },
                    )
                }

                if (cameraScanEnabled) {
                    ConnectionQrCameraScanButton(
                        onDetected = onCameraDetected,
                        onError = onCameraError,
                        enabled = !state.isConnectionBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualConnectionForm(
    state: LoginUiState,
    onConnectionUrlChange: (String) -> Unit,
    onAnonKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSaveConnection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClearableOutlinedTextField(
            value = state.connectionUrl,
            onValueChange = onConnectionUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Địa chỉ kết nối") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            colors = TextFieldDefaults.colors(),
        )

        ClearableOutlinedTextField(
            value = state.anonKey,
            onValueChange = onAnonKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Khóa truy cập công khai") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            colors = TextFieldDefaults.colors(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !state.isConnectionBusy && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.isTestingConnection) {
                        "Đang kiểm tra..."
                    } else {
                        "Kiểm tra kết nối"
                    },
                )
            }

            Button(
                onClick = onSaveConnection,
                enabled = !state.isConnectionBusy && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.isSavingConnection) {
                        "Đang lưu..."
                    } else {
                        "Lưu cấu hình"
                    },
                )
            }
        }
    }
}
