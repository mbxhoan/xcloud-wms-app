package vn.delfi.xcloudwms.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Modifier cho ô quét mã: tự động focus khi vào màn và (tuỳ chọn) tự lấy lại focus khi bị mất, giúp
 * PDA luôn sẵn sàng quét mà không cần chạm vào ô nhập hay màn hình.
 *
 * Bàn phím ảo được kiểm soát riêng ở cấp window bằng cài đặt "Ẩn bàn phím ảo" (xem [XcloudWmsApp]),
 * nên việc giữ focus ở đây không tự bật bàn phím ảo khi đang chặn.
 *
 * @param enabled bật/tắt toàn bộ hành vi auto focus.
 * @param keepFocused khi true sẽ tự lấy lại focus nếu ô bị mất focus. Chỉ nên bật cho màn chỉ có một
 *   ô nhập (list/lookup/scanner test); với màn có thêm dropdown/ô số lượng nên để false để không
 *   tranh focus với các input khác.
 */
@Composable
fun Modifier.alwaysFocusedScanInput(
    enabled: Boolean = true,
    keepFocused: Boolean = true,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            if (enabled && keepFocused && !focusState.isFocused) {
                runCatching { focusRequester.requestFocus() }
            }
        }
}
