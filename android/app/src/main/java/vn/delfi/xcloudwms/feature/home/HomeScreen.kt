package vn.delfi.xcloudwms.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenWarehouseSwitch: () -> Unit,
    onOpenDeviceLicense: () -> Unit,
    onOpenDeviceHardwareInfo: () -> Unit,
    onOpenScannerTest: () -> Unit,
    onOpenStockLookup: () -> Unit,
    onOpenPutaway: () -> Unit,
    onOpenGoodsIssue: () -> Unit,
    onOpenGoodsReceipt: () -> Unit,
    onOpenInventoryCount: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    val onModuleClick: (String?) -> Unit = { actionKey ->
        when (actionKey) {
            HomeViewModel.ACTION_STOCK_LOOKUP -> onOpenStockLookup()
            HomeViewModel.ACTION_PUTAWAY -> onOpenPutaway()
            HomeViewModel.ACTION_GOODS_ISSUE -> onOpenGoodsIssue()
            HomeViewModel.ACTION_GOODS_RECEIPT -> onOpenGoodsReceipt()
            HomeViewModel.ACTION_INVENTORY_COUNT -> onOpenInventoryCount()
            else -> Unit
        }
    }

    XcloudScaffold(title = "Trang chủ") {
        GreetingCard(operatorName = state.operatorName, tenantLabel = state.tenantLabel)

        InfoPill(text = "Kho: ${state.warehouseLabel}")

        ModuleGridSection(shortcuts = state.moduleShortcuts, onModuleClick = onModuleClick)

        SectionCard(title = "Tác vụ nhanh") {
            QuickAccessRow(
                icon = Icons.Filled.QrCodeScanner,
                title = "Quét thử mã bằng PDA",
                subtitle = "Bấm cò quét bên hông PDA và xem mã nhận được ngay.",
                onClick = onOpenScannerTest,
            )
        }

        SectionCard(title = "Thiết bị đang dùng") {
            QuickAccessRow(
                icon = Icons.Filled.QrCodeScanner,
                title = "Trạng thái cấp phép",
                subtitle = "Tình trạng hiện tại: ${state.deviceStatusLabel}. Kiểm tra lại sau khi quản trị viên đổi quyền hoặc khi đổi thiết bị.",
                onClick = onOpenDeviceLicense,
            )
            QuickAccessRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "Thông tin phần cứng",
                subtitle = "Xem dòng máy, Android, IP, MAC, Bluetooth, IMEI, serial và dữ liệu thiết bị.",
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
                    text = "Kết nối: ${state.connectionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.canSwitchWarehouse) {
                OutlinedButton(
                    onClick = onOpenWarehouseSwitch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.Warehouse, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  Đổi kho làm việc")
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
}

@Composable
private fun GreetingCard(operatorName: String, tenantLabel: String) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6F9C7F), Color(0xFFA9C6A8)),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Xin chào!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = operatorName.ifBlank { "Chưa đăng nhập" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = "Chúc bạn một ngày làm việc hiệu quả",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Text(
                    text = "Đơn vị: $tenantLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun ModuleGridSection(
    shortcuts: List<ModuleShortcut>,
    onModuleClick: (String?) -> Unit,
) {
    if (shortcuts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Danh mục thao tác",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        shortcuts.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { shortcut ->
                    ModuleTile(
                        shortcut = shortcut,
                        onClick = { onModuleClick(shortcut.actionKey) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModuleTile(
    shortcut: ModuleShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = shortcut.actionKey != null
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                        },
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = moduleIcon(shortcut),
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!enabled) {
                Text(
                    text = "Sắp có",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAccessRow(
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
    }
}

private fun moduleIcon(shortcut: ModuleShortcut): ImageVector {
    return when (shortcut.actionKey) {
        HomeViewModel.ACTION_GOODS_RECEIPT -> Icons.Filled.MoveToInbox
        HomeViewModel.ACTION_GOODS_ISSUE -> Icons.Filled.Outbox
        HomeViewModel.ACTION_PUTAWAY -> Icons.Filled.SwapHoriz
        HomeViewModel.ACTION_STOCK_LOOKUP -> Icons.AutoMirrored.Filled.ManageSearch
        HomeViewModel.ACTION_INVENTORY_COUNT -> Icons.AutoMirrored.Filled.FactCheck
        else -> when {
            shortcut.title.contains("Kiểm kê", ignoreCase = true) -> Icons.AutoMirrored.Filled.FactCheck
            shortcut.title.contains("Đơn vị chứa", ignoreCase = true) -> Icons.Filled.Inventory2
            shortcut.title.contains("Nhận", ignoreCase = true) -> Icons.Filled.MoveToInbox
            shortcut.title.contains("Xuất", ignoreCase = true) -> Icons.Filled.Outbox
            shortcut.title.contains("Sắp xếp", ignoreCase = true) -> Icons.Filled.SwapHoriz
            shortcut.title.contains("Tra cứu", ignoreCase = true) -> Icons.AutoMirrored.Filled.ManageSearch
            else -> Icons.Filled.Apps
        }
    }
}
