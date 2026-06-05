package vn.delfi.xcloudwms.data.device

import android.annotation.SuppressLint
import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import vn.delfi.xcloudwms.BuildConfig
import vn.delfi.xcloudwms.core.storage.OfflineStore

@SuppressLint("HardwareIds", "MissingPermission")
internal class NativeDeviceProfileCollector(
    private val context: Context,
    private val offlineStore: OfflineStore,
) {
    fun collect(): DeviceRegistrationSnapshot {
        val manufacturer = normalizedValue(Build.MANUFACTURER, "Android")
        val brand = normalizedValue(Build.BRAND, manufacturer)
        val model = normalizedValue(Build.MODEL, "Unknown Model")
        val deviceName = buildDeviceName(manufacturer = manufacturer, model = model)
        val androidId = readAndroidId()
        val androidIdHash = androidId?.let(::sha256Hex)
        val vendorSerialHash: String? = null
        val fingerprint = sha256Hex(
            listOf(
                manufacturer,
                brand,
                model,
                androidIdHash.orEmpty(),
                vendorSerialHash.orEmpty(),
            ).joinToString("|"),
        )

        val deviceOsVersion = buildOsVersion()
        val displayMetrics = context.resources.displayMetrics
        val screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        val timezone = TimeZone.getDefault().id
        val language = context.resources.configuration.locales[0]?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()
        val hardwareConcurrency = Runtime.getRuntime().availableProcessors()
        val deviceMemoryBytes = totalMemoryBytes()
        val deviceMemoryGb = deviceMemoryBytes?.let(::bytesToGigabytes)
        val clientPlatform = CLIENT_PLATFORM_ANDROID
        val installId = offlineStore.deviceInstallId()
        val metadata = buildMetadata(
            installId = installId,
            deviceName = deviceName,
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            androidId = androidId,
            androidIdHash = androidIdHash,
            screenResolution = screenResolution,
            timezone = timezone,
            language = language,
            deviceMemoryBytes = deviceMemoryBytes,
            deviceMemoryGb = deviceMemoryGb,
            hardwareConcurrency = hardwareConcurrency,
        )

        return DeviceRegistrationSnapshot(
            installId = installId,
            deviceName = deviceName,
            deviceType = resolveDeviceType(),
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            deviceOs = "Android",
            deviceOsVersion = deviceOsVersion,
            appVersion = BuildConfig.VERSION_NAME,
            androidIdHash = androidIdHash,
            vendorSerialHash = vendorSerialHash,
            fingerprint = fingerprint,
            screenResolution = screenResolution,
            timezone = timezone,
            language = language,
            hardwareConcurrency = hardwareConcurrency,
            deviceMemoryGb = deviceMemoryGb,
            userAgent = buildUserAgent(
                appVersion = BuildConfig.VERSION_NAME,
                deviceOsVersion = deviceOsVersion,
                manufacturer = manufacturer,
                model = model,
            ),
            clientPlatform = clientPlatform,
            metadata = metadata,
        )
    }

    private fun buildMetadata(
        installId: String,
        deviceName: String,
        manufacturer: String,
        brand: String,
        model: String,
        androidId: String?,
        androidIdHash: String?,
        screenResolution: String,
        timezone: String,
        language: String,
        deviceMemoryBytes: Long?,
        deviceMemoryGb: Double?,
        hardwareConcurrency: Int,
    ): JSONObject {
        val systemVersion = normalizedValue(Build.VERSION.RELEASE, "Unknown")
        val system = JSONObject()
            .put("os_name", "Android")
            .put("os_version", systemVersion)
            .put("api_level", Build.VERSION.SDK_INT)
            .put("security_patch", normalizedValue(Build.VERSION.SECURITY_PATCH, "Unknown"))
            .put("build_id", normalizedValue(Build.ID, "Unknown"))
            .put("build_fingerprint", normalizedValue(Build.FINGERPRINT, "Unknown"))
            .put("kernel_version", normalizedValue(System.getProperty("os.version"), "Unknown"))
            .put("radio_version", normalizedValue(Build.getRadioVersion(), "Unknown"))

        val networkSnapshot = readNetworkSnapshot()
        val telephonySnapshot = readTelephonySnapshot()

        val uniqueIds = JSONObject()
            .put("hardware_serial", readHardwareSerial())
            .put("android_id", androidId)
            .put("android_id_hash", androidIdHash)
            .put("imei_list", toJsonArray(telephonySnapshot.imeiList))
            .put("meid_list", toJsonArray(telephonySnapshot.meidList))
            .put("mac_addresses", toJsonArray(networkSnapshot.macAddresses))
            .put("bluetooth_mac", readBluetoothMac())

        val identity = JSONObject()
            .put("install_id", installId)
            .put("device_name", deviceName)
            .put("manufacturer", manufacturer)
            .put("brand", brand)
            .put("model", model)
            .put("device", normalizedValue(Build.DEVICE, "Unknown"))
            .put("product", normalizedValue(Build.PRODUCT, "Unknown"))
            .put("board", normalizedValue(Build.BOARD, "Unknown"))
            .put("hardware", normalizedValue(Build.HARDWARE, "Unknown"))
            .put("bootloader", normalizedValue(Build.BOOTLOADER, "Unknown"))
            .put("supported_abis", toJsonArray(Build.SUPPORTED_ABIS.map(::normalizedValueOrNull)))

        val telephony = JSONObject()
            .put("has_telephony", telephonySnapshot.hasTelephony)
            .put("phone_count", telephonySnapshot.phoneCount)
            .put("sim_state", telephonySnapshot.simState)
            .put("sim_operator_name", telephonySnapshot.simOperatorName)
            .put("network_operator_name", telephonySnapshot.networkOperatorName)
            .put("phone_number", telephonySnapshot.phoneNumber)

        val network = JSONObject()
            .put("transport", networkSnapshot.transport)
            .put("ipv4", toJsonArray(networkSnapshot.ipv4))
            .put("ipv6", toJsonArray(networkSnapshot.ipv6))
            .put("mac_addresses", toJsonArray(networkSnapshot.macAddresses))
            .put("bluetooth_name", readBluetoothName())
            .put("bluetooth_mac", readBluetoothMac())

        val display = JSONObject()
            .put("screen_resolution", screenResolution)
            .put("density_dpi", context.resources.displayMetrics.densityDpi)
            .put("density_scale", context.resources.displayMetrics.density.toDouble())
            .put("font_scale", context.resources.configuration.fontScale.toDouble())

        val hardware = JSONObject()
            .put("hardware_concurrency", hardwareConcurrency)
            .put("device_memory_gb", deviceMemoryGb)
            .put("device_memory_bytes", deviceMemoryBytes)

        val app = JSONObject()
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_channel", BuildConfig.APP_CHANNEL)
            .put("build_env", BuildConfig.BUILD_ENV)
            .put("timezone", timezone)
            .put("language", language)
            .put("captured_at", Instant.now().toString())

        val permissions = JSONObject()
            .put("missing", toJsonArray(buildMissingPermissions()))

        return JSONObject()
            .put("schema_version", 1)
            .put("client_platform", CLIENT_PLATFORM_ANDROID)
            .put("unique_ids", uniqueIds)
            .put("identity", identity)
            .put("system", system)
            .put("telephony", telephony)
            .put("network", network)
            .put("display", display)
            .put("hardware", hardware)
            .put("app", app)
            .put("permissions", permissions)
    }

    private fun buildUserAgent(
        appVersion: String,
        deviceOsVersion: String,
        manufacturer: String,
        model: String,
    ): String {
        return "XcloudWmsScanner/$appVersion (${CLIENT_PLATFORM_ANDROID}; Android $deviceOsVersion; $manufacturer; $model; ${BuildConfig.APP_CHANNEL})"
    }

    private fun resolveDeviceType(): String {
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return if (screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            "TABLET"
        } else {
            "MOBILE"
        }
    }

    private fun buildDeviceName(
        manufacturer: String,
        model: String,
    ): String {
        val normalizedManufacturer = manufacturer.trim()
        val normalizedModel = model.trim()
        return if (
            normalizedManufacturer.isBlank() ||
            normalizedModel.startsWith(normalizedManufacturer, ignoreCase = true)
        ) {
            normalizedModel.ifBlank { "Android Device" }
        } else {
            "$normalizedManufacturer $normalizedModel".trim()
        }
    }

    private fun buildOsVersion(): String {
        return buildString {
            append(normalizedValue(Build.VERSION.RELEASE, "Unknown"))
            append(" (API ")
            append(Build.VERSION.SDK_INT)
            append(")")
        }
    }

    private fun readAndroidId(): String? {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        return if (androidId.isBlank() || androidId.equals("unknown", ignoreCase = true)) {
            null
        } else {
            androidId
        }
    }

    private fun readHardwareSerial(): String? {
        if (!hasPhoneStatePermission()) return null
        return runCatching { Build.getSerial() }
            .getOrNull()
            ?.trim()
            ?.takeIf(::isMeaningful)
    }

    private fun readBluetoothName(): String? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || !hasBluetoothPermission()) {
            return null
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return runCatching { adapter.name }
            .getOrNull()
            ?.trim()
            ?.takeIf(::isMeaningful)
    }

    private fun readBluetoothMac(): String? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || !hasBluetoothPermission()) {
            return null
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return normalizeMac(
            runCatching { adapter.address }
                .getOrNull(),
        )
    }

    private fun totalMemoryBytes(): Long? {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.totalMem.takeIf { it > 0L }
    }

    private fun bytesToGigabytes(bytes: Long): Double {
        return ((bytes / 1024.0 / 1024.0 / 1024.0) * 100).toInt() / 100.0
    }

    private fun buildMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (!hasPhoneStatePermission()) missing += Manifest.permission.READ_PHONE_STATE
            if (!hasPhoneNumbersPermission()) missing += Manifest.permission.READ_PHONE_NUMBERS
        }
        if (
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            !hasBluetoothPermission()
        ) {
            missing += Manifest.permission.BLUETOOTH_CONNECT
        }
        return missing
    }

    private fun readTelephonySnapshot(): TelephonySnapshot {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (!hasTelephony) {
            return TelephonySnapshot(hasTelephony = false)
        }

        val manager = context.getSystemService(TelephonyManager::class.java)
        val imeiList = mutableListOf<String>()
        val meidList = mutableListOf<String>()

        if (manager != null && hasPhoneStatePermission()) {
            runCatching {
                val slots = manager.phoneCount.coerceAtLeast(1)
                repeat(slots) { slotIndex ->
                    manager.getImei(slotIndex)
                        ?.trim()
                        ?.takeIf(::isMeaningful)
                        ?.let(imeiList::add)
                    manager.getMeid(slotIndex)
                        ?.trim()
                        ?.takeIf(::isMeaningful)
                        ?.let(meidList::add)
                }
                if (imeiList.isEmpty()) {
                    manager.imei
                        ?.trim()
                        ?.takeIf(::isMeaningful)
                        ?.let(imeiList::add)
                }
            }
        }

        return TelephonySnapshot(
            hasTelephony = true,
            phoneCount = manager?.phoneCount ?: 0,
            simState = simStateLabel(manager?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN),
            simOperatorName = manager?.simOperatorName?.trim()?.takeIf(::isMeaningful),
            networkOperatorName = manager?.networkOperatorName?.trim()?.takeIf(::isMeaningful),
            phoneNumber = if (manager != null && (hasPhoneStatePermission() || hasPhoneNumbersPermission())) {
                runCatching { manager.line1Number }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf(::isMeaningful)
            } else {
                null
            },
            imeiList = imeiList,
            meidList = meidList,
        )
    }

    private fun readNetworkSnapshot(): NetworkSnapshot {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())

        val activeInterfaces = interfaces.mapNotNull { networkInterface ->
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

        val ipv4 = activeInterfaces.flatMap { networkInterface ->
            networkInterface.addresses
                .filterNot { address -> address is Inet6Address }
                .map { address -> "${networkInterface.name}: ${cleanAddress(address)}" }
        }

        val ipv6 = activeInterfaces.flatMap { networkInterface ->
            networkInterface.addresses
                .filterIsInstance<Inet6Address>()
                .map { address -> "${networkInterface.name}: ${cleanAddress(address)}" }
        }

        val macAddresses = activeInterfaces.mapNotNull { snapshot ->
            snapshot.macAddress?.let { "${snapshot.name}: $it" }
        }

        return NetworkSnapshot(
            transport = activeTransportLabel(capabilities),
            ipv4 = ipv4,
            ipv6 = ipv6,
            macAddresses = macAddresses,
        )
    }

    private fun activeTransportLabel(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "UNKNOWN"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
    }

    private fun simStateLabel(simState: Int): String {
        return when (simState) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            else -> "UNKNOWN"
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

    private fun normalizedValue(
        value: String?,
        fallback: String,
    ): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.isBlank() || normalized.equals("unknown", ignoreCase = true)) {
            fallback
        } else {
            normalized
        }
    }

    private fun normalizedValueOrNull(value: String?): String? {
        return value?.trim()?.takeIf(::isMeaningful)
    }

    private fun isMeaningful(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.trim()
        return normalized != "unknown" &&
            normalized != Build.UNKNOWN &&
            normalized != "N/A" &&
            normalized != "null"
    }

    private fun normalizeMac(value: String?): String? {
        val normalized = value?.trim()?.uppercase(Locale.US)
        return when {
            normalized.isNullOrBlank() -> null
            normalized == "02:00:00:00:00:00" -> null
            else -> normalized
        }
    }

    private fun cleanAddress(address: InetAddress): String {
        return address.hostAddress?.substringBefore('%').orEmpty().ifBlank { "UNKNOWN" }
    }

    private fun toJsonArray(values: List<String?>): JSONArray {
        return JSONArray(values.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) })
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private data class NetworkSnapshot(
        val transport: String,
        val ipv4: List<String>,
        val ipv6: List<String>,
        val macAddresses: List<String>,
    )

    private data class TelephonySnapshot(
        val hasTelephony: Boolean,
        val phoneCount: Int = 0,
        val simState: String? = null,
        val simOperatorName: String? = null,
        val networkOperatorName: String? = null,
        val phoneNumber: String? = null,
        val imeiList: List<String> = emptyList(),
        val meidList: List<String> = emptyList(),
    )

    private data class InterfaceSnapshot(
        val name: String,
        val addresses: List<InetAddress>,
        val macAddress: String?,
    )

    private companion object {
        const val CLIENT_PLATFORM_ANDROID = "SCANNER_ANDROID"
    }
}
