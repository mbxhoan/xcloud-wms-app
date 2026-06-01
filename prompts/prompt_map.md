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

## 2026-06-01 18:40 — Phase 7 GI goods issue picking

- Prompt summary: Triển khai module Goods Issue Picking native cho `app/android` (prompt 07), parity scanner PWA `GiPickClient.tsx` + route `/app/outbound`: danh sách phiếu xuất được phân công theo kho (CREATED/PICKING/PICKED), mở phiếu xem tiến độ pick từng dòng, auto `rpc_gi_start_picking`, quét serial (`rpc_gi_check_serial_scan` + `rpc_gi_bind_serial_to_summary_line` cho summary mode, hoặc cập nhật `gi_details.picked_quantity`), quét lot (`rpc_gi_check_lot_scan` + cập nhật picked_quantity), pick số lượng NONE (cập nhật detail reserved/insert), chống overpick phía client + backend, chốt picking (`rpc_gi_submit`) và xuất kho (`rpc_gi_complete`) với confirm khi còn thiếu, refresh khi lệch trạng thái, map lỗi nghiệp vụ sang tiếng Việt, offline/loading/disabled states.
- Ticket/Issue ID: APP-PHASE7
- Scope: `app/android` - chỉ gọi RPC/REST GI đã audit từ scanner PWA + migrations; không đổi DB/RPC/API/status contract; không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/network/NetworkClient.kt` (thêm `HttpMethod.PATCH`)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/GoodsIssue.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/gi/GoodsIssueErrorMapper.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/gi/GoodsIssueRepository.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/goodsissue/{GoodsIssueListUiState,GoodsIssueListViewModel,GoodsIssueListScreen,GoodsIssuePickUiState,GoodsIssuePickViewModel,GoodsIssuePickScreen}.kt`
  - `core/di/AppContainer.kt`, `core/navigation/AppDestination.kt`, `core/navigation/AppNavHost.kt`
  - `feature/home/HomeViewModel.kt`, `feature/home/HomeScreen.kt`
  - `app/android/app/src/test/java/vn/delfi/xcloudwms/data/gi/GoodsIssueErrorMapperTest.kt`, `.../domain/model/GoodsIssueModelTest.kt`
  - `app/prompts/prompt_map.md`
- Tests run:
  - `./gradlew :app:compileDevDebugKotlin :app:compileDevDebugUnitTestKotlin` ✅
  - `./gradlew :app:testDevDebugUnitTest` ✅ (full suite, gồm GI mapper + model tests)
  - `./gradlew :app:assembleDevDebug` ✅
  - `./gradlew :app:lintDevDebug` ❌ chỉ do 3 lỗi `RestrictedApi` có sẵn ở `MainActivity.dispatchKeyEvent` (không liên quan GI; file mới GI không có lint finding)
- Commit message: `feat(app-gi): implement goods issue picking flow`
- Notes/Risks:
  - Ghi `gi_details` (PATCH/INSERT PostgREST theo RLS `gi.scan`/`gi.update` như scanner) vì không có RPC pick chuyên dụng; đã thêm `HttpMethod.PATCH` (Android HttpURLConnection nền OkHttp cho phép PATCH).
  - Pick NONE: cập nhật `picked_quantity` của detail reserved (không tăng `quantity` để tránh trigger `gi_reserved_qty_exceeds_line_needed`), chỉ insert mới khi còn sức chứa dòng.
  - `rpc_gi_submit` tự chuyển COMPLETED khi mọi dòng đủ, ngược lại PICKED; nút "Xuất kho" gọi `rpc_gi_complete` cho phần đã pick. Tồn kho do backend quyết định, client chỉ optimistic + refresh.
  - Chưa hỗ trợ quét nguyên LPN (`rpc_gi_scan_whole_lpn`) và chọn vị trí pick thủ công như PWA — phạm vi phase sau.

## 2026-06-01 17:30 — Phase 6 PA put-away scan session

