package vn.delfi.xcloudwms.feature.goodsreceipt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.alwaysFocusedScanInput
import vn.delfi.xcloudwms.domain.model.GrLine
import vn.delfi.xcloudwms.domain.model.GrTrackingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsReceiptReceiveScreen(
    viewModel: GoodsReceiptReceiveViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var sessionDetailLineId by remember { mutableStateOf<String?>(null) }
    val sessionDetailLine = state.lines.firstOrNull { it.id == sessionDetailLineId }

    DisposableEffect(Unit) {
        viewModel.onScreenEntered()
        onDispose { viewModel.onScreenLeft() }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            kotlinx.coroutines.delay(1200)
            onBack()
        }
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(confirm.title) },
            text = { Text(confirm.message) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDialog) { Text(confirm.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Huỷ") }
            },
        )
    }

    if (sessionDetailLine != null) {
        SessionDetailsDialog(
            line = sessionDetailLine,
            details = state.sessionDetailsForLine(sessionDetailLine.id),
            onDismiss = { sessionDetailLineId = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.header?.displayCode ?: "Phiếu nhập",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.header != null && !state.isLoading) {
                ReceiveActionBar(state = state, onSubmit = viewModel::submit, onComplete = viewModel::complete)
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Đang tải phiếu nhập…", modifier = Modifier.padding(top = 12.dp))
            }

            state.loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(title = "Không tải được phiếu") {
                    Text(state.loadError, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text("Thử lại") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) {
                    item { GrOfflineBanner() }
                }
                state.banner?.takeIf { state.activeLine == null }?.let { banner ->
                    item { GrBannerCard(banner, onDismiss = viewModel::dismissBanner) }
                }
                item { HeaderSummaryCard(state) }
                item {
                    ActiveLineCard(
                        state = state,
                        viewModel = viewModel,
                        onOpenSessionDetails = { lineId -> sessionDetailLineId = lineId },
                    )
                }
                item {
                    Text(
                        text = "Danh sách dòng (${state.lines.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.lines, key = { it.id }) { line ->
                    val sessionDetailCount = state.sessionDetailsForLine(line.id).size
                    LineCard(
                        line = line,
                        isActive = line.id == state.activeLineId,
                        isProcessing = line.id == state.processingLineId,
                        sessionDetailCount = sessionDetailCount,
                        onSelect = {
                            viewModel.selectLine(line.id)
                            if (sessionDetailCount > 0) {
                                sessionDetailLineId = line.id
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSummaryCard(state: GoodsReceiptReceiveUiState) {
    val header = state.header ?: return
    SectionCard {
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

        val expected = state.totalExpected
        val received = state.totalReceived
        val progress = if (expected > 0) (received / expected).toFloat().coerceIn(0f, 1f) else 0f
        Text(
            text = "Tiến độ nhận: ${formatQty(received)} / ${formatQty(expected)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 8.dp),
        )
        if (!header.status.isScannable) {
            Text(
                text = "Phiếu đang ở trạng thái ${header.status.label} — chỉ xem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveLineCard(
    state: GoodsReceiptReceiveUiState,
    viewModel: GoodsReceiptReceiveViewModel,
    onOpenSessionDetails: (String) -> Unit,
) {
    val line = state.activeLine
    val showLocationError = state.requiresLocationSelection && state.scannedCode.isNotBlank()
    SectionCard(title = "Đang nhận") {
        if (line == null) {
            Text("Chọn một dòng bên dưới để bắt đầu nhận hàng.")
            return@SectionCard
        }
        Text(
            text = line.productLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackingPill(line.trackingType)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "Đã nhận ${formatQty(line.receivedQty)}/${formatQty(line.expectedQty)}${line.uomLabel?.let { " $it" } ?: ""}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        val sessionDetailCount = state.sessionDetailsForLine(line.id).size
        if (sessionDetailCount > 0) {
            TextButton(
                onClick = { onOpenSessionDetails(line.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Xem mã đã quét trong phiên ($sessionDetailCount)")
            }
        }

        state.banner?.let { banner ->
            GrBannerCard(banner = banner, onDismiss = viewModel::dismissBanner)
        }

        if (!state.canScan) {
            Text(
                text = "Không thể thao tác khi phiếu ở trạng thái ${state.header?.status?.label ?: "—"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        LocationPicker(state = state, viewModel = viewModel, showError = showLocationError)

        if (state.showQtyInput) {
            OutlinedTextField(
                value = state.qtyText,
                onValueChange = viewModel::updateQty,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Số lượng mỗi lần nhận") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        if (state.needsMfgInput || state.needsExpiryInput) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.needsMfgInput) {
                    OutlinedTextField(
                        value = state.mfgDateText,
                        onValueChange = viewModel::updateMfgDate,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("NSX (YYYY-MM-DD)") },
                    )
                }
                if (state.needsExpiryInput) {
                    OutlinedTextField(
                        value = state.expiryDateText,
                        onValueChange = viewModel::updateExpiryDate,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("HSD (YYYY-MM-DD)") },
                    )
                }
            }
        }

        when (line.trackingType) {
            GrTrackingType.SERIAL -> Text(
                text = "Quét liên tục từng serial để nhận hàng (mỗi serial = 1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GrTrackingType.LOT -> Text(
                text = "Quét hoặc nhập mã lô rồi xác nhận số lượng nhận.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GrTrackingType.NONE -> Text(
                text = "Quét mã sản phẩm hoặc bấm “Nhận SL” để ghi nhận số lượng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.scannedCode,
            onValueChange = viewModel::updateScannedCode,
            modifier = Modifier
                .fillMaxWidth()
                .alwaysFocusedScanInput(
                    enabled = state.canScan,
                    keepFocused = false,
                    focusKey = "${state.activeLineId}:${state.selectedLocationId}:${state.processingLineId ?: "-"}",
                ),
            singleLine = true,
            label = { Text("Mã quét (serial / lô / sản phẩm)") },
        )
        if (!state.autoSubmitScanInput) {
            Text(
                text = "Cài đặt tự động Enter / Tab đang tắt. Quét xong rồi bấm “Nhận theo mã quét” để lưu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::submitScannedCode,
                enabled = state.canSubmitScannedCode,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                if (state.processingLineId == line.id) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(state.scanButtonLabel)
                }
            }
            if (line.trackingType == GrTrackingType.NONE) {
                OutlinedButton(
                    onClick = viewModel::receiveActiveNoneQuantity,
                    enabled = state.canReceiveNoneQuantity,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text("Nhận SL") }
            }
        }
    }
}

@Composable
private fun LocationPicker(
    state: GoodsReceiptReceiveUiState,
    viewModel: GoodsReceiptReceiveViewModel,
    showError: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedLocation
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Vị trí nhập (bắt buộc)",
            style = MaterialTheme.typography.labelLarge,
            color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = selected?.label ?: "Chọn vị trí nhập",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(if (expanded) "Đóng" else "Chọn")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                state.locations.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(loc.label) },
                        onClick = {
                            viewModel.selectLocation(loc.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            text = if (selected != null) {
                "Đã chọn: ${selected.label}"
            } else if (showError) {
                "Cần chọn vị trí nhập trước khi nhận theo mã quét."
            } else {
                "Chọn vị trí trong kho hiện tại trước khi nhận."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (showError && selected == null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SessionDetailsDialog(
    line: GrLine,
    details: List<GrSessionDetail>,
    onDismiss: () -> Unit,
) {
    val detailTitle = when (line.trackingType) {
        GrTrackingType.SERIAL -> "Danh sách serial"
        GrTrackingType.LOT -> "Danh sách lô"
        GrTrackingType.NONE -> "Danh sách SKU"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = line.productLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TrackingPill(line.trackingType)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$detailTitle trong phiên (${details.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (details.isEmpty()) {
                    Text(
                        text = "Chưa có mã nào được nhận trong phiên hiện tại.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(details, key = { it.id }) { detail ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = detail.code,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "SL: ${formatQty(detail.qty)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "Vị trí: ${detail.locationLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    detail.mfgDate?.let { mfgDate ->
                                        Text(
                                            text = "NSX: $mfgDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    detail.expiryDate?.let { expiryDate ->
                                        Text(
                                            text = "HSD: $expiryDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
    )
}

@Composable
private fun LineCard(
    line: GrLine,
    isActive: Boolean,
    isProcessing: Boolean,
    sessionDetailCount: Int,
    onSelect: () -> Unit,
) {
    val border = if (isActive) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (line.isComplete) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(20.dp),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.productLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TrackingPill(line.trackingType)
                }
            }
            val progress = if (line.expectedQty > 0) {
                (line.receivedQty / line.expectedQty).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 6.dp),
            )
            Text(
                text = "Đã nhận ${formatQty(line.receivedQty)} / ${formatQty(line.expectedQty)}${line.uomLabel?.let { " $it" } ?: ""}" +
                    if (line.isComplete) " • Đủ" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sessionDetailCount > 0) {
                Text(
                    text = "Đã quét trong phiên: $sessionDetailCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiveActionBar(
    state: GoodsReceiptReceiveUiState,
    onSubmit: () -> Unit,
    onComplete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.canSubmit || state.canComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.canSubmit) {
                        Button(
                            onClick = onSubmit,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 54.dp),
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Lưu")
                            }
                        }
                    }
                    if (state.canComplete) {
                        Button(
                            onClick = onComplete,
                            enabled = !state.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 54.dp),
                        ) {
                            if (state.isCompleting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Hoàn tất")
                            }
                        }
                    }
                }
            }
            if (!state.canSubmit && !state.canComplete) {
                Text(
                    text = "Phiếu ở trạng thái ${state.header?.status?.label ?: "—"}, không còn thao tác nhận.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackingPill(trackingType: GrTrackingType) {
    val label = when (trackingType) {
        GrTrackingType.SERIAL -> "Serial"
        GrTrackingType.LOT -> "Lô"
        GrTrackingType.NONE -> "SKU"
    }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GrBannerCard(banner: GrBanner, onDismiss: () -> Unit) {
    val (container, onContainer) = when (banner.tone) {
        GrBannerTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        GrBannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        GrBannerTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = banner.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    }
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
}
