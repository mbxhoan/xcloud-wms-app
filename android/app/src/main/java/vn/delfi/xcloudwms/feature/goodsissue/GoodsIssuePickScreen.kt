package vn.delfi.xcloudwms.feature.goodsissue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.alwaysFocusedScanInput
import vn.delfi.xcloudwms.domain.model.GiLine
import vn.delfi.xcloudwms.domain.model.GiTrackingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsIssuePickScreen(
    viewModel: GoodsIssuePickViewModel,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.header?.displayCode ?: "Phiếu xuất",
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
                PickActionBar(state = state, onSubmit = viewModel::submit, onComplete = viewModel::complete)
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
                Text("Đang tải phiếu xuất…", modifier = Modifier.padding(top = 12.dp))
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline) {
                    item { OfflineBanner() }
                }
                state.banner?.let { banner ->
                    item { BannerCard(banner, onDismiss = viewModel::dismissBanner) }
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
                        onSelect = { viewModel.selectLine(line.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSummaryCard(state: GoodsIssuePickUiState) {
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
            GiStatusBadge(header.status)
        }
        header.warehouseLabel?.let { LabeledLine("Kho", it) }
        header.partnerLabel?.let { LabeledLine("Đối tác", it) }

        val planned = state.totalPlanned
        val picked = state.totalPicked
        val progress = if (planned > 0) (picked / planned).toFloat().coerceIn(0f, 1f) else 0f
        Text(
            text = "Tiến độ pick: ${formatQty(picked)} / ${formatQty(planned)}",
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

@Composable
private fun ActiveLineCard(state: GoodsIssuePickUiState, viewModel: GoodsIssuePickViewModel) {
    val line = state.activeLine
    SectionCard(title = "Đang lấy") {
        if (line == null) {
            Text("Chọn một dòng bên dưới để bắt đầu lấy hàng.")
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
                    text = "Đã pick ${formatQty(line.pickedQty)}/${formatQty(line.plannedQty)}${line.uomLabel?.let { " $it" } ?: ""}",
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

        if (state.showQtyInput) {
            OutlinedTextField(
                value = state.qtyText,
                onValueChange = viewModel::updateQty,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Số lượng mỗi lần lấy") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        when (line.trackingType) {
            GiTrackingType.SERIAL -> Text(
                text = "Quét liên tục từng serial để lấy hàng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GiTrackingType.LOT -> Text(
                text = "Quét mã lot đã được reserve cho dòng này.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GiTrackingType.NONE -> Text(
                text = "Quét mã sản phẩm hoặc bấm “Lấy” để ghi nhận số lượng.",
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
            label = { Text("Mã quét (serial / lot / sản phẩm)") },
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
                    Text("Lấy theo mã quét")
                }
            }
            if (line.trackingType == GiTrackingType.NONE) {
                OutlinedButton(
                    onClick = viewModel::pickActiveNoneQuantity,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text("Lấy SL") }
            }
        }
    }
}

@Composable
private fun LineCard(
    line: GiLine,
    isActive: Boolean,
    isProcessing: Boolean,
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
            val progress = if (line.plannedQty > 0) {
                (line.pickedQty / line.plannedQty).toFloat().coerceIn(0f, 1f)
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
                text = "Đã pick ${formatQty(line.pickedQty)} / ${formatQty(line.plannedQty)}${line.uomLabel?.let { " $it" } ?: ""}" +
                    if (line.isComplete) " • Đủ" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickActionBar(
    state: GoodsIssuePickUiState,
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
                    text = "Phiếu ở trạng thái ${state.header?.status?.label ?: "—"}, không còn thao tác xuất.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackingPill(trackingType: GiTrackingType) {
    val label = when (trackingType) {
        GiTrackingType.SERIAL -> "Serial"
        GiTrackingType.LOT -> "Lot"
        GiTrackingType.NONE -> "SKU"
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
private fun BannerCard(banner: GiBanner, onDismiss: () -> Unit) {
    val (container, onContainer) = when (banner.tone) {
        GiBannerTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        GiBannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        GiBannerTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
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
