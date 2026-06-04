# 11 — Gradle Tasks, Build Variants & Release Signing

Tài liệu chuẩn hóa lệnh build/test và cấu hình variant cho native Android app.
Chạy mọi lệnh tại folder `app/android` (nơi có `gradlew`).

Nguồn sự thật cho phần này:

- `app/android/app/build.gradle.kts`
- `app/android/gradle/libs.versions.toml`
- `app/android/keystore.properties.example`

## 1. Yêu cầu môi trường

- JDK 17 (Temurin 17.x đã verify OK).
- Android SDK Platform `android-35` (`compileSdk = 35`, `targetSdk = 35`).
- Android SDK Build Tools (đã verify với `35.0.0`).
- `minSdk = 26` (Android 8.0) để chạy được PDA cũ.

Kiểm tra nhanh:

```bash
java -version
./gradlew --version
```

`local.properties` phải có `sdk.dir` trỏ đúng Android SDK. File này KHÔNG commit.

## 2. Build variants

App dùng 1 flavor dimension `environment` × 2 build type.

### Flavor (productFlavors)

| Flavor | applicationId | versionName suffix | Dùng cho |
|---|---|---|---|
| `dev` | `vn.delfi.xcloudwms.dev` | `-dev` | Dev/local |
| `staging` | `vn.delfi.xcloudwms.staging` | `-staging` | QA/UAT |
| `prod` | `vn.delfi.xcloudwms` | (none) | Release thật |

`applicationIdSuffix` khác nhau cho phép cài song song dev + staging + prod trên cùng PDA.

### Build type

| Build type | Minify | Ký (signing) |
|---|---|---|
| `debug` | off | debug key tự động của Android |
| `release` | off (`isMinifyEnabled = false`) | chỉ ký khi có `keystore.properties` (xem mục 5) |

### Ma trận variant (flavor × build type)

| Variant | Lệnh assemble | Output APK |
|---|---|---|
| devDebug | `./gradlew assembleDevDebug` | `app/build/outputs/apk/dev/debug/app-dev-debug.apk` |
| devRelease | `./gradlew assembleDevRelease` | `app/build/outputs/apk/dev/release/app-dev-release{-unsigned}.apk` |
| stagingDebug | `./gradlew assembleStagingDebug` | `app/build/outputs/apk/staging/debug/app-staging-debug.apk` |
| stagingRelease | `./gradlew assembleStagingRelease` | `app/build/outputs/apk/staging/release/app-staging-release{-unsigned}.apk` |
| prodDebug | `./gradlew assembleProdDebug` | `app/build/outputs/apk/prod/debug/app-prod-debug.apk` |
| prodRelease | `./gradlew assembleProdRelease` | `app/build/outputs/apk/prod/release/app-prod-release{-unsigned}.apk` |

Tên file có `-unsigned` khi build release mà chưa cấu hình keystore.

### Cấu hình từng env

Mỗi flavor đọc `BASE_API_URL` / `DEFAULT_CONNECTION_URL` / `DEFAULT_CONNECTION_ANON_KEY` từ
`local.properties` (hoặc Gradle property / env var) theo key:

```txt
SUPABASE_URL_DEV / SUPABASE_ANON_KEY_DEV      (fallback BASE_API_URL_DEV / ANON_KEY_DEV)
SUPABASE_URL_STAGING / SUPABASE_ANON_KEY_STAGING
SUPABASE_URL_PROD / SUPABASE_ANON_KEY_PROD
```

Nếu để trống, flavor dùng host `*.example.invalid` (build chạy nhưng app không kết nối thật).
Chi tiết env xem `08_BUILD_DEPLOY_TEST_A_TO_Z.md` mục 3.

## 3. Lệnh thường dùng

### Test

```bash
./gradlew test                    # unit test toàn bộ flavor (JVM, không cần thiết bị)
./gradlew testProdDebugUnitTest   # unit test 1 variant cho nhanh
./gradlew connectedAndroidTest    # instrumented test, CẦN thiết bị/emulator
```

