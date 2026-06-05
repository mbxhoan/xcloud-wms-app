package vn.delfi.xcloudwms.feature.inventorycount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.PdaScanField
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.domain.model.IcHeader
import vn.delfi.xcloudwms.domain.model.IcStatus

@Composable
fun InventoryCountListScreen(
    viewModel: InventoryCountListViewModel,
    onBack: () -> Unit,
    onOpenHeader: (String) -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    DisposableEffect(Unit) {
        viewModel.onScreenEntered()
        onDispose { viewModel.onScreenLeft() }
    }

    LaunchedEffect(state.pendingOpenHeaderId) {
        val id = state.pendingOpenHeaderId
        if (id != null) {
            viewModel.consumeOpenEvent()
            onOpenHeader(id)
        }
    }

    XcloudScaffold(
        title = "Kiểm kê",
        subtitle = "Chọn hoặc quét mã phiếu kiểm kê trong kho để bắt đầu đếm.",
        onBack = onBack,
    ) {
        if (state.isOffline) {
            IcOfflineBanner()
        }

        SectionCard(title = "Tìm phiếu kiểm kê") {
            InfoPill(text = "Kho: ${state.warehouseLabel}")
            PdaScanField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = "Quét hoặc nhập mã phiếu",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = viewModel::submitManualSearch,
            )
            OutlinedButton(
                onClick = viewModel::refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Làm mới danh sách")
            }
        }

        when {
            state.isLoading -> SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Đang tải phiếu kiểm kê…")
                }
            }

            state.errorMessage != null -> SectionCard(title = "Không tải được") {
                Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("Thử lại")
                }
            }

            state.isEmpty -> SectionCard {
                Text(
                    text = if (state.query.isBlank()) {
                        "Chưa có phiếu kiểm kê nào đang mở trong kho này."
                    } else {
                        "Không tìm thấy phiếu khớp “${state.query}”."
                    },
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.visibleHeaders.forEach { header ->
                    InventoryCountHeaderCard(header = header, onClick = { onOpenHeader(header.id) })
                }
            }
        }
    }
}

@Composable
private fun InventoryCountHeaderCard(header: IcHeader, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header.displayCode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IcStatusBadge(header.status)
        }
        header.warehouseLabel?.let { IcLabeledLine("Kho", it) }
        if (header.isBlind) IcLabeledLine("Chế độ", "Đếm mù")
        header.note?.let { IcLabeledLine("Ghi chú", it) }
    }
}

@Composable
internal fun IcStatusBadge(status: IcStatus) {
    val container = when (status) {
        IcStatus.CREATED -> MaterialTheme.colorScheme.primary
        IcStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        IcStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
        IcStatus.CANCELLED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when (status) {
        IcStatus.COMPLETED, IcStatus.OTHER, IcStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun IcLabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun IcOfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = "Mất kết nối mạng. Dữ liệu có thể chưa cập nhật.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
