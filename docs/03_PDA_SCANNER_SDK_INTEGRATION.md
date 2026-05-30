# 03 — PDA Scanner SDK Integration Guide

## 1. Mục tiêu

PDA scanner có nhiều loại: Zebra, Honeywell, Datalogic, Urovo, Chainway, Newland, Seuic, CipherLab, Unitech... Mỗi loại có SDK/cách bắn barcode khác nhau. App phải thiết kế theo adapter để không khóa cứng vào một vendor.

## 2. Chiến lược hỗ trợ scanner

Ưu tiên theo thứ tự:

1. Vendor SDK chính thức.
2. Broadcast intent / DataWedge-style profile.
3. Keyboard wedge.
4. Camera scan fallback.
5. Manual input fallback.

## 3. Vì sao cần abstraction

Nếu code trực tiếp SDK vào màn GR/GI/PA/IC, sau này đổi PDA vendor sẽ phải sửa toàn bộ app. Do đó mọi scan phải đi qua `ScannerManager`.

```kotlin
interface ScannerManager {
    val events: Flow<ScanEvent>
    fun start()
    fun stop()
    fun setMode(mode: ScannerMode)
    fun enable()
    fun disable()
}
```

Adapter ví dụ:

```txt
ScannerManager
  ├── KeyboardWedgeScannerAdapter
  ├── BroadcastScannerAdapter
  ├── ZebraDataWedgeScannerAdapter
  ├── HoneywellScannerAdapter
  ├── UrovoScannerAdapter
  ├── ChainwayScannerAdapter
  └── CameraScannerAdapter
```

## 4. Keyboard wedge mode

Nhiều PDA mặc định bắn barcode như bàn phím. Ưu điểm: dễ tích hợp. Nhược điểm: phụ thuộc focus input, dễ bắn sai ô.

Native app nên tạo một hidden/focused input hoặc capture key events ở Activity:

- Gom ký tự đến khi gặp ENTER/TAB.
- Debounce duplicate.
- Gửi raw text vào `ScannerManager`.
- Không để người dùng cần chạm đúng input.

Pseudo flow:

```txt
onKeyEvent(char)
  -> append buffer
onKeyEvent(ENTER)
  -> emit ScanEvent.Success(buffer)
  -> clear buffer
```

## 5. Broadcast intent mode

Nhiều PDA cho cấu hình app scanner phát broadcast intent khi scan thành công.

Cần docs cho dev cấu hình PDA:

- Action name.
- Extra key chứa barcode data.
- Extra key chứa symbology nếu có.
- Package restriction nếu cần.

Mẫu adapter:

```kotlin
class BroadcastScannerAdapter(
    private val context: Context,
    private val action: String,
    private val dataExtraKey: String,
    private val symbologyExtraKey: String?
) : ScannerManager {
    // register/unregister BroadcastReceiver theo lifecycle
}
```

## 6. Vendor SDK mode

Khi đã chốt model PDA, tạo adapter riêng.

Checklist khi tích hợp vendor SDK:

- SDK có file `.aar` hay Maven repository?
- Min SDK/target SDK support?
- Cần permission gì?
- Cần init/deinit scanner khi Activity resume/pause?
- Có event callback barcode data không?
- Có điều khiển beep/LED/vibrate từ SDK không?
- Có config symbology: QR, Code128, EAN13, DataMatrix?
- Có config trigger physical key không?
- Có sample app từ vendor không?

## 7. Camera fallback

Camera fallback không thay thế hardware scanner trong kho, nhưng hữu ích cho Android phone thường, demo, hoặc PDA không có SDK.

Yêu cầu:

- Permission camera.
- Frame analyzer.
- Torch toggle.
- Scan box rõ.
- Debounce duplicate.
- Cho phép nhập tay nếu camera fail.

## 8. Barcode parser

Không assume mọi barcode đều là product code. Cần parser phân loại:

```txt
LOC:A1-01-02       -> LOCATION
SKU:SP001          -> PRODUCT
LOT:LOT202605      -> LOT
SN:TEST0001        -> SERIAL
GR:GR-2605-0001    -> DOCUMENT_GR
GI:GI-2605-0001    -> DOCUMENT_GI
```

Nếu barcode hiện tại chỉ là plain text, parser cần gọi API lookup:

```txt
scan raw -> API /scanner/lookup?code=raw&warehouse_id=...
         -> backend trả type: LOCATION/PRODUCT/SERIAL/LOT/DOCUMENT
```

## 9. Feedback chuẩn khi scan

| Case | UI | Sound | Vibration |
|---|---|---|---|
| Scan đúng bước | highlight xanh, tự qua bước kế | beep ngắn | 40ms |
| Scan sai type | banner vàng/đỏ | beep lỗi | 120ms |
| API validate fail | modal/banner đỏ | beep lỗi | 200ms |
| Duplicate scan | toast “Đã scan” | beep nhẹ | 40ms |
| Offline | banner “Mất kết nối” | không bắt buộc | không bắt buộc |

## 10. PDA device lab checklist

Mỗi model PDA cần ghi lại:

- Brand/model.
- Android version.
- Scanner mode dùng được: SDK/Broadcast/Keyboard.
- Barcode types test: QR, Code128, EAN13.
- Có trigger physical không.
- App foreground/background behavior.
- Có bị mất focus không.
- Tốc độ scan liên tục 50 mã/phút.
- Kết quả pin sau 2 giờ test.

## 11. Rủi ro thường gặp

- Vendor SDK không tương thích target SDK mới.
- Keyboard wedge bắn vào field sai.
- Broadcast action khác nhau giữa firmware.
- Thiết bị sleep làm mất scanner service.
- Nút scan vật lý bị hệ điều hành chiếm.
- Barcode có ENTER suffix gây submit lặp.
- User scan quá nhanh trước khi API response.

## 12. Rule cuối

Feature screen không được biết đang dùng Zebra/Honeywell/Urovo. Screen chỉ nhận `ScanEvent`. Adapter nào được dùng là cấu hình theo device/build/runtime.
