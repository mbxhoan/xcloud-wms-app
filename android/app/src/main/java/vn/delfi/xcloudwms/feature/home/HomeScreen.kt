package vn.delfi.xcloudwms.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenWarehouseSwitch: () -> Unit,
    onOpenScannerTest: () -> Unit,
    onOpenStockLookup: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    XcloudScaffold(
        title = "Trang chủ",
        subtitle = "Danh mục thao tác được lọc theo quyền hiện tại và kho đang làm việc.",
    ) {
        SectionCard(title = "Ngữ cảnh hiện tại") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(text = uiState.value.buildEnvironment)
                InfoPill(text = "Kho: ${uiState.value.warehouseLabel}")

                Text(
                    text = "Nhân viên: ${uiState.value.operatorName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Đơn vị: ${uiState.value.tenantLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Kết nối: ${uiState.value.connectionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Danh mục theo quyền") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.value.moduleShortcuts.forEach { shortcut ->
                    val cardModifier = when (shortcut.actionKey) {
                        HomeViewModel.ACTION_STOCK_LOOKUP -> Modifier.clickable { onOpenStockLookup() }
                        else -> Modifier
                    }
                    SectionCard(modifier = cardModifier) {
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.value.canSwitchWarehouse) {
                OutlinedButton(
                    onClick = onOpenWarehouseSwitch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("Đổi kho làm việc")
                }
            }

            Button(
                onClick = onOpenScannerTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Kiểm tra máy quét")
            }

            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Đăng xuất")
            }
        }
    }
}
