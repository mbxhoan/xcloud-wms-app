# 08 — Build, Deploy, Test A-Z for Non-native Developer

Tài liệu này dành cho người chưa quen build native Android.

## 1. Cài công cụ

### Bắt buộc

- Android Studio bản stable mới nhất.
- JDK đi kèm Android Studio hoặc JDK được Android Studio khuyến nghị.
- Android SDK Platform theo project.
- Android SDK Build Tools.
- Git.

### Kiểm tra

Mở Terminal:

```bash
java -version
./gradlew --version
```

Nếu chưa có project Android, `./gradlew` sẽ có sau khi tạo project.

## 2. Tạo project Android mới

Trong Android Studio:

1. New Project.
2. Chọn Empty Activity / Empty Compose Activity.
3. Language: Kotlin.
4. Minimum SDK: chọn mức phù hợp PDA đang dùng. Nếu chưa rõ, tạm chọn Android 8.0+ để hỗ trợ nhiều PDA cũ.
5. Name: `XcloudWmsScanner`.
6. Package: `vn.delfi.xcloudwms`.
7. Save vào `xcloud-wms-workspace/app/android` hoặc nếu muốn repo app riêng thì root `app/`.

Khuyến nghị nếu folder `app/` đang là docs: tạo code trong:

```txt
app/android/
```

## 3. Cấu hình environment

Tạo file local không commit:

```txt
app/android/local.properties
```

Dùng Gradle build config để có:

```txt
SUPABASE_URL_DEV
SUPABASE_ANON_KEY_DEV
SUPABASE_URL_STAGING
SUPABASE_ANON_KEY_STAGING
SUPABASE_URL_PROD
SUPABASE_ANON_KEY_PROD
```

Hoặc nếu team đang dùng tên cũ, app vẫn chấp nhận fallback:

```txt
BASE_API_URL_DEV
BASE_API_URL_STAGING
BASE_API_URL_PROD
ANON_KEY_DEV
ANON_KEY_STAGING
ANON_KEY_PROD
```

Ví dụ:

```txt
sdk.dir=/Users/<user>/Library/Android/sdk
SUPABASE_URL_DEV=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY_DEV=<anon-key>
```

Khi app chưa có cấu hình lưu cục bộ, bản build `dev/staging/prod` sẽ tự lấy cặp URL/key tương ứng từ `local.properties` và prefill ở màn đăng nhập.

Không commit secrets.

## 4. Chạy trên emulator

1. Android Studio > Device Manager.
2. Create virtual device.
3. Chọn Pixel/phone bình thường.
4. Run app.

Emulator dùng để test UI/auth/API cơ bản, không đại diện cho PDA scanner.

## 5. Chạy trên Android phone thật

1. Bật Developer Options.
2. Bật USB Debugging.
3. Cắm USB.
4. Android Studio chọn device.
5. Run.

## 6. Chạy trên PDA thật

1. Bật Developer Options/USB Debugging trên PDA.
2. Cắm USB hoặc dùng ADB over Wi-Fi nếu thiết bị hỗ trợ.
3. Cài app debug.
4. Cấu hình scanner mode trên PDA:
   - Keyboard wedge hoặc Broadcast/Intent.
   - Nếu broadcast: set action/extra key đúng docs adapter.
5. Nếu app đứng ở màn `Đăng nhập`, kiểm tra lại `local.properties` hoặc nhập thủ công `Địa chỉ kết nối` + `Khóa truy cập công khai`, sau đó bấm `Lưu cấu hình`.
6. Test nút scan vật lý ở màn Stock Lookup trước.

## 7. Build debug APK

Trong terminal tại folder Android project:

```bash
./gradlew assembleDebug
```

APK thường nằm ở:

```txt
app/build/outputs/apk/debug/app-debug.apk
```

Cài bằng adb:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 8. Build staging/release APK

Sau khi có build variants/flavors:

```bash
./gradlew assembleStagingRelease
./gradlew assembleProdRelease
```

Hoặc nếu chưa có flavor:

```bash
./gradlew assembleRelease
```

## 9. Ký release app

Không gửi keystore lung tung. Keystore là tài sản bảo mật.

Quy trình:

1. Android Studio > Build > Generate Signed Bundle/APK.
2. Tạo keystore nội bộ.
3. Lưu keystore ở nơi an toàn, không commit.
4. Ghi lại password trong password manager công ty.
5. Build signed APK/AAB.

## 10. Cài nội bộ cho PDA

Cách đơn giản giai đoạn đầu:

- Gửi APK qua Google Drive nội bộ/Zalo/USB rồi cài thủ công.
- Hoặc dùng MDM nếu công ty có.
- Hoặc tạo internal web download page có version notes.

Khuyến nghị production:

- Dùng MDM/Enterprise distribution.
- Có version check API để nhắc update.

## 11. Test nhanh sau khi cài

Smoke test:

1. Mở app.
2. Login staging.
3. Chọn kho.
4. Scan location bằng nút vật lý.
5. Scan product bằng nút vật lý.
6. Stock lookup trả đúng dữ liệu.
7. Mở PA tạo draft, add 1 dòng, không submit nếu không dùng data test.
8. Logout.

## 12. Test nghiệp vụ staging

Chuẩn bị data:

- 1 tenant test.
- 2 warehouse.
- 5 location.
- 3 product: NONE, LOT, SERIAL.
- Stock có sẵn cho từng case.
- GR/GI/IC/PA test documents.

Test cases:

- GR receive product NONE.
- GR receive product LOT có expiry.
- GR receive SERIAL không trùng.
- GI pick đúng allocation.
- GI overpick bị chặn.
- PA from A to B, stock chuyển đúng.
- IC scan serial duplicate bị chặn.

## 13. Khi app bị lỗi

Thu thập thông tin:

- App version.
- Device model.
- Android version.
- User/tenant/warehouse test.
- Module đang lỗi.
- Mã phiếu.
- Barcode đã scan.
- Ảnh màn hình.
- Logcat nếu dev có thể lấy.

Không gửi password/token.

## 14. Lệnh hữu ích

```bash
adb devices
adb install -r path/to/app.apk
adb uninstall vn.delfi.xcloudwms
adb logcat | grep Xcloud
adb shell input text "LOC:A1"
adb shell input keyevent 66
```

## 15. Release checklist ngắn

Trước khi gửi APK cho kho thật:

- Đúng base URL production/staging.
- Đúng app version.
- Signed APK.
- Không bật debug logs nhạy cảm.
- Login/logout OK.
- Scan hardware OK.
- GR/GI/PA/IC smoke OK trên staging.
- Có rollback APK version cũ.
