package vn.delfi.xcloudwms.core.scanner

import android.view.KeyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Trạng thái runtime của scanner để màn hình hiển thị (adapter đang chạy, mã/loại gần nhất...).
 */
data class ScannerRuntimeState(
    val isActive: Boolean = false,
    val mode: ScannerMode = ScannerMode.GENERIC,
    val activeSources: List<ScanSource> = emptyList(),
    val lastRaw: String? = null,
    val lastType: BarcodeType? = null,
    val continuousSerial: Boolean = false,
    val broadcastConfig: BroadcastScannerConfig = BroadcastScannerConfig.EMPTY,
)

/**
 * Điểm vào duy nhất cho mọi barcode/QR. Feature screen chỉ collect [scanEvents] và gọi
 * start/stop theo lifecycle — không biết đang dùng wedge/broadcast/camera/SDK nào.
 */
interface ScannerManager {
    /** Luồng sự kiện quét đã chuẩn hoá + phân tích. */
    val scanEvents: Flow<ScanEvent>

    /** Trạng thái hiển thị cho UI. */
    val state: StateFlow<ScannerRuntimeState>

    /** Bật toàn bộ adapter và bắt đầu nhận quét. */
    fun start()

    /** Tắt toàn bộ adapter; sau khi gọi, key event/broadcast không còn được xử lý. */
    fun stop()

    /** Đổi ngữ cảnh quét hiện tại (gợi ý cho parser). */
    fun setMode(mode: ScannerMode)

    /** Nhập tay (debug) — đi qua cùng pipeline như quét thật. */
    fun submitManualScan(raw: String)

    /** Nhận chuỗi do ô nhập wedge trên PDA bắt được rồi đẩy qua pipeline quét thật. */
    fun submitCapturedScan(raw: String)

    /**
     * Chuyển tiếp key event từ Activity cho keyboard wedge adapter.
     * @return true nếu adapter đã "nuốt" event (không cho dispatch tiếp), false nếu để hệ thống xử lý.
     */
    fun onKeyEvent(event: KeyEvent): Boolean

    /** Bật/tắt chế độ quét serial liên tục (bỏ chặn trùng). */
    fun setContinuousSerial(enabled: Boolean)

    /** Cập nhật cấu hình broadcast (lưu prefs + đăng ký lại receiver nếu đang chạy). */
    fun setBroadcastConfig(config: BroadcastScannerConfig)

    /** Phát beep + rung để kiểm tra phần cứng phản hồi. */
    fun testFeedback()
}
