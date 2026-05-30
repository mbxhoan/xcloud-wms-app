package vn.delfi.xcloudwms.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        subtitle = "Ứng dụng đang ở giai đoạn nền tảng. Luồng đăng nhập hiện mới là màn chờ.",
    ) {
        SectionCard(title = "Phiên làm việc mẫu") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoPill(text = "Chưa gọi API thật")

                Text(
                    text = uiState.value.helperMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = uiState.value.operatorCode,
                    onValueChange = viewModel::updateOperatorCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã đăng nhập") },
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
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    colors = TextFieldDefaults.colors(),
                )

                uiState.value.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !uiState.value.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = if (uiState.value.isLoading) {
                            "Đang xử lý..."
                        } else {
                            "Đăng nhập"
                        },
                    )
                }
            }
        }
    }
}
