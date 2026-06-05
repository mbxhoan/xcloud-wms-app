package vn.delfi.xcloudwms.feature.stocklookup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.PdaScanField
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.domain.model.LookupHistoryItem
import vn.delfi.xcloudwms.domain.model.StockActiveLpn
import vn.delfi.xcloudwms.domain.model.StockEvent
import vn.delfi.xcloudwms.domain.model.StockLookupResult
import vn.delfi.xcloudwms.domain.model.StockLpnContent
import vn.delfi.xcloudwms.domain.model.StockMatch
import vn.delfi.xcloudwms.domain.model.StockRow

// Bảng màu theo scanner PWA (Tailwind) để giữ đúng tông tra cứu trên native.
private object LookupPalette {
    val sky50 = Color(0xFFF0F9FF)
    val sky100 = Color(0xFFE0F2FE)
    val sky400 = Color(0xFF38BDF8)
    val sky700 = Color(0xFF0369A1)
    val sky800 = Color(0xFF075985)
    val emerald50 = Color(0xFFECFDF5)
    val emerald100 = Color(0xFFD1FAE5)
    val emerald400 = Color(0xFF34D399)
    val emerald700 = Color(0xFF047857)
    val emerald800 = Color(0xFF065F46)
    val amber50 = Color(0xFFFFFBEB)
    val amber100 = Color(0xFFFEF3C7)
    val amber400 = Color(0xFFFBBF24)
    val amber700 = Color(0xFFB45309)
    val amber800 = Color(0xFF92400E)
    val amber900 = Color(0xFF78350F)
    val zinc50 = Color(0xFFFAFAFA)
    val zinc100 = Color(0xFFF4F4F5)
    val zinc200 = Color(0xFFE4E4E7)
    val zinc400 = Color(0xFFA1A1AA)
    val zinc500 = Color(0xFF71717A)
    val zinc600 = Color(0xFF52525B)
    val zinc700 = Color(0xFF3F3F46)
    val zinc800 = Color(0xFF27272A)
    val zinc900 = Color(0xFF18181B)
    val white = Color(0xFFFFFFFF)
    val red50 = Color(0xFFFEF2F2)
    val red200 = Color(0xFFFECACA)
    val red600 = Color(0xFFDC2626)
    val red800 = Color(0xFF991B1B)
    val primary = Color(0xFF2563EB)
}

@Composable
fun StockLookupScreen(
    viewModel: StockLookupViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    // Bật scanner khi vào màn, tắt khi rời màn → màn khác không xử lý quét sai route.
    DisposableEffect(Unit) {
        viewModel.startScanner()
        onDispose { viewModel.stopScanner() }
    }

    XcloudScaffold(
        title = "Tra cứu",
        subtitle = "Quét hoặc nhập serial, lot, SKU hoặc LPN để xem tồn hiện tại, sản phẩm liên quan và lịch sử luân chuyển.",
        onBack = onBack,
    ) {
        if (state.isOffline) {
            OfflineBanner()
        }

        SectionCard(title = "Quét hoặc nhập mã") {
            InfoPill(text = "Kho: ${state.currentWarehouseLabel}")
            PdaScanField(
                value = state.manualCode,
                onValueChange = viewModel::updateManualCode,
                label = "Serial / lot / SKU / LPN",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = viewModel::submitManual,
            )
            if (state.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Đang tra cứu: ${state.busyCode ?: state.query}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!state.autoSubmitScanInput) {
                Text(
                    text = "Chế độ quét hiện tại chỉ điền mã vào ô. Quét xong rồi bấm “Tra cứu” để xem tồn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = viewModel::submitManual,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Tra cứu")
            }
        }

        if (state.errorMessage != null) {
            ErrorBox(
                message = state.errorMessage,
                retryable = state.errorRetryable,
                onRetry = viewModel::retry,
                onDismiss = viewModel::dismissError,
            )
        }

        Text(
            text = "Lịch sử tra cứu gần đây",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (state.history.isEmpty()) {
            EmptyHistory()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.history.forEach { item ->
                    HistoryCard(
                        item = item,
                        enabled = !state.isLoading,
                        onClick = { viewModel.lookupHistory(item.code) },
                    )
                }
            }
        }
    }

    state.detailResult?.let { result ->
        DetailSheet(result = result, onDismiss = viewModel::dismissResult)
    }

    state.notFoundCode?.let { code ->
        NotFoundSheet(code = code, onDismiss = viewModel::dismissResult)
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
private fun ErrorBox(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.red50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.red200, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = LookupPalette.red800)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (retryable) {
                OutlinedButton(onClick = onRetry) { Text("Thử lại") }
            }
            OutlinedButton(onClick = onDismiss) { Text("Đóng") }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = LookupPalette.zinc400,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Chưa có mã nào được tra cứu trên thiết bị này.",
            style = MaterialTheme.typography.bodyMedium,
            color = LookupPalette.zinc500,
        )
    }
}

