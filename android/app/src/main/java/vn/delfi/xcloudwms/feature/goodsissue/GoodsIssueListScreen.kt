package vn.delfi.xcloudwms.feature.goodsissue

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
import vn.delfi.xcloudwms.domain.model.GiHeader
import vn.delfi.xcloudwms.domain.model.GiStatus

@Composable
fun GoodsIssueListScreen(
    viewModel: GoodsIssueListViewModel,
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
        title = "Xuất hàng",
        subtitle = "Chọn hoặc quét mã phiếu xuất được phân công để bắt đầu lấy hàng.",
        onBack = onBack,
    ) {
        if (state.isOffline) {
            OfflineBanner()
        }

        SectionCard(title = "Tìm phiếu xuất") {
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
                    Text("Đang tải phiếu xuất…")
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
                        "Chưa có phiếu xuất nào đang chờ pick trong kho này."
                    } else {
                        "Không tìm thấy phiếu khớp “${state.query}”."
                    },
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.visibleHeaders.forEach { header ->
                    GoodsIssueHeaderCard(header = header, onClick = { onOpenHeader(header.id) })
                }
            }
        }
    }
}

@Composable
private fun GoodsIssueHeaderCard(header: GiHeader, onClick: () -> Unit) {
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
            GiStatusBadge(header.status)
        }
        header.warehouseLabel?.let { LabeledLine("Kho", it) }
        header.partnerLabel?.let { LabeledLine("Đối tác", it) }
        header.referenceNumber?.let { LabeledLine("Tham chiếu", it) }
        header.note?.let { LabeledLine("Ghi chú", it) }
    }
}

@Composable
internal fun GiStatusBadge(status: GiStatus) {
    val container = when (status) {
        GiStatus.CREATED -> MaterialTheme.colorScheme.primary
        GiStatus.PICKING -> MaterialTheme.colorScheme.tertiary
        GiStatus.PICKED -> MaterialTheme.colorScheme.secondary
        GiStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
        GiStatus.CANCELLED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when (status) {
        GiStatus.COMPLETED, GiStatus.OTHER, GiStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
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
internal fun LabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun OfflineBanner() {
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
