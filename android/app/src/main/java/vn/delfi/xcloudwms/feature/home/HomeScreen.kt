package vn.delfi.xcloudwms.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
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
    onOpenDeviceHardwareInfo: () -> Unit,
    onOpenScannerTest: () -> Unit,
    onOpenStockLookup: () -> Unit,
    onOpenPutaway: () -> Unit,
    onOpenGoodsIssue: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    XcloudScaffold(
        title = "Trang chủ",
        subtitle = "Danh mục thao tác được lọc theo quyền hiện tại và kho đang làm việc.",
    ) {
        SectionCard(title = "Tác vụ nhanh") {
            Button(
                onClick = onOpenScannerTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text("Quét thử mã bằng PDA")
            }

            Text(
                text = "Mở màn quét thử để bấm cò quét bên hông PDA và xem mã nhận được ngay.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Thiết bị đang dùng") {
            Text(
                text = "Mở màn thông tin phần cứng để xem dòng máy, phiên bản Android, IP, MAC, Bluetooth, IMEI, serial và các dữ liệu thiết bị mà app đọc được.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenDeviceHardwareInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Thông tin phần cứng")
            }
        }

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
                        HomeViewModel.ACTION_PUTAWAY -> Modifier.clickable { onOpenPutaway() }
                        HomeViewModel.ACTION_GOODS_ISSUE -> Modifier.clickable { onOpenGoodsIssue() }
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

                        shortcut.actionLabel?.let { actionLabel ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } ?: Text(
                            text = "Tính năng này chưa mở trực tiếp trong app Android ở build hiện tại.",
                            style = MaterialTheme.typography.bodySmall,
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
