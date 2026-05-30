package vn.delfi.xcloudwms.core.scanner

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import vn.delfi.xcloudwms.core.logging.SafeLogger

enum class ScanSource(val label: String) {
    SDK("SDK"),
    BROADCAST("Broadcast"),
    KEYBOARD_WEDGE("Phím quét"),
    CAMERA("Camera"),
    MANUAL("Thủ công"),
}

enum class ScannerMode(val label: String) {
    GENERIC("Tổng quát"),
    LOCATION("Vị trí"),
    PRODUCT("Sản phẩm"),
    LOT("Lô"),
    SERIAL("Serial"),
    DOCUMENT("Chứng từ"),
}

sealed interface ScanEvent {
    data class Success(
        val raw: String,
        val symbology: String? = null,
        val source: ScanSource,
        val timestamp: Long,
    ) : ScanEvent

    data class Error(
        val message: String,
        val source: ScanSource,
    ) : ScanEvent
}

interface ScannerManager {
    val scanEvents: Flow<ScanEvent>

    fun start()

    fun stop()

    fun setMode(mode: ScannerMode)

    fun submitManualScan(raw: String)
}

class ManualScannerManager(
    private val logger: SafeLogger,
    application: Application,
) : ScannerManager {
    private val mutableScanEvents = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 16)
    private val vibrator: Vibrator? = application.getSystemService(Vibrator::class.java)

    private var isActive: Boolean = false
    private var scannerMode: ScannerMode = ScannerMode.GENERIC

    override val scanEvents: Flow<ScanEvent> = mutableScanEvents.asSharedFlow()

    override fun start() {
        isActive = true
        logger.info("ScannerManager", "Scanner placeholder started")
    }

    override fun stop() {
        isActive = false
        logger.info("ScannerManager", "Scanner placeholder stopped")
    }

    override fun setMode(mode: ScannerMode) {
        scannerMode = mode
        logger.debug("ScannerManager", "Scanner mode changed to ${mode.name}")
    }

    override fun submitManualScan(raw: String) {
        val normalized = raw.trim()
        if (!isActive) {
            emitError("Máy quét chưa được kích hoạt.")
            return
        }

        if (normalized.isBlank()) {
            emitError("Mã quét đang trống.")
            return
        }

        vibrate(durationMs = 45L)
        mutableScanEvents.tryEmit(
            ScanEvent.Success(
                raw = normalized,
                source = ScanSource.MANUAL,
                timestamp = System.currentTimeMillis(),
            ),
        )
        logger.debug(
            "ScannerManager",
            "Accepted manual scan mode=${scannerMode.name} raw=$normalized",
        )
    }

    private fun emitError(message: String) {
        vibrate(durationMs = 140L)
        mutableScanEvents.tryEmit(
            ScanEvent.Error(
                message = message,
                source = ScanSource.MANUAL,
            ),
        )
        logger.error("ScannerManager", message)
    }

    private fun vibrate(durationMs: Long) {
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) {
            return
        }
        activeVibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }
}
