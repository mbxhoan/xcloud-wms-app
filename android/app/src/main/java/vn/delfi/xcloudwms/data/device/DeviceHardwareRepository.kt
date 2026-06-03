package vn.delfi.xcloudwms.data.device

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceHardwareField(
    val label: String,
    val value: String,
)

data class DeviceHardwareSection(
    val title: String,
    val fields: List<DeviceHardwareField>,
)

data class DeviceHardwareSnapshot(
    val capturedAtLabel: String,
    val missingPermissionLabels: List<String>,
    val sections: List<DeviceHardwareSection>,
)

interface DeviceHardwareRepository {
    fun loadSnapshot(): DeviceHardwareSnapshot
}

class DefaultDeviceHardwareRepository(
    private val context: Context,
) : DeviceHardwareRepository {

    override fun loadSnapshot(): DeviceHardwareSnapshot {
        return DeviceHardwareSnapshot(
            capturedAtLabel = "Cập nhật lúc ${dateFormat.format(Date())}",
            missingPermissionLabels = buildMissingPermissions(),
            sections = listOf(
                buildIdentitySection(),
                buildOsSection(),
                buildTelephonySection(),
                buildNetworkSection(),
                buildBluetoothSection(),
                buildBatteryAndStorageSection(),
                buildAppSection(),
            ),
        )
    }

    private fun buildIdentitySection(): DeviceHardwareSection {
        return DeviceHardwareSection(
            title = "Nhận dạng thiết bị",
            fields = listOf(
                field("Tên thiết bị", readDeviceName()),
                field("Hãng sản xuất", meaningfulOrFallback(Build.MANUFACTURER, "Không xác định")),
                field("Mã brand", meaningfulOrFallback(Build.BRAND, "Không xác định")),
                field("Model máy", meaningfulOrFallback(Build.MODEL, "Không xác định")),
                field("Mã máy", meaningfulOrFallback(Build.DEVICE, "Không xác định")),
                field("Mã product", meaningfulOrFallback(Build.PRODUCT, "Không xác định")),
                field("Bo mạch", meaningfulOrFallback(Build.BOARD, "Không xác định")),
                field("Mã hardware", meaningfulOrFallback(Build.HARDWARE, "Không xác định")),
                field("Bootloader", meaningfulOrFallback(Build.BOOTLOADER, "Không xác định")),
                field("Android ID", readAndroidId()),
                field("Serial phần cứng", readHardwareSerial()),
            ),
        )
    }

    private fun buildOsSection(): DeviceHardwareSection {
        return DeviceHardwareSection(
            title = "Hệ điều hành",
            fields = listOf(
                field(
                    "Phiên bản Android",
                    "${meaningfulOrFallback(Build.VERSION.RELEASE, "Không xác định")} • API ${Build.VERSION.SDK_INT}",
                ),
                field(
                    "Bản vá bảo mật",
                    meaningfulOrFallback(Build.VERSION.SECURITY_PATCH, "Không có"),
                ),
                field("Mã build", meaningfulOrFallback(Build.ID, "Không xác định")),
                field("Fingerprint hệ thống", meaningfulOrFallback(Build.FINGERPRINT, "Không xác định")),
                field("Kernel hệ điều hành", meaningfulOrFallback(System.getProperty("os.version"), "Không xác định")),
                field("Radio/Baseband", meaningfulOrFallback(Build.getRadioVersion(), "Không có")),
                field(
                    "Kiến trúc hỗ trợ",
                    Build.SUPPORTED_ABIS
                        .mapNotNull { abi -> abi.takeIf(::isMeaningful) }
                        .joinToString()
                        .ifBlank { "Không xác định" },
                ),
            ),
        )
    }

    private fun buildTelephonySection(): DeviceHardwareSection {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (!hasTelephony) {
            return DeviceHardwareSection(
                title = "Điện thoại và SIM",
                fields = listOf(
                    field("Hỗ trợ modem/SIM", "Thiết bị không có phần cứng điện thoại/SIM"),
                ),
            )
        }

        val manager = context.getSystemService(TelephonyManager::class.java)
        return DeviceHardwareSection(
            title = "Điện thoại và SIM",
            fields = listOf(
                field("Số khe SIM", manager?.phoneCount?.toString() ?: "Không xác định"),
                field("Trạng thái SIM", simStateLabel(manager?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN)),
                field("Nhà mạng SIM", meaningfulOrFallback(manager?.simOperatorName, "Chưa có SIM")),
                field("Nhà mạng hiện tại", meaningfulOrFallback(manager?.networkOperatorName, "Chưa đăng ký mạng")),
                field("Số điện thoại", readPhoneNumber(manager)),
                field("IMEI / MEID", readImeiOrMeid(manager)),
            ),
        )
    }

    private fun buildNetworkSection(): DeviceHardwareSection {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        val interfaces = readActiveInterfaces()

        val ipv4Values = interfaces.flatMap { networkInterface ->
            networkInterface.addresses
                .filterNot { address -> address is Inet6Address }
                .map { address -> "${networkInterface.name}: ${cleanAddress(address)}" }
        }
        val ipv6Values = interfaces.flatMap { networkInterface ->
            networkInterface.addresses
                .filterIsInstance<Inet6Address>()
                .map { address -> "${networkInterface.name}: ${cleanAddress(address)}" }
        }
        val macValues = interfaces.mapNotNull { networkInterface ->
            networkInterface.macAddress?.let { mac ->
                "${networkInterface.name}: $mac"
            }
        }

        return DeviceHardwareSection(
            title = "Mạng và địa chỉ",
            fields = listOf(
                field("Kết nối chính", activeTransportLabel(capabilities)),
                field("IPv4", joinLines(ipv4Values, "Không có địa chỉ IPv4 khả dụng")),
                field("IPv6", joinLines(ipv6Values, "Không có địa chỉ IPv6 khả dụng")),
                field("MAC theo giao diện", joinLines(macValues, "Android không cho đọc hoặc giao diện chưa có MAC khả dụng")),
            ),
        )
    }

    private fun buildBluetoothSection(): DeviceHardwareSection {
        val hasBluetooth = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        if (!hasBluetooth) {
            return DeviceHardwareSection(
                title = "Bluetooth",
                fields = listOf(
                    field("Trạng thái", "Thiết bị không hỗ trợ Bluetooth"),
                ),
            )
        }

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return DeviceHardwareSection(
            title = "Bluetooth",
            fields = listOf(
                field("Trạng thái", bluetoothStateLabel(adapter)),
                field("Tên Bluetooth", readBluetoothName(adapter)),
                field("MAC Bluetooth", readBluetoothAddress(adapter)),
            ),
        )
    }

    private fun buildBatteryAndStorageSection(): DeviceHardwareSection {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            activityManager?.getMemoryInfo(info)
        }
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val displayMetrics = context.resources.displayMetrics

        return DeviceHardwareSection(
            title = "Nguồn, màn hình và bộ nhớ",
            fields = listOf(
                field("Mức pin", readBatteryPercent(batteryIntent)),
                field("Trạng thái sạc", readBatteryStatus(batteryIntent)),
                field("RAM tổng", formatBytes(memoryInfo.totalMem)),
                field("RAM khả dụng", formatBytes(memoryInfo.availMem)),
                field("Bộ nhớ trong tổng", formatBytes(statFs.totalBytes)),
                field("Bộ nhớ trong còn trống", formatBytes(statFs.availableBytes)),
                field("Độ phân giải", "${displayMetrics.widthPixels} × ${displayMetrics.heightPixels} px"),
                field(
                    "Mật độ màn hình",
                    "${displayMetrics.densityDpi} dpi • scale ${decimalFormat.format(displayMetrics.density)}x",
                ),
                field(
                    "Cỡ chữ hệ thống",
                    "${decimalFormat.format(context.resources.configuration.fontScale)}x",
                ),
            ),
        )
    }

    private fun buildAppSection(): DeviceHardwareSection {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }

        return DeviceHardwareSection(
            title = "Ứng dụng hiện tại",
            fields = listOf(
                field("Package app", context.packageName),
                field("Phiên bản hiển thị", meaningfulOrFallback(packageInfo.versionName, "Không xác định")),
                field("Mã phiên bản", versionCode),
                field("Kênh app", meaningfulOrFallback(vn.delfi.xcloudwms.BuildConfig.APP_CHANNEL, "Không xác định")),
                field("Môi trường build", meaningfulOrFallback(vn.delfi.xcloudwms.BuildConfig.BUILD_ENV, "Không xác định")),
            ),
        )
    }

    private fun buildMissingPermissions(): List<String> {
        return buildList {
            if (
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                (!hasPhoneStatePermission() || !hasPhoneNumbersPermission())
            ) {
                add("Điện thoại")
            }
            if (
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
                !hasBluetoothPermission()
            ) {
                add("Bluetooth")
            }
        }
    }

    private fun readDeviceName(): String {
        val fromSystem = Settings.Global.getString(context.contentResolver, "device_name")
        if (isMeaningful(fromSystem)) {
            return fromSystem!!.trim()
        }
        return listOfNotNull(
            Build.MANUFACTURER.takeIf(::isMeaningful),
            Build.MODEL.takeIf(::isMeaningful),
        ).joinToString(" ").ifBlank { "Không xác định" }
    }

    private fun readAndroidId(): String {
        return meaningfulOrFallback(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
            "Không có",
        )
    }

    private fun readHardwareSerial(): String {
        if (!hasPhoneStatePermission()) {
            return "Cần cấp quyền điện thoại"
        }
        return try {
            meaningfulOrFallback(Build.getSerial(), "Android chặn hoặc thiết bị không cung cấp")
        } catch (_: SecurityException) {
            "Android chặn hoặc thiếu quyền"
        }
    }

    @Suppress("DEPRECATION")
    private fun readPhoneNumber(manager: TelephonyManager?): String {
        if (manager == null) {
            return "Không có dịch vụ điện thoại"
        }
        if (!hasPhoneStatePermission() && !hasPhoneNumbersPermission()) {
            return "Cần cấp quyền điện thoại"
        }
        return try {
            meaningfulOrFallback(manager.line1Number, "Không có hoặc nhà mạng không cấp")
        } catch (_: SecurityException) {
            "Android chặn hoặc thiếu quyền"
        }
    }

    private fun readImeiOrMeid(manager: TelephonyManager?): String {
        if (manager == null) {
            return "Không có dịch vụ điện thoại"
        }
        if (!hasPhoneStatePermission()) {
            return "Cần cấp quyền điện thoại"
        }
        return try {
            val values = linkedSetOf<String>()
            val slots = manager.phoneCount.coerceAtLeast(1)
            repeat(slots) { slotIndex ->
                manager.getImei(slotIndex)
                    ?.takeIf(::isMeaningful)
                    ?.let { imei -> values.add("SIM ${slotIndex + 1}: $imei") }
                manager.getMeid(slotIndex)
                    ?.takeIf(::isMeaningful)
                    ?.let { meid -> values.add("MEID ${slotIndex + 1}: $meid") }
            }
            if (values.isEmpty()) {
                manager.imei
                    ?.takeIf(::isMeaningful)
                    ?.let { imei -> values.add(imei) }
            }
            joinLines(values.toList(), "Android chặn hoặc thiết bị không trả về IMEI/MEID")
        } catch (_: SecurityException) {
            "Android chặn hoặc thiếu quyền"
        }
    }

    private fun readBluetoothName(adapter: BluetoothAdapter?): String {
        if (adapter == null) {
            return "Không có adapter Bluetooth"
        }
        if (!hasBluetoothPermission()) {
            return "Cần cấp quyền Bluetooth"
        }
        return try {
            meaningfulOrFallback(adapter.name, "Chưa đặt tên Bluetooth")
        } catch (_: SecurityException) {
            "Android chặn hoặc thiếu quyền"
        }
    }

    private fun readBluetoothAddress(adapter: BluetoothAdapter?): String {
        if (adapter == null) {
            return "Không có adapter Bluetooth"
        }
        if (!hasBluetoothPermission()) {
            return "Cần cấp quyền Bluetooth"
        }
        return try {
            meaningfulOrFallback(
                normalizeMac(adapter.address),
                "Android chặn hoặc adapter không trả về địa chỉ",
            )
        } catch (_: SecurityException) {
            "Android chặn hoặc thiếu quyền"
        }
    }

    private fun readBatteryPercent(intent: Intent?): String {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) {
            return "Không đọc được"
        }
        return "${(level * 100f / scale).toInt()}%"
    }

    private fun readBatteryStatus(intent: Intent?): String {
        return when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Đang sạc"
            BatteryManager.BATTERY_STATUS_FULL -> "Đã sạc đầy"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Đang dùng pin"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Đang cắm nguồn nhưng không sạc"
            else -> "Không xác định"
        }
    }

    private fun readActiveInterfaces(): List<InterfaceSnapshot> {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())

        return interfaces.mapNotNull { networkInterface ->
            val isUp = runCatching { networkInterface.isUp }.getOrDefault(false)
            val isLoopback = runCatching { networkInterface.isLoopback }.getOrDefault(false)
            if (!isUp || isLoopback) {
                return@mapNotNull null
            }

            val addresses = runCatching {
                networkInterface.inetAddresses.toList()
                    .filterNot(InetAddress::isLoopbackAddress)
            }.getOrDefault(emptyList())

            if (addresses.isEmpty()) {
                return@mapNotNull null
            }

            InterfaceSnapshot(
                name = networkInterface.name ?: "unknown",
                addresses = addresses,
                macAddress = normalizeMac(
                    runCatching { networkInterface.hardwareAddress }
                        .getOrNull()
                        ?.joinToString(":") { byte -> "%02X".format(byte) },
                ),
            )
        }
    }

    private fun activeTransportLabel(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) {
            return "Không có kết nối hoạt động"
        }
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Di động"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Kết nối khác"
        }
    }

    private fun bluetoothStateLabel(adapter: BluetoothAdapter?): String {
        if (adapter == null) {
            return "Không có adapter Bluetooth"
        }
        return when (adapter.state) {
            BluetoothAdapter.STATE_ON -> "Đang bật"
            BluetoothAdapter.STATE_TURNING_ON -> "Đang bật dần"
            BluetoothAdapter.STATE_TURNING_OFF -> "Đang tắt dần"
            BluetoothAdapter.STATE_OFF -> "Đang tắt"
            else -> "Không xác định"
        }
    }

    private fun simStateLabel(simState: Int): String {
        return when (simState) {
            TelephonyManager.SIM_STATE_READY -> "Sẵn sàng"
            TelephonyManager.SIM_STATE_ABSENT -> "Không có SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Đang chờ mã PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Đang chờ mã PUK"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bị khoá mạng"
            else -> "Không xác định"
        }
    }

    private fun hasPhoneStatePermission(): Boolean {
        return hasPermission(Manifest.permission.READ_PHONE_STATE)
    }

    private fun hasPhoneNumbersPermission(): Boolean {
        return hasPermission(Manifest.permission.READ_PHONE_NUMBERS)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun field(label: String, value: String): DeviceHardwareField = DeviceHardwareField(
        label = label,
        value = value,
    )

    private fun meaningfulOrFallback(value: String?, fallback: String): String {
        return value?.trim()?.takeIf(::isMeaningful) ?: fallback
    }

    private fun isMeaningful(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }
        val normalized = value.trim()
        return normalized != "unknown" &&
            normalized != Build.UNKNOWN &&
            normalized != "N/A" &&
            normalized != "null"
    }

    private fun joinLines(values: List<String>, fallback: String): String {
        return values.filter(String::isNotBlank).joinToString("\n").ifBlank { fallback }
    }

    private fun cleanAddress(address: InetAddress): String {
        return address.hostAddress?.substringBefore('%').orEmpty().ifBlank { "Không xác định" }
    }

    private fun normalizeMac(value: String?): String? {
        val normalized = value?.trim()?.uppercase(Locale.US)
        return when {
            normalized.isNullOrBlank() -> null
            normalized == "02:00:00:00:00:00" -> null
            else -> normalized
        }
    }

    private fun formatBytes(value: Long): String {
        if (value <= 0L) {
            return "0 B"
        }
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex += 1
        }
        return "${decimalFormat.format(size)} ${units[unitIndex]}"
    }

    private data class InterfaceSnapshot(
        val name: String,
        val addresses: List<InetAddress>,
        val macAddress: String?,
    )

    private companion object {
        val decimalFormat = DecimalFormat("#,##0.##")
        val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale("vi", "VN"))
    }
}
