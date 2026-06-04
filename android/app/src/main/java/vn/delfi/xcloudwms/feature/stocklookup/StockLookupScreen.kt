package vn.delfi.xcloudwms.feature.stocklookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.core.ui.components.alwaysFocusedScanInput
import vn.delfi.xcloudwms.domain.model.StockMatch
import vn.delfi.xcloudwms.domain.model.StockRow
import vn.delfi.xcloudwms.domain.model.StockSummary

@Composable
fun StockLookupScreen(
    viewModel: StockLookupViewModel,
    onBack: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value

    // Bật scanner khi vào màn, tắt khi rời màn → màn khác không xử lý quét sai route.
    DisposableEffect(Unit) {
        viewModel.startScanner()
        onDispose { viewModel.stopScanner() }
    }

    XcloudScaffold(
        title = "Tra cứu tồn kho",
        subtitle = "Quét hoặc nhập mã để xem tồn kho (chỉ đọc).",
        onBack = onBack,
    ) {
        if (state.isOffline) {
            OfflineBanner()
        }

        SectionCard(title = "Quét hoặc nhập mã") {
            InfoPill(text = "Kho: ${state.currentWarehouseLabel}")
            OutlinedTextField(
                value = state.manualCode,
                onValueChange = viewModel::updateManualCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .alwaysFocusedScanInput(),
                singleLine = true,
                label = { Text("Mã hàng / lô / serial / vị trí") },
            )
            Button(
                onClick = viewModel::submitManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Tra cứu")
            }
        }

        when {
            state.isLoading -> SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Đang tra cứu…")
                }
            }

            state.errorMessage != null -> SectionCard(title = "Không tra cứu được") {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.errorRetryable) {
                    OutlinedButton(
                        onClick = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text("Thử lại")
                    }
                }
            }

            state.isEmptyResult -> SectionCard {
                Text("Không tìm thấy tồn cho mã “${state.query}”.")
            }

            state.hasResult -> {
                state.match?.let { MatchCard(it) }
                state.summary?.let { SummaryCard(it) }
                RowsCard(
                    state = state,
                    onToggleShowAll = viewModel::toggleShowAllWarehouses,
                )
            }

            else -> SectionCard {
                Text("Chưa có kết quả. Quét hoặc nhập mã để bắt đầu.")
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = "Mất kết nối mạng. Kết quả có thể chưa cập nhật.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MatchCard(match: StockMatch) {
    SectionCard(title = "Thông tin mã") {
        Text(
            text = match.productLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        InfoPill(text = kindLabel(match.kind))
        match.productCode?.let { LabeledLine("Mã hàng", it) }
        match.lotNumber?.let { LabeledLine("Lô", it) }
        match.serialNumber?.let { LabeledLine("Serial", it) }
        match.lpnCode?.let { LabeledLine("Đơn vị chứa", it) }
        (match.uomName ?: match.uomCode)?.let { LabeledLine("ĐVT", it) }
        match.status?.let { LabeledLine("Trạng thái", it) }
    }
}

@Composable
private fun SummaryCard(summary: StockSummary) {
    SectionCard(title = "Tổng tồn (toàn bộ kho được phân quyền)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(text = "Tồn thực: ${formatQty(summary.totalOnHand)}")
            InfoPill(text = "Khả dụng: ${formatQty(summary.totalAvailable)}")
        }
        LabeledLine("Giữ chỗ", formatQty(summary.totalReserved))
        LabeledLine("Đã khóa", formatQty(summary.totalLocked))
    }
}

@Composable
private fun RowsCard(
    state: StockLookupUiState,
    onToggleShowAll: (Boolean) -> Unit,
) {
    SectionCard(title = "Tồn theo vị trí") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Xem tất cả kho được phân quyền",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.showAllWarehouses,
                onCheckedChange = onToggleShowAll,
            )
        }

        if (!state.showAllWarehouses) {
            Text(
                text = "Đang xem kho: ${state.currentWarehouseLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.hiddenWarehouseRowCount > 0) {
                Text(
                    text = "(Còn ${state.hiddenWarehouseRowCount} dòng ở kho khác)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.rows.isEmpty()) {
            Text("Không có tồn ở kho hiện tại cho mã này.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.rows.forEach { row -> StockRowCard(row) }
            }
        }
    }
}

@Composable
private fun StockRowCard(row: StockRow) {
    SectionCard {
        Text(
            text = "${row.warehouseLabel} • ${row.locationLabel}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        row.trackingValue?.takeIf { it.isNotBlank() && it != "—" }?.let {
            LabeledLine("Lô/Serial", it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(text = "Tồn: ${formatQty(row.quantityOnHand)}")
            InfoPill(text = "Khả dụng: ${formatQty(row.quantityAvailable)}")
        }
        LabeledLine(
            "Giữ chỗ / Đã khóa",
            "${formatQty(row.quantityReserved)} / ${formatQty(row.quantityLocked)}",
        )
        row.inboundDate?.let { LabeledLine("Ngày nhập", it) }
        row.expiryDate?.let { LabeledLine("Hạn sử dụng", it) }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun kindLabel(kind: String): String = when (kind.uppercase()) {
    "PRODUCT" -> "Sản phẩm"
    "LOT" -> "Lô"
    "SERIAL" -> "Số seri"
    "LPN" -> "Đơn vị chứa"
    else -> kind
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
}
