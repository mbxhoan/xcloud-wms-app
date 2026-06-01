package vn.delfi.xcloudwms.core.scanner.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.RawScan
import vn.delfi.xcloudwms.core.scanner.ScanSource

/**
 * Placeholder cho camera fallback. Repo chưa có CameraX/ML Kit nên adapter này chỉ giữ chỗ:
 * không yêu cầu quyền CAMERA, không phát [RawScan]. Khi thêm dependency, hiện thực preview +
 * analyzer + torch ở đây (doc 03 §7) mà không cần đổi [vn.delfi.xcloudwms.core.scanner.ScannerManager].
 *
 * @param enabled lấy từ build flag `ENABLE_CAMERA_SCAN_FALLBACK`.
 */
class CameraScannerAdapter(
    private val enabled: Boolean,
    private val logger: SafeLogger,
) : ScannerAdapter {

    private val mutableRawScans = MutableSharedFlow<RawScan>(extraBufferCapacity = 16)

    override val source: ScanSource = ScanSource.CAMERA
    override val rawScans: Flow<RawScan> = mutableRawScans.asSharedFlow()

    private var active: Boolean = false

    /** Chỉ coi là active khi build bật cờ camera fallback (hiện chưa hiện thực thật). */
    override val isActive: Boolean get() = active && enabled && AVAILABLE

    override fun start() {
        active = true
        if (enabled && !AVAILABLE) {
            logger.info(TAG, "Camera fallback bật theo cấu hình nhưng chưa hiện thực (placeholder).")
        }
    }

    override fun stop() {
        active = false
    }

    private companion object {
        const val TAG = "CameraScanner"

        /** Đặt true khi đã tích hợp CameraX + ML Kit. */
        const val AVAILABLE = false
    }
}
