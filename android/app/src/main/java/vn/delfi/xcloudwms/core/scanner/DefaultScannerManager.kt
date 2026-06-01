package vn.delfi.xcloudwms.core.scanner

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.core.logging.SafeLogger
import vn.delfi.xcloudwms.core.scanner.adapter.BroadcastScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.CameraScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.KeyboardWedgeScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.ManualScannerAdapter
import vn.delfi.xcloudwms.core.scanner.adapter.ScannerAdapter

/**
 * Coordinator: gom mọi adapter về một pipeline thống nhất
 * (chuẩn hoá → chống trùng → phân tích → phản hồi → phát [ScanEvent]).
 *
 * Pipeline chạy trên một coroutine duy nhất (merge các flow) nên các biến trạng thái nội bộ
 * (lastNormalized, currentMode, debounce) không bị race giữa nhiều adapter.
 */
class DefaultScannerManager(
    private val manualAdapter: ManualScannerAdapter,
    private val keyboardWedgeAdapter: KeyboardWedgeScannerAdapter,
    private val broadcastAdapter: BroadcastScannerAdapter,
    private val cameraAdapter: CameraScannerAdapter,
    private val parser: BarcodeParser,
    private val feedback: FeedbackManager,
    private val logger: SafeLogger,
    initialBroadcastConfig: BroadcastScannerConfig,
    private val onBroadcastConfigChanged: (BroadcastScannerConfig) -> Unit,
) : ScannerManager {

    // Ưu tiên broadcast → wedge → camera → manual (doc 03 §2).
    private val adapters: List<ScannerAdapter> =
        listOf(broadcastAdapter, keyboardWedgeAdapter, cameraAdapter, manualAdapter)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null

    private val mutableScanEvents = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 16)
    override val scanEvents: Flow<ScanEvent> = mutableScanEvents.asSharedFlow()

    private val mutableState = MutableStateFlow(
        ScannerRuntimeState(broadcastConfig = initialBroadcastConfig),
    )
    override val state: StateFlow<ScannerRuntimeState> = mutableState.asStateFlow()

    private var active: Boolean = false
    private var currentMode: ScannerMode = ScannerMode.GENERIC
    private var debounceConfig: ScanDebounceConfig = ScanDebounceConfig()
    private val debouncer = ScanDebouncer()

    override fun start() {
        if (active) {
            return
        }
        active = true
        adapters.forEach { it.start() }

        collectorJob = scope.launch {
            merge(*adapters.map { it.rawScans }.toTypedArray()).collect { rawScan ->
                processRaw(rawScan)
            }
        }
        refreshActiveSources()
        mutableState.update { it.copy(isActive = true) }
        logger.info(TAG, "Scanner started, adapters=${activeSourceLabels()}")
    }

    override fun stop() {
        if (!active) {
            return
        }
        active = false
        collectorJob?.cancel()
        collectorJob = null
        adapters.forEach { it.stop() }
        debouncer.reset()
        mutableState.update { it.copy(isActive = false, activeSources = emptyList()) }
        logger.info(TAG, "Scanner stopped")
    }

    override fun setMode(mode: ScannerMode) {
        currentMode = mode
        mutableState.update { it.copy(mode = mode) }
        logger.debug(TAG, "Mode=${mode.name}")
    }

    override fun submitManualScan(raw: String) {
        if (!active) {
            emitError("Máy quét chưa được kích hoạt.", ScanSource.MANUAL)
            return
        }
        if (raw.trim().isBlank()) {
            emitError("Mã quét đang trống.", ScanSource.MANUAL)
            return
        }
        manualAdapter.submit(raw)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!active) {
            return false
        }
        return keyboardWedgeAdapter.onKeyEvent(event)
    }

    override fun setContinuousSerial(enabled: Boolean) {
        debounceConfig = debounceConfig.copy(continuousSerial = enabled)
        mutableState.update { it.copy(continuousSerial = enabled) }
        logger.debug(TAG, "Continuous serial=$enabled")
    }

    override fun setBroadcastConfig(config: BroadcastScannerConfig) {
        onBroadcastConfigChanged(config)
        mutableState.update { it.copy(broadcastConfig = config) }
        if (active) {
            // Đăng ký lại receiver với cấu hình mới.
            broadcastAdapter.stop()
            broadcastAdapter.start()
            refreshActiveSources()
        }
        logger.info(TAG, "Broadcast config updated action=${config.action} enabled=${config.enabled}")
    }

    override fun testFeedback() {
        feedback.test()
    }

    private fun processRaw(rawScan: RawScan) {
        val normalized = parser.normalize(rawScan.raw)
        if (normalized.isBlank()) {
            emitError("Mã quét đang trống.", rawScan.source)
            return
        }

        val now = System.currentTimeMillis()
        if (!debouncer.shouldAccept(normalized, now, debounceConfig)) {
            feedback.duplicate()
            logger.debug(TAG, "Bỏ qua mã trùng: $normalized")
            return
        }

        val parsed = parser.parse(rawScan.raw, currentMode)
        feedback.success()
        mutableScanEvents.tryEmit(
            ScanEvent.Success(
                raw = rawScan.raw,
                parsed = parsed,
                source = rawScan.source,
                timestamp = now,
                symbology = rawScan.symbology,
            ),
        )
        mutableState.update {
            it.copy(lastRaw = parsed.normalized, lastType = parsed.type)
        }
        logger.debug(TAG, "Scan ${rawScan.source.name} type=${parsed.type.name} raw=${parsed.normalized}")
    }

    private fun emitError(message: String, source: ScanSource) {
        feedback.error()
        mutableScanEvents.tryEmit(ScanEvent.Error(message = message, source = source))
        logger.error(TAG, message)
    }

    private fun refreshActiveSources() {
        mutableState.update {
            it.copy(activeSources = adapters.filter { adapter -> adapter.isActive }.map { adapter -> adapter.source })
        }
    }

    private fun activeSourceLabels(): String =
        adapters.filter { it.isActive }.joinToString { it.source.name }

    private companion object {
        const val TAG = "ScannerManager"
    }
}
