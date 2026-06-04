package vn.delfi.xcloudwms.feature.putaway

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold
import vn.delfi.xcloudwms.core.ui.components.alwaysFocusedScanInput
import vn.delfi.xcloudwms.data.putaway.PutawayLineValidator
import vn.delfi.xcloudwms.domain.model.PaDraftLine
import vn.delfi.xcloudwms.domain.model.PaSessionStatus

@Composable
fun PutawayScreen(
    viewModel: PutawayViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBackConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.onScreenEntered()
        onDispose { viewModel.onScreenLeft() }
    }

    val handleBack: () -> Unit = {
        if (state.session != null && state.draftLines.isNotEmpty()) {
            showBackConfirm = true
        } else {
            onBack()
        }
    }

    if (showBackConfirm) {
        AlertDialog(
            onDismissRequest = { showBackConfirm = false },
            title = { Text("Rời phiên sắp xếp?") },
            text = {
                Text(
                    "Các dòng nháp đã lưu vẫn được giữ trên máy chủ và có thể tiếp tục sau. " +
                        "Phiên chỉ di chuyển tồn khi bạn bấm Hoàn tất.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirm = false
                    onBack()
                }) { Text("Rời đi") }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirm = false }) { Text("Ở lại") }
            },
        )
    }

    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Phiếu đã thay đổi") },
            text = { Text(conflict.message) },
            confirmButton = {
                TextButton(onClick = viewModel::reloadAfterConflict) { Text("Tải lại") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConflict) { Text("Đóng") }
            },
        )
    }

    XcloudScaffold(
        title = "Sắp xếp kho",
        subtitle = "Quét vị trí nguồn → sản phẩm/serial/lot → số lượng → vị trí đích, rồi thêm dòng và hoàn tất.",
        onBack = handleBack,
    ) {
        if (state.isOffline) {
            Banner(BannerTone.WARNING, "Mất kết nối mạng. Một số thao tác có thể không thực hiện được.")
        }
        if (state.usingCachedData) {
            Banner(BannerTone.WARNING, "Đang xem dữ liệu đã lưu (ngoại tuyến). Cần có mạng để hoàn tất phiếu.")
        }
        state.banner?.let { banner ->
            Banner(banner.tone, banner.message, onDismiss = viewModel::dismissBanner)
        }

        when {
            !state.hasWarehouse -> SectionCard(title = "Chưa chọn kho") {
                Text("Vui lòng quay lại trang chủ và chọn kho làm việc trước khi sắp xếp.")
            }

            state.isLoadingContext -> LoadingCard("Đang tải vị trí và sản phẩm…")

            state.contextError != null -> SectionCard(title = "Không tải được dữ liệu") {
                Text(state.contextError!!, color = MaterialTheme.colorScheme.error)
                FullWidthOutlinedButton("Thử lại", onClick = viewModel::loadContext)
            }

            state.session == null -> StartSessionCard(state, viewModel)

            else -> {
                SessionHeaderCard(state)
                StepperCard(state, viewModel)
                DraftLinesCard(state, viewModel)
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    if (state.isSubmitting) {
                        InlineSpinner()
                    } else {
                        Text("Hoàn tất (${state.draftLines.size} dòng)")
                    }
                }
            }
        }
    }
}

@Composable
private fun StartSessionCard(state: PutawayUiState, viewModel: PutawayViewModel) {
    SectionCard(title = "Tạo phiên sắp xếp") {
        InfoPill(text = "Kho: ${state.warehouseLabel}")
        OutlinedTextField(
            value = state.sessionNotes,
            onValueChange = viewModel::updateSessionNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ghi chú phiên (tuỳ chọn)") },
            singleLine = true,
        )
        Button(
            onClick = viewModel::startSession,
            enabled = !state.isStartingSession,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            if (state.isStartingSession) InlineSpinner() else Text("Tạo phiên sắp xếp")
        }
    }
}

@Composable
private fun SessionHeaderCard(state: PutawayUiState) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.session?.code ?: "(Chưa có mã)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Kho: ${state.warehouseLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(state.session?.status ?: PaSessionStatus.DRAFT)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepperCard(state: PutawayUiState, viewModel: PutawayViewModel) {
    SectionCard(title = "Thêm dòng sắp xếp") {
        ScanTargetSelector(state.activeScanField, viewModel::setActiveScanField)

        StepLabel(1, "Vị trí nguồn", active = state.activeScanField == PaScanField.FROM_LOCATION)
        DropdownField(
            label = "Chọn vị trí nguồn",
            options = state.locations.map { it.id to it.label },
            selectedId = state.fromLocationId.ifBlank { null },
            onSelect = viewModel::selectFromLocation,
            enabled = state.canEditSession,
        )

        StepLabel(2, "Sản phẩm / Serial / Lot", active = state.activeScanField == PaScanField.CODE)
        DropdownField(
            label = "Chọn sản phẩm (tuỳ chọn)",
            options = state.products.map { it.id to it.label },
            selectedId = state.selectedProductId,
            onSelect = { id -> viewModel.selectProduct(id) },
            enabled = state.canEditSession,
            allowClear = true,
            onClear = { viewModel.selectProduct(null) },
        )
        if (state.requiresCode) {
            OutlinedTextField(
                value = state.scannedCode,
                onValueChange = viewModel::updateScannedCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .alwaysFocusedScanInput(
                        enabled = state.canEditSession,
                        keepFocused = false,
                    ),
                singleLine = true,
                enabled = state.canEditSession,
                label = { Text(codeFieldLabel(state)) },
            )
        }

        if (state.showQtyInput) {
            StepLabel(3, "Số lượng", active = false)
            QtyStepper(
                qtyText = state.qtyText,
                enabled = state.canEditSession,
                onChange = viewModel::updateQty,
            )
        }

        StepLabel(4, "Vị trí đích", active = state.activeScanField == PaScanField.TO_LOCATION)
        DropdownField(
            label = "Chọn vị trí đích",
            options = state.locations
                .filter { it.id != state.fromLocationId }
                .map { it.id to it.label },
            selectedId = state.toLocationId.ifBlank { null },
            onSelect = viewModel::selectToLocation,
            enabled = state.canEditSession,
        )

        OutlinedTextField(
            value = state.lineNotes,
            onValueChange = viewModel::updateLineNotes,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state.canEditSession,
            label = { Text("Ghi chú dòng (tuỳ chọn)") },
        )

        Button(
            onClick = viewModel::addLine,
            enabled = state.canEditSession && !state.isAddingLine,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            if (state.isAddingLine) InlineSpinner() else Text("Thêm dòng")
        }
    }
}

