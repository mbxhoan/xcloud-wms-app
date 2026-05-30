# 02 — Tech Stack & Android Architecture

## 1. Stack đề xuất

Dùng native Android hiện đại, dễ thuê dev, dễ maintain:

- Kotlin.
- Jetpack Compose.
- Material 3.
- Navigation Compose.
- ViewModel + StateFlow.
- Retrofit/OkHttp hoặc Ktor Client.
- Kotlinx Serialization hoặc Moshi.
- Room cho local cache/draft.
- DataStore cho settings nhẹ.
- EncryptedSharedPreferences/Keystore cho token.
- WorkManager cho retry/background sync an toàn.
- Hilt hoặc Koin cho dependency injection.
- CameraX + ML Kit/ZXing fallback scan.

## 2. Module/package structure đề xuất

```txt
app/
  build.gradle.kts
  settings.gradle.kts
  app/
    src/main/java/vn/delfi/xcloudwms/
      MainActivity.kt
      XcloudWmsApp.kt
      core/
        config/
        network/
        auth/
        security/
        scanner/
        sync/
        ui/
        util/
      data/
        local/
        remote/
        repository/
        dto/
        mapper/
      domain/
        model/
        usecase/
      feature/
        auth/
        home/
        warehouse/
        stocklookup/
        gr/
        gi/
        pa/
        ic/
        settings/
```

## 3. Layering

```txt
Compose Screen
  ↓ user action / scan event
ViewModel
  ↓ call usecase
UseCase
  ↓ call repository
Repository
  ↓ remote/local data source
API / Room / DataStore
```

Nguyên tắc:

- Screen chỉ render state, gửi event.
- ViewModel quản lý UI state, loading, error.
- UseCase chứa orchestration nhẹ.
- Repository map DTO <-> domain model.
- API client không bị gọi trực tiếp từ UI.

## 4. Scan event architecture

Scanner là cross-cutting concern. Không nhét logic scan vào từng screen theo kiểu input text rời rạc.

```kotlin
sealed interface ScanEvent {
    data class Success(
        val raw: String,
        val symbology: String? = null,
        val source: ScanSource,
        val timestamp: Long
    ) : ScanEvent

    data class Error(
        val message: String,
        val source: ScanSource
    ) : ScanEvent
}

enum class ScanSource { SDK, BROADCAST, KEYBOARD_WEDGE, CAMERA, MANUAL }
enum class ScannerMode { GENERIC, LOCATION, PRODUCT, LOT, SERIAL, DOCUMENT }
```

Mỗi feature screen subscribe event khi active:

```kotlin
scannerEvents.collect { event ->
    when (currentStep) {
        Step.ScanFromLocation -> handleLocationScan(event.raw)
        Step.ScanProduct -> handleProductScan(event.raw)
        Step.ScanSerial -> handleSerialScan(event.raw)
    }
}
```

## 5. Environment/config

Dùng build variants:

```txt
dev      -> API staging/dev, log nhiều hơn
staging  -> API staging/UAT
prod     -> API production, log ít, crash reporting bật
```

Config cần có:

- `BASE_API_URL`
- `APP_CHANNEL=SCANNER_NATIVE`
- `APP_VERSION`
- `BUILD_ENV`
- `ENABLE_CAMERA_SCAN_FALLBACK`
- `ENABLE_DEVICE_LICENSE_CHECK`

Không commit secrets vào repo.

## 6. Error model chuẩn

Chuẩn hóa API error để UI hiển thị dễ hiểu:

```kotlin
data class AppError(
    val code: String,
    val message: String,
    val userAction: String? = null,
    val retryable: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap()
)
```

Ví dụ:

- `OUT_OF_STOCK`: “Tồn kho không đủ. Vui lòng refresh hoặc chọn vị trí khác.”
- `DUPLICATE_SERIAL`: “Serial đã tồn tại/đã scan. Vui lòng kiểm tra lại.”
- `PERMISSION_DENIED`: “Bạn không có quyền thao tác kho này.”
- `SESSION_EXPIRED`: “Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.”

## 7. State model mẫu cho màn scan

```kotlin
data class ScanOperationState(
    val loading: Boolean = false,
    val offline: Boolean = false,
    val currentStep: Step,
    val warehouse: WarehouseSummary?,
    val draftLines: List<DraftLine> = emptyList(),
    val lastScan: String? = null,
    val error: AppError? = null,
    val successMessage: String? = null
)
```

Nút submit:

- Disabled nếu `loading = true`.
- Disabled nếu thiếu required step.
- Disabled nếu offline và commit không hỗ trợ queue.

## 8. Testing architecture

- Unit test ViewModel event handling.
- Unit test barcode parser.
- Unit test repository mapper.
- Integration test API fake bằng MockWebServer.
- Manual test PDA hardware scanner.
- Regression test WMS flow bằng staging data.

## 9. Rule quan trọng

Client chỉ validate để UX nhanh. Backend vẫn là nguồn quyết định cuối cùng.

Ví dụ client có thể check `qty > 0`, nhưng stock enough, serial unique, permission, status flow phải backend enforce trong transaction.