@Composable
private fun HistoryCard(
    item: LookupHistoryItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val kind = (item.matchKind ?: inferKind(item.code)).uppercase()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = LookupPalette.white,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LookupPalette.zinc200),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = LookupPalette.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = item.code,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.productLabel ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = LookupPalette.zinc500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatTimeShort(item.updatedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LookupPalette.zinc700,
                )
                KindBadge(kind = kind)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    result: StockLookupResult,
    onDismiss: () -> Unit,
) {
    val match = result.match ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LookupPalette.white,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DetailHeader(match = match, onClose = onDismiss)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            result.warnings.forEach { warning -> WarningRow(warning) }

            // 4 ô tổng tồn.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Tồn thực", formatQty(result.summary.totalOnHand), LookupPalette.sky50, LookupPalette.sky800, Modifier.weight(1f))
                SummaryTile("Khả dụng", formatQty(result.summary.totalAvailable), LookupPalette.emerald50, LookupPalette.emerald800, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Giữ chỗ", formatQty(result.summary.totalReserved), LookupPalette.amber50, LookupPalette.amber800, Modifier.weight(1f))
                SummaryTile("Đã khóa", formatQty(result.summary.totalLocked), LookupPalette.zinc50, LookupPalette.zinc800, Modifier.weight(1f))
            }

            // Chip đếm.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip("Kho", result.summary.warehouseCount.toString())
                MetaChip("Vị trí", result.summary.locationCount.toString())
                MetaChip("LPN", result.summary.activeLpnCount.toString())
                MetaChip("Sự kiện", result.summary.eventCount.toString())
            }

            LookupSection(title = "Thông tin mã") {
                InfoRow("Loại mã", kindBadgeLabel(match.kind), accent = true)
                InfoRow("Mã chính", match.code ?: "—")
                InfoRow("Loại quản lý", match.trackingType ?: "—")
                InfoRow("Trạng thái", match.status ?: "—")
                InfoRow("Mã tham chiếu", match.referenceCode ?: "—")
                match.lotNumber?.let { InfoRow("Mã lô", it) }
                match.serialNumber?.let { InfoRow("Mã serial", it) }
                match.lpnCode?.let { InfoRow("LPN", it) }
            }

            LookupSection(title = "Thông tin sản phẩm") {
                InfoRow("Mã sản phẩm", match.productCode ?: "—")
                InfoRow("Tên sản phẩm", match.productName ?: "—")
                InfoRow("Đơn vị tính", match.uomName ?: match.uomCode ?: "—")
                InfoRow("Loại quản lý", match.trackingType ?: "—")
            }

            val isLpn = match.kind.equals("LPN", ignoreCase = true)

            LookupSection(title = "Hiện trạng tồn kho") {
                if (result.rows.isEmpty()) {
                    EmptyStateMessage(
                        if (isLpn) "LPN tra cứu này không có dòng tồn rời riêng."
                        else "Không có dòng tồn nào trong phạm vi được cấp quyền.",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        result.rows.forEach { row -> CurrentRowCard(row) }
                    }
                }
            }

            LookupSection(title = if (isLpn) "Nội dung LPN" else "LPN đang chứa") {
                if (isLpn) {
                    if (result.lpnContents.isEmpty()) {
                        EmptyStateMessage("LPN này hiện không còn hàng bên trong.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            result.lpnContents.forEach { row -> LpnContentCard(row) }
                        }
                    }
                } else {
                    if (result.activeLpns.isEmpty()) {
                        EmptyStateMessage("Không có LPN active đang chứa mã này.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            result.activeLpns.forEach { row -> ActiveLpnCard(row) }
                        }
                    }
                }
            }

            LookupSection(title = "Dòng thời gian sự kiện") {
                if (result.events.isEmpty()) {
                    EmptyStateMessage("Chưa có lịch sử phát sinh trong phạm vi được cấp quyền.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        result.events.forEach { event -> EventCard(event) }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun DetailHeader(match: StockMatch, onClose: () -> Unit) {
    val kind = match.kind.uppercase()
    val headerBg = when (kind) {
        "SERIAL" -> LookupPalette.sky50
        "LOT" -> LookupPalette.emerald50
        "LPN" -> LookupPalette.amber50
        else -> LookupPalette.zinc50
    }
    val accentBar = when (kind) {
        "SERIAL" -> LookupPalette.sky400
        "LOT" -> LookupPalette.emerald400
        "LPN" -> LookupPalette.amber400
        else -> LookupPalette.zinc400
    }
    val captionTone = when (kind) {
        "SERIAL" -> LookupPalette.sky700
        "LOT" -> LookupPalette.emerald700
        "LPN" -> LookupPalette.amber700
        else -> LookupPalette.zinc700
    }
    val productLine = if (match.productCode != null && match.productName != null) {
        "${match.productCode} — ${match.productName}"
    } else {
        match.label ?: match.productName ?: match.productCode ?: "—"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(vertical = 4.dp)
                .width(4.dp)
                .heightIn(min = 44.dp)
                .background(accentBar, RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = LookupPalette.zinc600)
        }
        Column(modifier = Modifier.padding(start = 12.dp, end = 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TRA CỨU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = captionTone,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = match.code ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = LookupPalette.zinc900,
                    modifier = Modifier.weight(1f, fill = false),
                )
                KindBadge(kind = kind)
            }
            Text(
                text = productLine,
                style = MaterialTheme.typography.bodyMedium,
                color = LookupPalette.zinc700,
            )
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    val container = when (kind.uppercase()) {
        "SERIAL" -> LookupPalette.sky700
        "LOT" -> LookupPalette.emerald700
        "LPN" -> LookupPalette.amber700
        else -> LookupPalette.zinc700
    }
    Surface(color = container, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = kindBadgeLabel(kind),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = LookupPalette.white,
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    Surface(color = LookupPalette.zinc100, shape = RoundedCornerShape(999.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = LookupPalette.zinc700)
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = LookupPalette.zinc900)
        }
    }
}

@Composable
private fun LookupSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.white, RoundedCornerShape(20.dp))
            .border(1.dp, LookupPalette.zinc100, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = LookupPalette.primary, modifier = Modifier.size(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LookupPalette.zinc900)
        }
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String, accent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LookupPalette.zinc500,
            modifier = Modifier.width(108.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (accent) LookupPalette.primary else LookupPalette.zinc900,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CurrentRowCard(row: StockRow) {
    val dates = buildList {
        formatDateOnly(row.inboundDate)?.let { add("Ngày nhập: $it") }
        formatDateOnly(row.manufactureDate)?.let { add("NSX: $it") }
        formatDateOnly(row.expiryDate)?.let { add("HSD: $it") }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.zinc50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.zinc200, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.warehouseCode ?: row.warehouseName ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LookupPalette.zinc900,
            )
            Text(
                text = row.locationCode ?: row.locationName ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = LookupPalette.zinc600,
            )
            row.trackingValue?.takeIf { it.isNotBlank() && it != "—" }?.let {
                Text(text = it.uppercase(), style = MaterialTheme.typography.labelSmall, color = LookupPalette.zinc500)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("Tồn thực", formatQty(row.quantityOnHand), LookupPalette.sky50, LookupPalette.sky700, Modifier.weight(1f))
            MiniMetric("Khả dụng", formatQty(row.quantityAvailable), LookupPalette.emerald50, LookupPalette.emerald700, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("Giữ chỗ", formatQty(row.quantityReserved), LookupPalette.amber50, LookupPalette.amber700, Modifier.weight(1f))
            MiniMetric("Đã khóa", formatQty(row.quantityLocked), LookupPalette.zinc100, LookupPalette.zinc700, Modifier.weight(1f))
        }
        if (dates.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dates.forEach { DateChip(it) }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun DateChip(text: String) {
    Surface(color = LookupPalette.white, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, LookupPalette.zinc200)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = LookupPalette.zinc600,
        )
    }
}

@Composable
private fun ActiveLpnCard(row: StockActiveLpn) {
    val location = listOfNotNull(
        row.warehouseCode ?: row.warehouseName,
        row.locationCode ?: row.locationName,
    ).joinToString(" • ").ifBlank { "—" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.zinc50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.zinc200, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = row.lpnCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LookupPalette.zinc900)
                Text(text = location, style = MaterialTheme.typography.bodySmall, color = LookupPalette.zinc600)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(
                    text = if (row.isSealed) "Niêm phong" else (row.status ?: "Hoạt động"),
                    sealed = row.isSealed,
                )
                Text(text = formatQty(row.packedQtyBase), style = MaterialTheme.typography.labelMedium, color = LookupPalette.zinc500)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("Số lượng", formatQty(row.packedQtyBase), LookupPalette.sky50, LookupPalette.sky700, Modifier.weight(1f))
            MiniMetric("Dòng chứa", row.contentsCount.toString(), LookupPalette.zinc100, LookupPalette.zinc700, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusPill(text: String, sealed: Boolean) {
    val bg = if (sealed) LookupPalette.amber100 else LookupPalette.emerald100
    val fg = if (sealed) LookupPalette.amber800 else LookupPalette.emerald800
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
private fun LpnContentCard(row: StockLpnContent) {
    val title = listOfNotNull(row.productCode, row.productName).joinToString(" — ").ifBlank { "—" }
    val secondary = listOfNotNull(
        row.trackingType,
        listOfNotNull(row.lotNumber, row.serialNumber).joinToString(" • ").ifBlank { null },
    ).joinToString(" • ").ifBlank { "—" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.zinc50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.zinc200, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LookupPalette.zinc900)
            Text(text = secondary, style = MaterialTheme.typography.bodySmall, color = LookupPalette.zinc600)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = formatQty(row.qtyBase), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LookupPalette.zinc900)
            Text(text = row.uomName ?: row.uomCode ?: "—", style = MaterialTheme.typography.labelMedium, color = LookupPalette.zinc500)
        }
    }
}

@Composable
private fun EventCard(event: StockEvent) {
    val meta = listOfNotNull(
        event.referenceCode?.let { "#$it" },
        resolveLocationSummary(event),
        event.actorName,
        event.partnerName,
        event.lpnCode?.let { "LPN $it" },
    )
    val note = event.reasonNote ?: event.notes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.zinc50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.zinc200, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBadge(event.source)
                Text(text = event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LookupPalette.zinc900)
            }
            event.subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = LookupPalette.zinc600) }
            if (meta.isNotEmpty()) {
                Text(text = meta.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = LookupPalette.zinc500)
            }
            note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = LookupPalette.zinc700) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            formatDateTimeShort(event.time)?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = LookupPalette.zinc500)
            }
            event.quantityText?.let {
                val color = when {
                    event.quantityDelta == null -> LookupPalette.zinc700
                    event.quantityDelta >= 0 -> LookupPalette.emerald700
                    else -> LookupPalette.red600
                }
                Text(text = it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val (bg, fg) = when (source.uppercase()) {
        "STOCK" -> LookupPalette.sky50 to LookupPalette.sky700
        "LPN" -> LookupPalette.amber50 to LookupPalette.amber700
        else -> LookupPalette.emerald50 to LookupPalette.emerald700
    }
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = sourceLabel(source),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
private fun WarningRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.amber50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.amber100, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = LookupPalette.amber700, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = LookupPalette.amber900)
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LookupPalette.zinc50, RoundedCornerShape(16.dp))
            .border(1.dp, LookupPalette.zinc200, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = LookupPalette.zinc500)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotFoundSheet(code: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LookupPalette.white,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Không tìm thấy mã", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Không tìm thấy mã “$code” trong phạm vi được cấp quyền.",
                style = MaterialTheme.typography.bodyMedium,
                color = LookupPalette.zinc700,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
            ) {
                Text("Đóng")
            }
        }
    }
}

