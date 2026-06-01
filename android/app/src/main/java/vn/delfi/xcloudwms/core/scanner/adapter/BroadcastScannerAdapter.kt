package vn.delfi.xcloudwms.core.scanner.adapter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.BroadcastScannerConfig
import vn.delfi.xcloudwms.core.scanner.RawScan
import vn.delfi.xcloudwms.core.scanner.ScanSource

/**
 * Nhận barcode do app scanner của PDA phát ra dưới dạng broadcast intent (kiểu DataWedge).
 *
 * KHÔNG hard-code vendor: action / extra key lấy từ [configProvider] (người dùng cấu hình trong
 * màn Kiểm tra máy quét). Đăng ký receiver với cờ EXPORTED vì broadcast đến từ app khác (API 34+).
 */
class BroadcastScannerAdapter(
    private val context: Context,
    private val logger: SafeLogger,
    private val configProvider: () -> BroadcastScannerConfig,
) : ScannerAdapter {

    private val mutableRawScans = MutableSharedFlow<RawScan>(extraBufferCapacity = 16)

    override val source: ScanSource = ScanSource.BROADCAST
    override val rawScans: Flow<RawScan> = mutableRawScans.asSharedFlow()

    private var active: Boolean = false
    override val isActive: Boolean get() = active

    private var receiver: BroadcastReceiver? = null

    override fun start() {
        active = true
        registerReceiver()
    }

    override fun stop() {
        active = false
        unregisterReceiver()
    }

    private fun registerReceiver() {
        unregisterReceiver()

        val config = configProvider()
        if (!config.isUsable) {
            logger.debug(TAG, "Broadcast adapter chưa cấu hình đủ → bỏ qua đăng ký.")
            return
        }

        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                handleIntent(intent)
            }
        }

        runCatching {
            ContextCompat.registerReceiver(
                context,
                newReceiver,
                IntentFilter(config.action),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onSuccess {
            receiver = newReceiver
            logger.info(TAG, "Đã đăng ký broadcast action=${config.action}")
        }.onFailure {
            logger.error(TAG, "Đăng ký broadcast thất bại: ${it.message}")
        }
    }

    private fun unregisterReceiver() {
        val current = receiver ?: return
        runCatching { context.unregisterReceiver(current) }
            .onFailure { logger.error(TAG, "Huỷ đăng ký broadcast lỗi: ${it.message}") }
        receiver = null
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val config = configProvider()
        if (!config.isUsable) {
            return
        }

        val data = intent.getStringExtra(config.dataExtraKey)
            ?: intent.getCharSequenceExtra(config.dataExtraKey)?.toString()
        if (data.isNullOrBlank()) {
            logger.debug(TAG, "Broadcast không có dữ liệu ở key=${config.dataExtraKey}")
            return
        }

        val symbology = config.symbologyExtraKey
            .takeIf { it.isNotBlank() }
            ?.let { intent.getStringExtra(it) }

        mutableRawScans.tryEmit(
            RawScan(raw = data, source = ScanSource.BROADCAST, symbology = symbology),
        )
    }

    private companion object {
        const val TAG = "BroadcastScanner"
    }
}
