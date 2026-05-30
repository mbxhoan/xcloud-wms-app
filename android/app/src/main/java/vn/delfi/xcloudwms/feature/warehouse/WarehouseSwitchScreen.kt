package vn.delfi.xcloudwms.feature.warehouse

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun WarehouseSwitchScreen(
    viewModel: WarehouseSwitchViewModel,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    XcloudScaffold(
        title = "Chọn kho",
        subtitle = "Chọn kho đang thao tác cho phiên hiện tại.",
        onBack = onBack,
    ) {
        SectionCard(title = "Kho được phân quyền") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.value.warehouses.forEach { warehouse ->
                    val isSelected = uiState.value.currentWarehouseId == warehouse.id
                    Button(
                        onClick = { viewModel.selectWarehouse(warehouse.id) },
                        enabled = uiState.value.isLoadingWarehouseId == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = if (uiState.value.isLoadingWarehouseId == warehouse.id) {
                                "Đang chọn ${warehouse.label}..."
                            } else if (isSelected) {
                                "${warehouse.label} • Kho hiện tại"
                            } else {
                                warehouse.label
                            },
                        )
                    }
                }

                if (uiState.value.warehouses.isEmpty()) {
                    Text(
                        text = "Chưa có kho nào khả dụng để lựa chọn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.value.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text("Đăng xuất")
        }
    }
}
