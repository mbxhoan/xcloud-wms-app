package vn.delfi.xcloudwms.feature.login

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun ConnectionQrCameraScanButton(
    onDetected: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }
    var isLaunching by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            isLaunching = true
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    isLaunching = false
                    val rawValue = barcode.rawValue?.trim().orEmpty()
                    if (rawValue.isBlank()) {
                        onError("Không đọc được dữ liệu từ mã QR. Vui lòng thử lại.")
                    } else {
                        onDetected(rawValue)
                    }
                }
                .addOnCanceledListener {
                    isLaunching = false
                }
                .addOnFailureListener { throwable ->
                    isLaunching = false
                    val statusCode = (throwable as? com.google.android.gms.common.api.ApiException)?.statusCode
                    if (statusCode == CommonStatusCodes.CANCELED) {
                        return@addOnFailureListener
                    }
                    onError("Không thể mở máy quét camera trên thiết bị này. Hãy dùng cò scan hoặc nhập thủ công.")
                }
        },
        enabled = enabled && !isLaunching,
        modifier = modifier,
    ) {
        if (isLaunching) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Text("Quét bằng camera")
        }
    }
}
