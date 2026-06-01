package vn.delfi.xcloudwms.core.scanner

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import vn.delfi.xcloudwms.core.logging.SafeLogger

/**
 * Phản hồi beep + rung khi quét. Bảng phản hồi theo doc 03 §9.
 */
interface FeedbackManager {
    /** Scan đúng bước. */
    fun success()

    /** Scan lỗi / sai type. */
    fun error()

    /** Quét trùng (đã chặn debounce). */
    fun duplicate()

    /** Kiểm tra phần cứng phản hồi (nút thử trong màn Kiểm tra máy quét). */
    fun test()
}

class DefaultFeedbackManager(
    application: Application,
    private val logger: SafeLogger,
) : FeedbackManager {

    private val vibrator: Vibrator? = application.getSystemService(Vibrator::class.java)

    // ToneGenerator có thể ném lỗi trên một số thiết bị → khởi tạo lười và bọc try/catch.
    private val toneGenerator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }
            .onFailure { logger.error(TAG, "Không khởi tạo được ToneGenerator: ${it.message}") }
            .getOrNull()
    }

    override fun success() {
        beep(ToneGenerator.TONE_PROP_BEEP, durationMs = 120)
        vibrate(40L)
    }

    override fun error() {
        beep(ToneGenerator.TONE_SUP_ERROR, durationMs = 250)
        vibrate(120L)
    }

    override fun duplicate() {
        beep(ToneGenerator.TONE_PROP_ACK, durationMs = 90)
        vibrate(40L)
    }

    override fun test() {
        success()
    }

    private fun beep(toneType: Int, durationMs: Int) {
        val generator = toneGenerator ?: return
        runCatching { generator.startTone(toneType, durationMs) }
            .onFailure { logger.error(TAG, "Lỗi phát beep: ${it.message}") }
    }

    private fun vibrate(durationMs: Long) {
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) {
            return
        }
        runCatching {
            activeVibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }.onFailure { logger.error(TAG, "Lỗi rung: ${it.message}") }
    }

    private companion object {
        const val TAG = "FeedbackManager"
        const val TONE_VOLUME = 80
    }
}