@Composable
private fun ScanTargetSelector(active: PaScanField, onSelect: (PaScanField) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanTargetChip("Nguồn", active == PaScanField.FROM_LOCATION, Modifier.weight(1f)) {
            onSelect(PaScanField.FROM_LOCATION)
        }
        ScanTargetChip("Mã hàng", active == PaScanField.CODE, Modifier.weight(1f)) {
            onSelect(PaScanField.CODE)
        }
        ScanTargetChip("Đích", active == PaScanField.TO_LOCATION, Modifier.weight(1f)) {
            onSelect(PaScanField.TO_LOCATION)
        }
    }
    Text(
        text = "Máy quét sẽ điền vào ô đang chọn ở trên.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScanTargetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 44.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QtyStepper(qtyText: String, enabled: Boolean, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                val current = qtyText.toDoubleOrNull() ?: 0.0
                val next = (current - 1).coerceAtLeast(0.0)
                onChange(PutawayLineValidator.formatQty(next))
            },
            enabled = enabled,
            modifier = Modifier.size(52.dp),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }

        OutlinedTextField(
            value = qtyText,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Số lượng") },
        )

        OutlinedButton(
            onClick = {
                val current = qtyText.toDoubleOrNull() ?: 0.0
                onChange(PutawayLineValidator.formatQty(current + 1))
            },
            enabled = enabled,
            modifier = Modifier.size(52.dp),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun DraftLinesCard(state: PutawayUiState, viewModel: PutawayViewModel) {
    SectionCard(title = "Dòng nháp (${state.draftLines.size})") {
        when {
            state.isLoadingLines && state.draftLines.isEmpty() -> InlineSpinner()
            state.draftLines.isEmpty() -> Text("Chưa có dòng nào. Thêm dòng ở trên để bắt đầu.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.draftLines.forEach { line ->
                    DraftLineRow(
                        line = line,
                        deleting = state.deletingDetailId == line.id,
                        deleteEnabled = state.canEditSession && state.deletingDetailId == null,
                        onDelete = { viewModel.deleteLine(line.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftLineRow(
    line: PaDraftLine,
    deleting: Boolean,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.productLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${line.fromLocationLabel}  →  ${line.toLocationLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                line.serialNumber?.let { LabeledLine("Serial", it) }
                line.lotNumber?.let { LabeledLine("Lô", it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(
                        text = "SL: ${PutawayLineValidator.formatQty(line.quantity)}" +
                            (line.uomLabel?.let { " $it" } ?: ""),
                    )
                }
                line.notes?.let { LabeledLine("Ghi chú", it) }
            }
            TextButton(onClick = onDelete, enabled = deleteEnabled) {
                if (deleting) InlineSpinner() else Text("Xoá", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    allowClear: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowClear && selectedId != null) {
                DropdownMenuItem(
                    text = { Text("— Bỏ chọn —") },
                    onClick = {
                        expanded = false
                        onClear?.invoke()
                    },
                )
            }
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("(Không có dữ liệu)") }, onClick = { expanded = false })
            }
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

private fun codeFieldLabel(state: PutawayUiState): String = when (state.trackingType) {
    vn.delfi.xcloudwms.domain.model.PaTrackingType.SERIAL -> "Quét serial"
    vn.delfi.xcloudwms.domain.model.PaTrackingType.LOT -> "Quét lô"
    vn.delfi.xcloudwms.domain.model.PaTrackingType.NONE -> "Quét SKU / serial / lô"
}

@Composable
private fun StatusBadge(status: PaSessionStatus) {
    val (label, color) = when (status) {
        PaSessionStatus.DRAFT -> "NHÁP" to MaterialTheme.colorScheme.tertiary
        PaSessionStatus.COMPLETED -> "HOÀN TẤT" to MaterialTheme.colorScheme.primary
        PaSessionStatus.LOCKED -> "ĐÃ KHOÁ" to MaterialTheme.colorScheme.onSurfaceVariant
        PaSessionStatus.OTHER -> status.name to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepLabel(step: Int, text: String, active: Boolean) {
    Text(
        text = "Bước $step • $text" + if (active) "  (đang quét)" else "",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoadingCard(message: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(message)
        }
    }
}

@Composable
private fun InlineSpinner() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp))
}

@Composable
private fun FullWidthOutlinedButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) { Text(text) }
}

@Composable
private fun Banner(tone: BannerTone, message: String, onDismiss: (() -> Unit)? = null) {
    val container = when (tone) {
        BannerTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        BannerTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = when (tone) {
        BannerTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        BannerTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                Box(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    }
}
