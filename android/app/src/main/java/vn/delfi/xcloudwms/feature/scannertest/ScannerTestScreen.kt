package vn.delfi.xcloudwms.feature.scannertest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    XcloudScaffold(
        title = "Kiểm tra máy quét",
        subtitle = "Dùng để thử luồng máy quét trước khi nối phần cứng thật.",
        onBack = onBack,
    ) {
        SectionCard(title = "Trạng thái") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(
                    text = if (uiState.value.isActive) {
                        "Đang bật"
                    } else {
                        "Đang tắt"
                    },
                )
                InfoPill(text = "Chế độ: ${uiState.value.selectedMode.label}")
            }

            Text(
                text = uiState.value.latestEvent,
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

        SectionCard(title = "Chế độ quét") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScannerMode.entries.toList()) { mode ->
                    FilterChip(
                        selected = uiState.value.selectedMode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        SectionCard(title = "Giả lập mã quét") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.value.manualCode,
                    onValueChange = viewModel::updateManualCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã quét thử") },
                    supportingText = {
                        Text("Ví dụ: SKU-001, LOC-A01 hoặc SERIAL-0001")
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

        SectionCard(title = "Lịch sử gần nhất") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.value.eventHistory.ifEmpty { listOf("Chưa có sự kiện nào.") }.forEach { event ->
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