- Prompt summary: Triển khai module PA (Put-away/Internal transfer) native cho `app/android` (prompt 06), parity scanner PWA `PaPutawayClient.tsx`: tạo phiên DRAFT (`rpc_pa_start_session`), stepper quét vị trí nguồn → sản phẩm/serial/lot → số lượng → vị trí đích, thêm/xoá dòng nháp (`rpc_pa_add_line`/`rpc_pa_delete_line`), live stock check (`rpc_pa_live_stock_check`), submit (`rpc_pa_submit` + hậu xử lý `rpc_process_inventory_threshold_events`). Validation UX, chặn trùng serial/lot/none phía client, map mã lỗi nghiệp vụ sang tiếng Việt, chống double-submit, offline banner.
- Ticket/Issue ID: APP-PHASE6
- Scope: `app/android + docs` - chỉ gọi RPC/REST PA đã audit, không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/Putaway.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/putaway/**` (`PutawayRepository`, `PutawayErrorMapper`, `PutawayLineValidator`)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/putaway/**` (UiState/ViewModel/Screen)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/di/AppContainer.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/navigation/{AppDestination,AppNavHost}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/home/{HomeViewModel,HomeScreen}.kt`
  - `app/android/app/src/test/java/vn/delfi/xcloudwms/data/putaway/**`
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `./gradlew :app:testDevDebugUnitTest` ✅ pass (PutawayErrorMapper + PutawayLineValidator)
  - `./gradlew :app:assembleDevDebug` ✅ pass
  - `git -C app diff --check` ✅ no whitespace error
- Commit message: `feat(app-pa): implement putaway scan session flow`
- Notes/Risks:
  - Backend `rpc_pa_submit` không nhận idempotency request id (parity scanner). Chống double-commit bằng in-flight guard + chặn submit khi phiên không còn DRAFT; chưa có dedupe phía server.
  - Native dùng kho hiện tại từ session làm `p_warehouse_id` (không thêm warehouse selector toàn cục) → khác scanner ở chỗ scanner chọn kho ngay trong màn PA.
  - Serial/lot resolution truy vấn trực tiếp `serials`/`lots`/`stock_summary` qua PostgREST (giống scanner); phụ thuộc RLS cho phép đọc các bảng này với quyền PA user.
  - Parse JSON nằm trong repo (org.json) nên chỉ verify qua build; unit test phủ logic thuần (error map + validator).
  - Menu Home gate PA bằng `inventory.scan` (đồng bộ các module hiện có); backend vẫn enforce `SCANNER_PA_CREATE`/`SCANNER_PA_SUBMIT`.

## 2026-06-01 14:40 — Phase 5 stock lookup (read-only)

- Prompt summary: Triển khai Stock Lookup cho `app/android` (prompt 05): quét/nhập mã → xem tồn read-only theo kho hiện tại qua RPC audited `rpc_traceability_lookup`. Result cards (match/summary/tồn theo vị trí), empty state, error/retry, offline banner; dùng lại scanner core + auth/warehouse context.
- Ticket/Issue ID: APP-PHASE5
- Scope: `app/android + docs` - chỉ gọi đọc RPC sẵn có, không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/StockLookup.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/stock/**` (`StockLookupRepository`, `StockRowFilter`, `LookupErrorMapper`)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/network/ConnectivityObserver.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/stocklookup/**` (Screen/ViewModel/UiState)
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/di/AppContainer.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/navigation/{AppDestination,AppNavHost}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/home/{HomeUiState,HomeViewModel,HomeScreen}.kt`
  - `app/android/app/src/main/AndroidManifest.xml` (thêm `ACCESS_NETWORK_STATE`)
  - `app/android/app/src/test/java/vn/delfi/xcloudwms/data/stock/**`
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `./gradlew -p app/android :app:testDevDebugUnitTest` ✅ pass (StockRowFilter + LookupErrorMapper)
  - `./gradlew -p app/android :app:assembleDevDebug` ✅ pass
  - `git -C app diff --check` ✅ no whitespace error
- Commit message: `feat(app-stock): add scanner stock lookup`
- Notes/Risks:
  - RPC `rpc_traceability_lookup` không nhận param kho; server scope theo `fn_my_warehouse_ids()`. App lọc hiển thị dòng theo kho hiện tại (view-filter, read-only) + switch "Xem tất cả kho được phân quyền" để "đổi kho → kết quả đổi".
  - Token chưa tự refresh ở tầng lookup: 401 → báo phiên hết hạn, đăng nhập lại.
  - Chưa render `events`/`active_lpns`/`lpn_contents_preview` (prompt không yêu cầu).
  - Test pure (filter + error map) do org.json không chạy trong unit test thường; parse verify qua build + manual.

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