Report HTML: `app/build/reports/tests/<task>/index.html`
Report XML : `app/build/test-results/<task>/*.xml`

### Build APK

```bash
./gradlew assembleDebug           # tất cả flavor, build type debug
./gradlew assembleRelease         # tất cả flavor, build type release
./gradlew assembleStagingRelease  # 1 variant cụ thể
```

### Build AAB (Android App Bundle, cho store/MDM)

```bash
./gradlew bundleProdRelease       # -> app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

### Lint

```bash
./gradlew lint                    # report: app/build/reports/lint-results-*.html
./gradlew lintProdRelease
```

### Dọn dẹp

```bash
./gradlew clean
```

### Liệt kê task

```bash
./gradlew tasks --all | grep -iE 'assemble|bundle|test|lint'
```

## 4. Lệnh verify chuẩn (gate trước release)

```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

Trạng thái verify gần nhất trong môi trường dev (JDK 17 + SDK android-35):

- `./gradlew test` → BUILD SUCCESSFUL, 69 unit test / 12 class pass (mỗi flavor).
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (dev/staging/prod debug).
- `./gradlew assembleRelease` → BUILD SUCCESSFUL, ra APK `*-release-unsigned.apk` khi chưa có keystore.

## 5. Release signing (không commit keystore)

### Nguyên tắc

- Keystore (`.jks`/`.keystore`) và password là TÀI SẢN BẢO MẬT, không commit, không gửi chat/USB tùy tiện.
- `.gitignore` đã chặn `keystore.properties`, `*.jks`, `*.keystore`.
- MẤT keystore = không thể ký bản update cùng identity → user phải gỡ cài app cũ. Backup keystore vĩnh viễn.

### Cơ chế trong `build.gradle.kts`

Signing là opt-in qua file `keystore.properties` đặt ở `app/android/` (cùng cấp `gradlew`):

- Có file `keystore.properties` hợp lệ → `assemble*Release` ký bằng key đó, ra `app-*-release.apk`.
- Không có file → release giữ nguyên trạng thái cũ: build OK nhưng ra `app-*-release-unsigned.apk`.

Nhờ vậy CI / máy dev không có keystore vẫn chạy `assembleRelease` không lỗi.

### Tạo keystore một lần

```bash
keytool -genkeypair -v \
  -keystore xcloud-wms-release.jks \
  -alias xcloud-wms \
  -keyalg RSA -keysize 2048 -validity 10000
```

Lưu `.jks` ngoài repo + ghi password vào password manager công ty.

### Cấu hình máy build

```bash
cp keystore.properties.example keystore.properties
# rồi sửa storeFile/storePassword/keyAlias/keyPassword
```

`keystore.properties`:

```properties
storeFile=/absolute/path/secrets/xcloud-wms-release.jks
storePassword=...
keyAlias=xcloud-wms
keyPassword=...
```

### Build signed + verify

```bash
./gradlew assembleProdRelease
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$APKSIGNER" verify --print-certs app/build/outputs/apk/prod/release/app-prod-release.apk
```

Đã verify trong môi trường dev: với `keystore.properties` tạm trỏ tới keystore test,
`assembleProdRelease` ra `app-prod-release.apk` và `apksigner verify` xác nhận chữ ký hợp lệ.

### Ký thủ công (nếu không dùng keystore.properties)

Dùng `Build > Generate Signed Bundle/APK` trong Android Studio, hoặc:

```bash
"$APKSIGNER" sign --ks xcloud-wms-release.jks \
  --out app-prod-release.apk \
  app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk
```

## 6. Liên kết tài liệu

- A-Z build/deploy/test cho người mới: `08_BUILD_DEPLOY_TEST_A_TO_Z.md`
- QA/UAT checklist: `09_QA_UAT_CHECKLIST.md`
- Versioning + rollback: `12_VERSIONING_AND_ROLLBACK_POLICY.md`
- Release checklist ngắn: `../checklists/android_release_checklist.md`
- Smoke test: `../checklists/smoke_test_checklist.md`
- QA execution report: `../checklists/qa_execution_report_template.md`
- Script helper: `../scripts/`
