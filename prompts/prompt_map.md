# Prompt Map — Native Android App

File này lưu mapping giữa prompt/user request và commit message để truy vết.

## Format

```md
## YYYY-MM-DD HH:mm — <short title>

- Prompt summary:
- Ticket/Issue ID:
- Scope:
- Main files changed:
- Tests run:
- Commit message: `<type>(<scope>): <summary>`
- Notes/Risks:
```

---

## 2026-06-01 10:15 — Phase 4 scanner abstraction & PDA input adapters

- Prompt summary: Triển khai scanner core cho `app/android` (prompt 04): mọi barcode/QR đi qua `ScannerManager`; thêm domain models (`ScanEvent`/`ScanSource`/`ScannerMode`/`ParsedBarcode`), core interfaces (`ScannerManager`/`ScannerAdapter`/`BarcodeParser`/`FeedbackManager`), adapters (keyboard wedge, broadcast intent cấu hình được, manual, camera placeholder), debounce chống trùng cấu hình được, beep/rung feedback và mở rộng màn Kiểm tra máy quét.
- Ticket/Issue ID: APP-PHASE4
- Scope: `app/android + docs` - chỉ thêm tầng scanner abstraction phía Android, không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/scanner/**` (models, `ScannerManager`, `DefaultScannerManager`, `BarcodeParser`, `FeedbackManager`, `ScanDebouncer`, `ScannerConfig`)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/scanner/adapter/**` (Manual/KeyboardWedge/Broadcast/Camera)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/di/AppContainer.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/storage/AppPreferences.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/MainActivity.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/scannertest/**`
  - `app/android/app/src/test/java/vn/delfi/xcloudwms/core/scanner/**`
  - `app/android/gradle/libs.versions.toml`, `app/android/app/build.gradle.kts`
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `./gradlew -p app/android :app:testDevDebugUnitTest` ✅ pass (parser + debounce)
  - `./gradlew -p app/android :app:assembleDevDebug` ✅ pass
  - `git -C app diff --check` ✅ no whitespace error
- Commit message: `feat(app-scan): add scanner abstraction and pda input adapters`
- Notes/Risks:
  - Camera adapter mới là placeholder (chưa thêm CameraX/ML Kit, chưa xin quyền CAMERA) theo quyết định phase này.
  - Keyboard wedge phân biệt quét máy/gõ tay bằng độ trễ phím; ký tự đầu của một lần quét nhanh vào ô đang focus có thể để lại 1 ký tự thừa, mã phát ra vẫn đầy đủ.
  - Broadcast receiver đăng ký cờ EXPORTED để nhận intent từ app scanner ngoài (API 34+); action/extra key do người dùng cấu hình trong màn Kiểm tra máy quét, không hard-code vendor.
  - `BarcodeParser` chỉ là gợi ý phía client; phân loại chính thức vẫn thuộc backend lookup (`rpc_traceability_lookup`) ở phase Stock Lookup.

## 2026-05-30 15:11 — Phase 2 auth tenant và warehouse context

- Prompt summary: Triển khai auth/context cho native Android app theo endpoint thật đã audit trong `app/specs/api_endpoints_draft.md`: login Supabase Auth, secure token storage, session restore/refresh, load profile/tenant/permissions/warehouses, chọn kho hiện tại và home menu theo quyền.
- Ticket/Issue ID: APP-PHASE2
- Scope: `app/android + docs` - thêm auth/context native thật, không đổi DB/RPC/API contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/{config,error,network,security,storage}/**`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/{auth,session}/**`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/UserSession.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/{login,home,splash,warehouse}/**`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/navigation/**`
  - `app/android/app/src/main/res/values/strings.xml`
  - `app/prompts/prompt_map.md`
  - `docs/commit_prompt_map.md`
- Tests run:
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅ pass trước khi làm
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅ pass sau khi triển khai auth/context
  - `cd app/android && git diff --check`
  - `cd app/android && rg -n 'Tenant|Menu|Scanner|Broadcast|Camera|API key|URL kết nối|Connection|Home|Login' app/src/main/java/vn/delfi/xcloudwms app/src/main/res --glob '!**/build/**'`
- Commit message: `feat(app-auth): add login tenant and warehouse context`
- Notes/Risks:
  - Login thật hiện yêu cầu nhập thủ công `địa chỉ kết nối` và `khóa truy cập công khai` vì repo chưa có QR/runtime config được cấp sẵn; điều này bám đúng kết luận audit là không được hard-code Supabase host/key.
  - Device license/pending-device chưa nằm trong prompt phase này nên chưa được native hóa; session restore hiện tập trung vào auth/profile/permission/warehouse context.
  - `SupabaseAuthRepository` còn warning unchecked cast do phải chịu được nhiều shape JSON khác nhau từ `profiles/users/permissions/user_warehouses`.

## 2026-05-30 11:13 — Phase 1 Android Compose foundation

- Prompt summary: Tạo native Android project trong `app/android` cho Xcloud WMS Scanner với Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel + StateFlow, logger an toàn, network/scanner placeholders và ba màn chờ `Đăng nhập` / `Trang chủ` / `Kiểm tra máy quét`.
- Ticket/Issue ID: APP-PHASE1
- Scope: `app + docs` - scaffold project Android native phase 1, chưa gọi API thật, chưa đổi DB/RPC/API contract.
- Main files changed:
  - `app/android/**`
  - `app/prompts/prompt_map.md`
  - `docs/commit_prompt_map.md`
- Tests run:
  - `git -C app diff --check`
  - `rg -n '"[^"]*(Placeholder|placeholder|screen|foundation|pipeline|mobile-first|Mode:)[^"]*"' app/android/app/src/main/java/vn/delfi/xcloudwms/feature app/android/app/src/main/java/vn/delfi/xcloudwms/core/ui`
  - `cd app/android && java -version` ✅ OpenJDK 17.0.19
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅ pass
- Commit message: `chore(app): initialize native android compose project`
- Notes/Risks:
  - Đã scaffold flavors `dev/staging/prod`, `AppConfig`, `AppContainer`, `SessionRepository`, `ScannerManager`, theme paper/light gần scanner PWA và navigation shell.
  - Đã vá dependency `com.google.android.material:material`, suppress warning `compileSdk 35` và lỗi import `weight` trong Compose để `:app:assembleDevDebug` chạy thành công.

## 2026-05-30 10:44 — Phase 0 scanner parity discovery

- Prompt summary: Audit `scanner/` để chốt route, component, auth context, warehouse scope, GR/GI/PA/IC/Lookup contract cho native Android PDA app; điền parity matrix và API draft bằng endpoint thật đang dùng.
- Ticket/Issue ID: APP-PHASE0
- Scope: `app + scanner + docs` - chỉ audit/spec mapping, chưa code Android.
- Main files changed:
  - `app/specs/scanner_pwa_parity_matrix.md`
  - `app/specs/api_endpoints_draft.md`
  - `app/prompts/prompt_map.md`
  - `docs/commit_prompt_map.md`
- Tests run: Audit source bằng `rg` và `sed`; không chạy build/test vì chưa đổi runtime code.
- Commit message: `docs(app): audit scanner pwa parity and backend contracts`
- Notes/Risks:
  - Scanner PWA thực tế đang dựa vào Supabase Auth, RPC và table CRUD; không có bằng chứng về backend REST `/scanner/*` riêng cho các flow kho.
  - `rpc_ic_complete` chỉ thấy trong playbook tài liệu, không thấy scanner source gọi; `rpc_gi_submit` ở standard GI cũng đang bị UI hard-disable, nên Android phase sau phải bám đúng evidence này.

## Initial — Create native Android app documentation pack

- Prompt summary: Create `/app` folder documentation and implementation prompts for native Android PDA scanner app parity with scanner PWA.
- Ticket/Issue ID: APP-INIT
- Scope: docs/prompts/specs/checklists only.
- Main files changed: `app/**`
- Tests run: N/A
- Commit message: `docs(app): add native android pda scanner implementation pack`
- Notes/Risks: Requires agent to audit actual scanner PWA endpoints before coding.
