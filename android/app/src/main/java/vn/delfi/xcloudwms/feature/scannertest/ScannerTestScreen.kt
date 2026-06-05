package vn.delfi.xcloudwms.feature.scannertest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.scanner.ScannerMode
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode
import vn.delfi.xcloudwms.core.ui.components.InfoPill
import vn.delfi.xcloudwms.core.ui.components.PdaScanField
import vn.delfi.xcloudwms.core.ui.components.PdaScanFieldSettings
import vn.delfi.xcloudwms.core.ui.components.SectionCard
import vn.delfi.xcloudwms.core.ui.components.XcloudScaffold

@Composable
fun ScannerTestScreen(
    viewModel: ScannerTestViewModel,
    onBack: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value

    // Tự bật scanner khi vào màn, tắt khi rời màn → màn khác không xử lý quét sai route.
    DisposableEffect(Unit) {
        viewModel.startScanner()
        onDispose { viewModel.stopScanner() }
    }

    XcloudScaffold(
        title = "Quét thử mã",
        subtitle = "Bấm cò quét bên hông PDA để thử mã vạch hoặc QR ngay trên thiết bị.",
        onBack = onBack,
    ) {
        SectionCard(title = "Sẵn sàng quét") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(text = if (state.isActive) "Đang bật" else "Đang tắt")
                InfoPill(text = "Chế độ: ${state.selectedMode.label}")
            }

            Text(
                text = "Bấm cò quét của PDA. Không cần chạm vào ô nhập trước khi quét.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PdaScanField(
                value = state.captureInput,
                onValueChange = viewModel::updateCaptureInput,
                label = "Ô nhận quét từ PDA",
                modifier = Modifier.fillMaxWidth(),
                keepFocused = !state.softKeyboardEnabled,
                onSubmit = viewModel::submitCaptureInput,
                settingsOverride = PdaScanFieldSettings(
                    blockSoftKeyboard = !state.softKeyboardEnabled,
                    submitMode = ScannerSubmitMode.ENTER,
                    allowManualInputFallback = state.softKeyboardEnabled,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hiện bàn phím mềm khi chạm ô quét",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.softKeyboardEnabled,
                    onCheckedChange = viewModel::toggleSoftKeyboard,
                )
            }

            Text(
                text = "Nếu PM85 đang ở chế độ Keyboard Event hoặc Wedge, hãy giữ focus ở ô trên rồi bóp cò quét.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = viewModel::submitCaptureInput,
                enabled = state.captureInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Nhận mã đang có trong ô")
            }

            OutlinedTextField(
                value = if (state.lastRawScan == "—") "" else state.lastRawScan,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Kết quả quét") },
                placeholder = { Text("Mã đã xử lý sẽ hiện ở đây") },
            )

            Text(
                text = "Loại mã: ${state.lastParsedType}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Nguồn nhận: ${state.lastSourceLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Chuẩn mã: ${state.lastSymbology}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.latestEvent,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = viewModel::startScanner,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(if (state.isActive) "Bật lại máy quét" else "Bật máy quét")
                }

                OutlinedButton(
                    onClick = viewModel::testFeedback,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text("Thử beep + rung")
                }
            }
        }

        SectionCard(title = "Bộ thu hiện tại") {
            InfoPill(text = state.currentAdapters)
            Text(
                text = "Nếu PM85 đang bật Keyboard Event hoặc Intent Broadcast đúng cách, mã sẽ hiện ở phía trên ngay khi bóp cò quét.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Chế độ quét") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScannerMode.entries.toList()) { mode ->
                    FilterChip(
                        selected = state.selectedMode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quét serial liên tục (bỏ chặn trùng)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.continuousSerial,
                    onCheckedChange = viewModel::toggleContinuousSerial,
                )
            }
        }

        SectionCard(title = "Giả lập mã quét thủ công") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.manualCode,
                    onValueChange = viewModel::updateManualCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã quét thử") },
                    supportingText = {
                        Text("Ví dụ: SKU:SP001, LOC:A01 hoặc SN:SERIAL-0001")
                    },
                )
                Button(
                    onClick = viewModel::submitManualScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("Giả lập quét")
                }
            }
        }

        SectionCard(title = "Thiết lập Point Mobile PM85") {
            Text(
                text = "Nếu quét không vào app, trên PDA hãy mở EmKit > ScanSettings > bật Scanner. Có thể dùng Keyboard Event để thử nhanh, hoặc dùng Intent Broadcast / Custom Intent để ổn định hơn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nhận tín hiệu phát từ máy quét",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.broadcastEnabled,
                    onCheckedChange = viewModel::updateBroadcastEnabled,
                )
            }

            OutlinedTextField(
                value = state.broadcastAction,
                onValueChange = viewModel::updateBroadcastAction,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tên action") },
                supportingText = { Text("Gợi ý cho PM85: dùng cùng action này trong Custom Intent") },
            )
            OutlinedTextField(
                value = state.broadcastDataKey,
                onValueChange = viewModel::updateBroadcastDataKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Khoá dữ liệu chứa mã") },
                supportingText = { Text("Gợi ý: data") },
            )
            OutlinedTextField(
                value = state.broadcastSymbologyKey,
                onValueChange = viewModel::updateBroadcastSymbologyKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Khoá dữ liệu loại mã (tuỳ chọn)") },
                supportingText = { Text("Gợi ý: symbology") },
            )
            Button(
                onClick = viewModel::saveBroadcastConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Lưu cấu hình phát tín hiệu")
            }
        }

        SectionCard(title = "Lịch sử gần nhất") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.eventHistory.ifEmpty { listOf("Chưa có sự kiện nào.") }.forEach { event ->
                    SectionCard {
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
