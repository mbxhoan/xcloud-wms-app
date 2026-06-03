package vn.delfi.xcloudwms.feature.goodsreceipt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.domain.model.GrHeader
import vn.delfi.xcloudwms.domain.model.GrStatus

@Composable
fun GoodsReceiptListScreen(
    viewModel: GoodsReceiptListViewModel,
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
        title = "Nhận hàng",
        subtitle = "Chọn hoặc quét mã phiếu nhập trong kho để bắt đầu nhận hàng.",
        onBack = onBack,
    ) {
        if (state.isOffline) {
            GrOfflineBanner()
        }

        SectionCard(title = "Tìm phiếu nhập") {
            InfoPill(text = "Kho: ${state.warehouseLabel}")
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Quét hoặc nhập mã phiếu") },
            )
            OutlinedButton(
                onClick = viewModel::refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
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
                    Text("Đang tải phiếu nhập…")
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
                        "Chưa có phiếu nhập nào đang chờ nhận trong kho này."
                    } else {
                        "Không tìm thấy phiếu khớp “${state.query}”."
                    },
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.visibleHeaders.forEach { header ->
                    GoodsReceiptHeaderCard(header = header, onClick = { onOpenHeader(header.id) })
                }
            }
        }
    }
}

@Composable
private fun GoodsReceiptHeaderCard(header: GrHeader, onClick: () -> Unit) {
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
            GrStatusBadge(header.status)
        }
        header.warehouseLabel?.let { GrLabeledLine("Kho", it) }
        header.partnerLabel?.let { GrLabeledLine("Đối tác", it) }
        header.referenceNumber?.let { GrLabeledLine("Tham chiếu", it) }
        header.note?.let { GrLabeledLine("Ghi chú", it) }
    }
}

@Composable
internal fun GrStatusBadge(status: GrStatus) {
    val container = when (status) {
        GrStatus.CREATED -> MaterialTheme.colorScheme.primary
        GrStatus.RECEIVING -> MaterialTheme.colorScheme.tertiary
        GrStatus.RECEIVED -> MaterialTheme.colorScheme.secondary
        GrStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
        GrStatus.CANCELLED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when (status) {
        GrStatus.COMPLETED, GrStatus.OTHER, GrStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
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
internal fun GrLabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun GrOfflineBanner() {
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
