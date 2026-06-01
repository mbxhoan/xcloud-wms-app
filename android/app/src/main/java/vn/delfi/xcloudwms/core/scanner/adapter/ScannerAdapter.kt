package vn.delfi.xcloudwms.core.scanner.adapter

import kotlinx.coroutines.flow.Flow
import vn.delfi.xcloudwms.core.scanner.RawScan
import vn.delfi.xcloudwms.core.scanner.ScanSource

/**
 * Một nguồn quét cụ thể (wedge/broadcast/camera/manual). Mọi adapter chỉ phát [RawScan];
 * việc chuẩn hoá, chống trùng, phân tích và phản hồi do [vn.delfi.xcloudwms.core.scanner.ScannerManager] lo.
 */
interface ScannerAdapter {
    val source: ScanSource

    val rawScans: Flow<RawScan>

    val isActive: Boolean

    fun start()

    fun stop()
}
