package vn.delfi.xcloudwms.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    XcloudScaffold(
        title = "Đăng nhập",
        subtitle = "Đăng nhập để tải đơn vị, kho đang thao tác và quyền truy cập từ hệ thống.",
    ) {
        if (uiState.value.showConnectionSection) {
            SectionCard(title = "Kết nối hệ thống") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.value.connectionConfigured && !uiState.value.connectionLabel.isNullOrBlank()) {
                        InfoPill(text = "Đã cấu hình: ${uiState.value.connectionLabel}")
                    } else {
                        Text(
                            text = "Ứng dụng cần địa chỉ kết nối và khóa truy cập công khai của hệ thống trước khi đăng nhập.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = uiState.value.connectionUrl,
                        onValueChange = viewModel::updateConnectionUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Địa chỉ kết nối") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.colors(),
                    )

                    OutlinedTextField(
                        value = uiState.value.anonKey,
                        onValueChange = viewModel::updateAnonKey,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Khóa truy cập công khai") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        colors = TextFieldDefaults.colors(),
                    )

                    uiState.value.connectionErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    uiState.value.connectionSuccessMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = viewModel::testConnection,
                            enabled = !uiState.value.isConnectionBusy && !uiState.value.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (uiState.value.isTestingConnection) {
                                    "Đang kiểm tra..."
                                } else {
                                    "Kiểm tra kết nối"
                                },
                            )
                        }

                        Button(
                            onClick = viewModel::saveConnection,
                            enabled = !uiState.value.isConnectionBusy && !uiState.value.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (uiState.value.isSavingConnection) {
                                    "Đang lưu..."
                                } else {
                                    "Lưu cấu hình"
                                },
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = "Thông tin đăng nhập") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.value.connectionConfigured && !uiState.value.connectionLabel.isNullOrBlank()) {
                    InfoPill(text = "Máy chủ: ${uiState.value.connectionLabel}")
                }

                OutlinedTextField(
                    value = uiState.value.operatorCode,
                    onValueChange = viewModel::updateOperatorCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã đăng nhập hoặc email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    colors = TextFieldDefaults.colors(),
                )

                OutlinedTextField(
                    value = uiState.value.password,
                    onValueChange = viewModel::updatePassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    visualTransformation = if (uiState.value.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        TextButton(onClick = viewModel::togglePasswordVisibility) {
                            Text(if (uiState.value.isPasswordVisible) "Ẩn" else "Hiện")
                        }
                    },
                    colors = TextFieldDefaults.colors(),
                )

                if (!uiState.value.connectionConfigured) {
                    Text(
                        text = "Hãy lưu cấu hình kết nối trước khi đăng nhập.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.value.loginErrorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !uiState.value.isLoading && uiState.value.connectionConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = if (uiState.value.isLoading) {
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
