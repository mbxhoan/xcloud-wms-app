package vn.delfi.xcloudwms.core.scanner.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import vn.delfi.xcloudwms.core.scanner.RawScan
import vn.delfi.xcloudwms.core.scanner.ScanSource

/**
 * Nhập tay (debug). Đi qua cùng pipeline với quét thật để kiểm thử nghiệp vụ khi chưa có phần cứng.
 */
class ManualScannerAdapter : ScannerAdapter {

    private val mutableRawScans = MutableSharedFlow<RawScan>(extraBufferCapacity = 16)

    override val source: ScanSource = ScanSource.MANUAL
    override val rawScans: Flow<RawScan> = mutableRawScans.asSharedFlow()

    private var active: Boolean = false
    override val isActive: Boolean get() = active

    override fun start() {
        active = true
    }

    override fun stop() {
        active = false
    }

    /** Phát một mã nhập tay. Trả false nếu chưa active hoặc buffer đầy. */
    fun submit(raw: String): Boolean {
        if (!active) {
            return false
        }
        return mutableRawScans.tryEmit(RawScan(raw = raw, source = ScanSource.MANUAL))
    }
}
