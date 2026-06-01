package vn.delfi.xcloudwms.core.scanner.adapter

import android.view.KeyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import vn.delfi.xcloudwms.core.scanner.RawScan
import vn.delfi.xcloudwms.core.scanner.ScanSource

/**
 * Keyboard-wedge adapter: nhiều PDA bắn barcode như bàn phím (ký tự nhanh + ENTER/TAB).
 *
 * Phân biệt quét máy với gõ tay bằng độ trễ giữa các phím (doc 03 §4, PWA `keyboardMaxInterKeyDelayMs`):
 * - Phím đến nhanh (gap <= [interKeyDelayMs]) → coi là chuỗi máy → gom buffer + "nuốt" event.
 * - Ký tự ĐẦU của một chuỗi luôn có gap lớn → cho lọt xuống field để không nuốt mất ký tự đầu khi gõ tay.
 * - ENTER/TAB kết thúc: nếu buffer là chuỗi máy và đủ dài → phát [RawScan], nuốt ENTER.
 *   Nếu không (gõ tay chậm) → để ENTER hoạt động bình thường.
 *
 * Giới hạn đã biết: ký tự đầu của một lần quét nhanh vào ô đang focus có thể để lại 1 ký tự thừa
 * trong ô đó; mã phát ra vẫn đầy đủ và đúng. PDA thật thường quét vào màn không có ô nhập focus.
 */
class KeyboardWedgeScannerAdapter(
    private val interKeyDelayMs: Long = DEFAULT_INTER_KEY_DELAY_MS,
    private val minLength: Int = DEFAULT_MIN_LENGTH,
    private val dedupeWindowMs: Long = DEFAULT_DEDUPE_WINDOW_MS,
) : ScannerAdapter {

    private val mutableRawScans = MutableSharedFlow<RawScan>(extraBufferCapacity = 16)

    override val source: ScanSource = ScanSource.KEYBOARD_WEDGE
    override val rawScans: Flow<RawScan> = mutableRawScans.asSharedFlow()

    private var active: Boolean = false
    override val isActive: Boolean get() = active

    private val buffer = StringBuilder()
    private var burstFast: Boolean = false
    private var lastDownTime: Long = 0L

    private val consumedDownKeyCodes = mutableSetOf<Int>()

    private var lastEmitText: String? = null
    private var lastEmitTime: Long = 0L

    override fun start() {
        active = true
        resetBuffer()
    }

    override fun stop() {
        active = false
        resetBuffer()
        consumedDownKeyCodes.clear()
    }

    /** Xử lý key event do Activity chuyển tới. Trả true nếu đã "nuốt" event. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!active) {
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            // Nuốt nốt ACTION_UP của các phím đã nuốt ở ACTION_DOWN để không lọt xuống field.
            return consumedDownKeyCodes.remove(event.keyCode)
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_TAB,
            -> handleTerminator(event)

            else -> handleChar(event)
        }
    }

    private fun handleTerminator(event: KeyEvent): Boolean {
        val candidate = buffer.toString()
        val qualifies = burstFast && candidate.length >= minLength
        resetBuffer()

        if (!qualifies) {
            // Gõ tay / chuỗi không hợp lệ → để ENTER hoạt động bình thường.
            return false
        }

        if (shouldEmit(candidate, event.eventTime)) {
            mutableRawScans.tryEmit(RawScan(raw = candidate, source = ScanSource.KEYBOARD_WEDGE))
        }
        consumedDownKeyCodes.add(event.keyCode)
        return true
    }

    private fun handleChar(event: KeyEvent): Boolean {
        val unicodeChar = event.unicodeChar
        if (unicodeChar == 0) {
            // Phím điều khiển (shift, alt...) → bỏ qua, không nuốt.
            return false
        }

        val now = event.eventTime
        val gap = if (lastDownTime == 0L) Long.MAX_VALUE else now - lastDownTime
        lastDownTime = now

        return if (gap <= interKeyDelayMs) {
            // Chuỗi máy: gom + nuốt.
            burstFast = true
            buffer.append(unicodeChar.toChar())
            consumedDownKeyCodes.add(event.keyCode)
            true
        } else {
            // Ký tự rời/đầu chuỗi: khởi tạo lại buffer, cho lọt xuống field.
            buffer.setLength(0)
            buffer.append(unicodeChar.toChar())
            burstFast = false
            false
        }
    }

    private fun shouldEmit(text: String, now: Long): Boolean {
        if (text == lastEmitText && now - lastEmitTime < dedupeWindowMs) {
            return false
        }
        lastEmitText = text
        lastEmitTime = now
        return true
    }

    private fun resetBuffer() {
        buffer.setLength(0)
        burstFast = false
        lastDownTime = 0L
    }

    private companion object {
        const val DEFAULT_INTER_KEY_DELAY_MS = 110L
        const val DEFAULT_MIN_LENGTH = 3
        const val DEFAULT_DEDUPE_WINDOW_MS = 100L
    }
}
