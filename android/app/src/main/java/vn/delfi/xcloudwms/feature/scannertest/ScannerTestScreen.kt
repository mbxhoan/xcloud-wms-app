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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.delfi.xcloudwms.core.scanner.ScannerMode
import vn.delfi.xcloudwms.core.ui.components.InfoPill
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
        title = "Kiểm tra máy quét",
        subtitle = "Dùng để thử luồng máy quét trước khi nối phần cứng thật.",
        onBack = onBack,
    ) {
        SectionCard(title = "Trạng thái") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(text = if (state.isActive) "Đang bật" else "Đang tắt")
                InfoPill(text = "Chế độ: ${state.selectedMode.label}")
            }

            Text(
                text = state.latestEvent,
                style = MaterialTheme.typography.bodyLarge,
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
                    Text("Bật máy quét")
                }

                OutlinedButton(
                    onClick = viewModel::stopScanner,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text("Tắt máy quét")
                }
            }
        }

        SectionCard(title = "Bộ thu hiện tại") {
            InfoPill(text = state.currentAdapters)
            Text(
                text = "Mã gần nhất: ${state.lastRawScan}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Loại nhận diện: ${state.lastParsedType}",
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

        SectionCard(title = "Phản hồi") {
            OutlinedButton(
                onClick = viewModel::testFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Thử âm báo + rung")
            }
        }

        SectionCard(title = "Giả lập mã quét") {
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

        SectionCard(title = "Cấu hình broadcast (PDA)") {
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
                label = { Text("Action") },
                supportingText = { Text("Ví dụ: com.xcloud.SCAN") },
            )
            OutlinedTextField(
                value = state.broadcastDataKey,
                onValueChange = viewModel::updateBroadcastDataKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Extra key chứa mã") },
            )
            OutlinedTextField(
                value = state.broadcastSymbologyKey,
                onValueChange = viewModel::updateBroadcastSymbologyKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Extra key loại mã (tuỳ chọn)") },
            )
            Button(
                onClick = viewModel::saveBroadcastConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Lưu cấu hình")
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
