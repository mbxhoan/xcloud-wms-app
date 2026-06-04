package vn.delfi.xcloudwms.feature.inventorycount

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import vn.delfi.xcloudwms.domain.model.IcLine
import vn.delfi.xcloudwms.domain.model.IcTrackingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryCountScreen(
    viewModel: InventoryCountViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

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

    if (state.showFinishDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinishDialog,
            title = { Text("Kết thúc kiểm kê") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đóng phiếu kiểm kê. Việc cân bằng/duyệt điều chỉnh tồn thực hiện trên webapp.")
                    OutlinedTextField(
                        value = state.finishNote,
                        onValueChange = viewModel::updateFinishNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ghi chú (bắt buộc)") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmFinish,
                    enabled = state.finishNote.isNotBlank() && !state.isFinishing,
                ) { Text("Kết thúc") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFinishDialog) { Text("Huỷ") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = state.header?.displayCode ?: "Phiếu kiểm kê", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Quay lại") } },
            )
        },
        bottomBar = {
            if (state.header != null && !state.isLoading) {
                CountActionBar(state = state, onFinish = viewModel::openFinishDialog)
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
                Text("Đang tải phiếu kiểm kê…", modifier = Modifier.padding(top = 12.dp))
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
                    item { IcOfflineBanner() }
                }
                state.banner?.let { banner ->
                    item { IcBannerCard(banner, onDismiss = viewModel::dismissBanner) }
                }
                item { HeaderSummaryCard(state) }
                item { ActiveLineCard(state, viewModel) }
                item {
                    Text(
                        text = "Danh sách dòng (${state.lines.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.lines, key = { it.id }) { line ->
                    LineCard(
                        line = line,
                        isActive = line.id == state.activeLineId,
                        isProcessing = line.id == state.processingLineId,
                        isBlind = state.isBlind,
                        onSelect = { viewModel.selectLine(line.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSummaryCard(state: InventoryCountUiState) {
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
            IcStatusBadge(header.status)
        }
        header.warehouseLabel?.let { IcLabeledLine("Kho", it) }
        if (state.isBlind) IcLabeledLine("Chế độ", "Đếm mù (ẩn tồn hệ thống)")

        if (!state.isBlind) {
            val expected = state.totalExpected
            val counted = state.totalCounted
            val progress = if (expected > 0) (counted / expected).toFloat().coerceIn(0f, 1f) else 0f
            Text(
                text = "Tiến độ đếm: ${formatQty(counted)} / ${formatQty(expected)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 8.dp),
            )
        } else {
            Text(
                text = "Đã đếm: ${formatQty(state.totalCounted)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
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
private fun ActiveLineCard(state: InventoryCountUiState, viewModel: InventoryCountViewModel) {
    val line = state.activeLine
    SectionCard(title = "Đang đếm") {
        if (line == null) {
            Text("Chọn một dòng bên dưới để bắt đầu đếm.")
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
                    text = if (state.isBlind) {
                        "Đã đếm ${formatQty(line.countedQty)}${line.uomLabel?.let { " $it" } ?: ""}"
                    } else {
                        "Đã đếm ${formatQty(line.countedQty)} / tồn ${formatQty(line.expectedQty)}${line.uomLabel?.let { " $it" } ?: ""}"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (!state.canScan) {
            Text(
                text = "Không thể thao tác khi phiếu ở trạng thái ${state.header?.status?.label ?: "—"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        LocationPicker(state = state, viewModel = viewModel)

        if (state.showQtyInput) {
            OutlinedTextField(
                value = state.qtyText,
                onValueChange = viewModel::updateQty,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Số lượng đếm thêm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        when (line.trackingType) {
            IcTrackingType.SERIAL -> Text(
                text = "Quét liên tục từng serial tìm thấy (mỗi serial = 1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IcTrackingType.LOT -> Text(
                text = "Quét hoặc nhập mã lô rồi xác nhận số lượng đếm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IcTrackingType.NONE -> Text(
                text = "Quét mã sản phẩm hoặc bấm “Đếm SL” để ghi nhận số lượng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.scannedCode,
            onValueChange = viewModel::updateScannedCode,
            modifier = Modifier
                .fillMaxWidth()
                .alwaysFocusedScanInput(keepFocused = false),
            singleLine = true,
            label = { Text("Mã quét (serial / lô / sản phẩm)") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::submitScannedCode,
                enabled = !state.isBusy && state.scannedCode.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                if (state.processingLineId == line.id) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Đếm theo mã quét")
                }
            }
            if (line.trackingType == IcTrackingType.NONE) {
                OutlinedButton(
                    onClick = viewModel::countActiveNoneQuantity,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text("Đếm SL") }
            }
        }
    }
}

@Composable
private fun LocationPicker(state: InventoryCountUiState, viewModel: InventoryCountViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = state.filteredLocations()
    Box {
        OutlinedTextField(
            value = state.locationQuery,
            onValueChange = {
                viewModel.updateLocationQuery(it)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Vị trí (tuỳ chọn)") },
            trailingIcon = {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Đóng" else "Chọn") }
            },
            supportingText = {
                val selected = state.selectedLocation
                Text(selected?.let { "Đã chọn: ${it.label}" } ?: "Để trống nếu đếm theo vị trí mặc định của dòng.")
            },
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { loc ->
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
}

@Composable
private fun LineCard(
    line: IcLine,
    isActive: Boolean,
    isProcessing: Boolean,
    isBlind: Boolean,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            line.locationLabel?.let {
                Text(
                    text = "Vị trí: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBlind) {
                Text(
                    text = "Đã đếm ${formatQty(line.countedQty)}${line.uomLabel?.let { " $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                val diff = line.diffQty
                val diffTone = when {
                    diff == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    diff < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Text(
                    text = "Đếm ${formatQty(line.countedQty)} / tồn ${formatQty(line.expectedQty)}${line.uomLabel?.let { " $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Lệch: " + if (diff > 0) "+${formatQty(diff)}" else formatQty(diff),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = diffTone,
                )
            }
        }
    }
}

@Composable
private fun CountActionBar(
    state: InventoryCountUiState,
    onFinish: () -> Unit,
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
            if (state.canFinish) {
                Button(
                    onClick = onFinish,
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                ) {
                    if (state.isFinishing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Kết thúc kiểm kê")
                    }
                }
                Text(
                    text = "Kết thúc chỉ đóng phiếu. Cân bằng/duyệt điều chỉnh tồn thực hiện trên webapp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Phiếu ở trạng thái ${state.header?.status?.label ?: "—"}, không còn thao tác kiểm kê.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackingPill(trackingType: IcTrackingType) {
    val label = when (trackingType) {
        IcTrackingType.SERIAL -> "Serial"
        IcTrackingType.LOT -> "Lô"
        IcTrackingType.NONE -> "SKU"
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
private fun IcBannerCard(banner: IcBanner, onDismiss: () -> Unit) {
    val (container, onContainer) = when (banner.tone) {
        IcBannerTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        IcBannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        IcBannerTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
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
