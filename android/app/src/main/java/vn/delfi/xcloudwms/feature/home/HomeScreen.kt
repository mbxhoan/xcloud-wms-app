package vn.delfi.xcloudwms.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenScannerTest: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    XcloudScaffold(
        title = "Trang chủ",
        subtitle = "Khung giao diện tối ưu cho PDA quét mã. Chưa có module nghiệp vụ thật ở bước này.",
    ) {
        SectionCard(title = "Ngữ cảnh hiện tại") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(text = uiState.value.buildEnvironment)
                    InfoPill(text = "Kho: ${uiState.value.warehouseLabel}")
                }

                Text(
                    text = "Nhân viên: ${uiState.value.operatorName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "API: ${uiState.value.baseApiUrl}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = uiState.value.networkSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Module dự kiến") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.value.moduleShortcuts.forEach { shortcut ->
                    SectionCard {
                        Text(
                            text = shortcut.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = shortcut.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onOpenScannerTest,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                Text("Kiểm tra máy quét")
            }

            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .width(132.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text("Đăng xuất")
            }
        }
    }
}