// ─── helpers ───────────────────────────────────────────────────────────────

private fun kindBadgeLabel(kind: String): String =
    if (kind.equals("PRODUCT", ignoreCase = true)) "SKU" else kind.uppercase()

private fun inferKind(code: String): String {
    val upper = code.uppercase()
    return when {
        upper.startsWith("LPN") -> "LPN"
        upper.startsWith("LOT") -> "LOT"
        upper.startsWith("SN") || upper.startsWith("SER") -> "SERIAL"
        else -> "PRODUCT"
    }
}

private fun sourceLabel(source: String): String = when (source.uppercase()) {
    "STOCK" -> "Tồn kho"
    "LPN" -> "LPN"
    else -> "Kiểm kê"
}

private fun resolveLocationSummary(event: StockEvent): String? = when {
    event.fromLocationCode != null && event.toLocationCode != null -> "${event.fromLocationCode} → ${event.toLocationCode}"
    event.locationCode != null -> event.locationCode
    event.toLocationCode != null -> "Đích: ${event.toLocationCode}"
    event.fromLocationCode != null -> "Nguồn: ${event.fromLocationCode}"
    else -> null
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale("vi", "VN"), "%,d", value.toLong())
    } else {
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
}

private val DATE_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DATETIME_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val TIME_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun parseDate(value: String?): LocalDate? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { OffsetDateTime.parse(raw).toLocalDate() }
        .recoverCatching { LocalDateTime.parse(raw).toLocalDate() }
        .recoverCatching { LocalDate.parse(raw) }
        .recoverCatching { LocalDate.parse(raw.take(10)) }
        .getOrNull()
}

private fun formatDateOnly(value: String?): String? = parseDate(value)?.format(DATE_OUT)

private fun formatDateTimeShort(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val dateTime = runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(raw) }
        .getOrNull()
    if (dateTime != null) return dateTime.format(DATETIME_OUT)
    return parseDate(raw)?.format(DATE_OUT)
}

private fun formatTimeShort(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(TIME_OUT)
    }.getOrDefault("")
}
