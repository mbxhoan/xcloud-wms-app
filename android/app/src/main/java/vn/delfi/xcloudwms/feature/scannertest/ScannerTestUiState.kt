package vn.delfi.xcloudwms.feature.scannertest

import vn.delfi.xcloudwms.core.scanner.ScannerMode

data class ScannerTestUiState(
    val isActive: Boolean = false,
    val selectedMode: ScannerMode = ScannerMode.GENERIC,
    val manualCode: String = "",
    val latestEvent: String = "Chưa có mã quét",
    val eventHistory: List<String> = emptyList(),
)
