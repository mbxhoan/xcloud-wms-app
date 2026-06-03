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

## 2026-06-03 18:40 — Phase 9 IC inventory count

- Prompt summary: Triển khai module Inventory Count native cho `app/android` (prompt 09), parity scanner PWA `count/IcCountClient.tsx` + route `/app/count`: danh sách phiếu kiểm kê trong kho, mở phiếu xem snapshot/counted/diff từng dòng, auto `rpc_ic_start_counting`, đếm theo NONE (`rpc_ic_update_count` tuyệt đối) / LOT (`rpc_check_lot_scan` → `rpc_ic_add_detail`) / SERIAL (`rpc_check_serial_scan` → `rpc_ic_add_detail` qty=1, chặn trùng), kết thúc kiểm kê (`rpc_ic_complete` action COMPLETE_ONLY + ghi chú), đếm mù (ẩn tồn), refresh khi lệch trạng thái, map lỗi tiếng Việt.
- Ticket/Issue ID: APP-PHASE9
- Scope: `app/android` - chỉ gọi RPC/REST IC đã audit từ scanner PWA + migrations; không đổi DB/RPC/API/status contract; không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/InventoryCount.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/ic/{InventoryCountRepository,InventoryCountErrorMapper}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/inventorycount/{InventoryCountListUiState,InventoryCountListViewModel,InventoryCountListScreen,InventoryCountUiState,InventoryCountViewModel,InventoryCountScreen}.kt`
  - `core/di/AppContainer.kt`, `core/navigation/AppDestination.kt`, `core/navigation/AppNavHost.kt`
  - `feature/home/HomeViewModel.kt`, `feature/home/HomeScreen.kt`
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅
- Commit message: `feat(app-ic): implement inventory count scan flow`
- Notes/Risks:
  - An toàn tồn kho: kết thúc dùng `rpc_ic_complete(p_id,'COMPLETE_ONLY',note)` — chỉ đóng phiếu, KHÔNG post ledger. Trigger điều chỉnh tồn chỉ chạy khi `action='ADJUST'`; cân bằng/duyệt để webapp (đúng yêu cầu MVP).
  - NONE: `rpc_ic_update_count` đặt số đếm tuyệt đối (current + delta). LOT/SERIAL: `rpc_ic_add_detail` (gọi KHÔNG kèm `p_ic_line_id` để khớp signature gốc; resolve dòng theo product+location). Chặn trùng serial: session set + backend `uniq_ic_details_header_serial`/`ic_conflict`.
  - List theo kho hiện tại (warehouse_id + status DRAFT/CREATED/IN_PROGRESS), không lọc assigned (đồng bộ list GR/GI native). Không dùng `rpc_scanner_list_ic_headers` để tránh phụ thuộc gate `login_app=SCANNER`.
  - Đếm mù (count_mode BLIND) ẩn tồn hệ thống + lệch; ngược lại hiển thị snapshot/counted/diff.
  - Chưa hỗ trợ quét LPN (`rpc_ic_scan_lpn`) và duyệt điều chỉnh ACID (`IC_APPROVE_ADJUSTMENT`) — phạm vi phase/webapp sau.

## 2026-06-03 18:10 — Refactor home UI theo scanner Menu.png

- Prompt summary: Refactor lại UI trang chủ app native theo `scanner/docs/UI-12/Menu.png` (greeting card xanh + lưới module dạng icon tile), vẫn giữ phần thông tin phần cứng và test quét nhanh như hiện tại.
- Ticket/Issue ID: (none)
- Scope: `app/android` - chỉ đổi layout/visual màn Home native; không đổi DB/RPC/API/status contract, không đổi data source, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/home/HomeScreen.kt` (viết lại: GreetingCard + ModuleGrid icon tile + QuickAccessRow giữ Quét thử/Phần cứng + context + đổi kho/đăng xuất)
  - `app/android/gradle/libs.versions.toml`, `app/android/app/build.gradle.kts` (thêm `androidx.compose.material:material-icons-extended`)
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅
- Commit message: `style(app-home): refactor home menu ui to match scanner design`
- Notes/Risks:
  - Card "Số lượng công việc" (Cần xử lý/Đang xử lý/Completed) trong design KHÔNG được dựng vì app native chưa có nguồn dữ liệu đếm phiếu theo trạng thái; không bịa số. Có thể bổ sung khi có RPC/aggregate đếm phiếu — phase sau.
  - Module tile lấy từ `moduleShortcuts` (permission-gated): tile có `actionKey` thì bấm được + style nổi; tile chưa wire (Kiểm kê, Đơn vị chứa) hiển thị mờ + nhãn "Sắp có".
  - Thêm `material-icons-extended` làm tăng kích thước bundle (chưa bật minify); chấp nhận cho bản nội bộ.
  - Bottom tab bar + nút scan nổi ở giữa như Menu.png là shell của scanner PWA, chưa đưa vào native phase này (Home vẫn dùng `XcloudScaffold` cuộn dọc).

## 2026-06-03 17:40 — Phase 8 GR goods receipt receiving

- Prompt summary: Triển khai module Goods Receipt Receiving native cho `app/android` (prompt 08), parity scanner PWA `inbound/GrReceiveClient.tsx` + route `/app/inbound`: danh sách phiếu nhập trong kho (CREATED/RECEIVING/RECEIVED), mở phiếu xem tiến độ expected/received từng dòng, auto `rpc_gr_start_receiving`, nhận hàng theo location/product/lot/serial/qty (NONE: qty+location; LOT: `rpc_gr_resolve_lot_scan`+NSX/HSD nếu require/FEFO; SERIAL: `rpc_gr_resolve_serial_scan` qty=1, chặn trùng serial trong phiên), ghi `gr_details` qua PostgREST, chốt nhận (`rpc_gr_submit_receive`) + hoàn tất (`rpc_gr_complete`) với confirm khi còn thiếu, refresh khi lệch trạng thái, map lỗi nghiệp vụ sang tiếng Việt, offline/loading/disabled states.
- Ticket/Issue ID: APP-PHASE8
- Scope: `app/android` - chỉ gọi RPC/REST GR đã audit từ scanner PWA + migrations; không đổi DB/RPC/API/status contract; không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/domain/model/GoodsReceipt.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/gr/{GoodsReceiptRepository,GoodsReceiptErrorMapper}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/goodsreceipt/{GoodsReceiptListUiState,GoodsReceiptListViewModel,GoodsReceiptListScreen,GoodsReceiptReceiveUiState,GoodsReceiptReceiveViewModel,GoodsReceiptReceiveScreen}.kt`
  - `core/di/AppContainer.kt`, `core/navigation/AppDestination.kt`, `core/navigation/AppNavHost.kt`
  - `feature/home/HomeViewModel.kt`, `feature/home/HomeScreen.kt`
  - `app/prompts/prompt_map.md`, `docs/commit_prompt_map.md`
- Tests run:
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅
  - `cd app/android && ./gradlew :app:lintDevDebug` ❌ chỉ do 7 lỗi có sẵn (`MissingPermission` ở `DeviceHardwareRepository`, `RestrictedApi` ở `MainActivity.dispatchKeyEvent`); file mới GR không có lint finding nào.
- Commit message: `feat(app-gr): implement goods receipt receiving flow`
- Notes/Risks:
  - Không có RPC "release stock" riêng: stock post khi `rpc_gr_complete` (ledger GR_IN). UI 2 nút: `Chốt nhận hàng` (`rpc_gr_submit_receive` → RECEIVED/COMPLETED) + `Hoàn tất nhập kho` (`rpc_gr_complete` → COMPLETED). Client optimistic + refresh; tồn do backend quyết định.
  - List scope = toàn bộ phiếu nhận được trong kho hiện tại (warehouse_id + status), không lọc theo `assigned_scanner_user_id` (đồng bộ hành vi list GI native).
  - Vị trí nhập chọn từ `locations` của kho phiếu (đảm bảo location thuộc đúng kho); ghi `gr_details` gồm cả `manufacture_date` + `manufactured_date` (legacy) để tương thích schema.
  - Không insert thẳng `serials`/`lots`; chỉ qua RPC resolve (SECURITY DEFINER). Chặn trùng serial trong phiên ở client + backend.
  - NSX/HSD nhập dạng text `YYYY-MM-DD`, validate client (bắt buộc theo require_*/FEFO, HSD≥NSX, HSD không quá khứ); backend vẫn enforce.
  - Chưa hỗ trợ luồng tạo phiếu PDA-initiated (`rpc_gr_pda_*`) — phạm vi phase sau.

## 2026-06-03 17:02 — Add PDA hardware diagnostics screen

- Prompt summary: Ở trang chủ cần có phần `Thông tin phần cứng`; bấm vào sẽ mở màn hiển thị toàn bộ thông tin thiết bị mà app Android có thể đọc được như model, device name, số điện thoại nếu có, IMEI, serial, Android version, IP, MAC, Bluetooth và các trạng thái liên quan.
- Ticket/Issue ID: (none)
- Scope: `app/android` - chỉ thêm màn thông tin phần cứng native, route từ trang chủ và quyền đọc thiết bị liên quan; không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/AndroidManifest.xml`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/di/AppContainer.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/navigation/{AppDestination,AppNavHost}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/home/HomeScreen.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/device/DeviceHardwareRepository.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/deviceinfo/{DeviceHardwareInfoUiState,DeviceHardwareInfoViewModel,DeviceHardwareInfoScreen}.kt`
  - `app/prompts/prompt_map.md`
- Tests run:
  - `cd app && git diff --check` ✅
  - `cd app/android && GRADLE_USER_HOME=/private/tmp/xcloud-gradle ./gradlew :app:assembleDevDebug` ✅
- Commit message: `feat(app-device): add pda hardware diagnostics screen`
- Notes/Risks:
  - Màn mới chia dữ liệu theo nhóm: nhận dạng thiết bị, hệ điều hành, điện thoại/SIM, mạng/IP/MAC, Bluetooth, pin/bộ nhớ và thông tin app.
  - App sẽ xin thêm quyền `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `BLUETOOTH_CONNECT` khi cần để đọc sâu hơn các trường như số điện thoại, IMEI, serial phần cứng, tên/MAC Bluetooth.
  - Trên Android 10+ nhiều định danh không reset như IMEI/serial/MAC thật có thể vẫn bị hệ điều hành chặn dù đã cấp quyền; màn hình sẽ hiện rõ trạng thái bị chặn thay vì giả dữ liệu.

## 2026-06-03 16:24 — Fix PM85 wedge capture and keyboard toggle

- Prompt summary: Người dùng đã bật EmKit > ScanSettings trên PM85 nhưng mã chỉ vào ô giả lập nhập tay, không vào ô hiển thị kết quả quét; đồng thời cần bật/tắt bàn phím mềm khi chạm vào ô quét để vừa dùng cò quét PDA vừa xem lại mã đã nhận ngay trên máy.
- Ticket/Issue ID: (none)
- Scope: `app/android` - chỉ chỉnh scanner test flow native và adapter pipeline cho PM85; không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/scanner/{ScannerManager,DefaultScannerManager}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/scannertest/{ScannerTestUiState,ScannerTestViewModel,ScannerTestScreen}.kt`
  - `app/prompts/prompt_map.md`
- Tests run:
  - `cd app && git diff --check` ✅
  - `cd app/android && GRADLE_USER_HOME=/private/tmp/xcloud-gradle ./gradlew :app:assembleDevDebug` ✅
- Commit message: `fix(app-scan): support pda wedge capture and keyboard toggle`
- Notes/Risks:
  - Màn `Quét thử mã` giờ có một ô nhận quét PDA riêng, giữ focus sẵn và mặc định không bật bàn phím mềm; ô `Kết quả quét` chỉ còn vai trò hiển thị mã sau khi pipeline xử lý xong.
  - Nếu PM85 bắn theo kiểu key event, app sẽ huỷ submit tạm từ ô nhận quét để tránh nhận mã dở hoặc nhận trùng; nếu PM85 bắn thẳng text vào ô focus, app sẽ tự submit sau khi chuỗi ổn định hoặc khi người dùng bấm nút nhận mã trong ô.
  - Với PM85 nên ưu tiên `Keyboard Event` hoặc `Wedge` để test nhanh; nếu firmware vẫn cư xử như nhập text thuần vào ô focus thì màn này đã hỗ trợ, còn nếu muốn ổn định lâu dài hơn nên cân nhắc `Intent Broadcast` / `Custom Intent`.

## 2026-06-03 15:45 — Fix PDA home flow and add scan demo screen

- Prompt summary: App Android đã đăng nhập thành công nhưng cần vào thẳng giao diện chính không phải chọn kho, màn chính trên PDA đang giống bị cứng vì không scroll/click được gì hữu ích, và cần có màn quét thử QR/mã vạch để dùng nút cứng của PDA quét như app mẫu.
- Ticket/Issue ID: (none)
- Scope: `app/android` - chỉ chỉnh flow native app, không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/auth/SupabaseAuthRepository.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/ui/components/XcloudScaffold.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/home/{HomeUiState,HomeViewModel,HomeScreen}.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/scannertest/{ScannerTestUiState,ScannerTestViewModel,ScannerTestScreen}.kt`
  - `app/prompts/prompt_map.md`
- Tests run:
  - `cd app/android && GRADLE_USER_HOME=/private/tmp/xcloud-gradle ./gradlew :app:assembleDevDebug` ✅
  - `cd app && git diff --check` ✅
- Commit message: `fix(app-home): streamline pda entry and scan demo flow`
- Notes/Risks:
  - Native app giờ sẽ tự lấy kho đầu tiên được phân quyền nếu chưa có kho đã lưu cục bộ; user vẫn có thể đổi kho lại từ màn chính.
  - PM85 không bắt buộc phải có SDK để test scan bước đầu; có thể chạy bằng Keyboard Event hoặc Intent Broadcast trong EmKit/ScanSettings.
  - Nếu PDA không phát được dữ liệu vào app sau khi bấm cò, bước tiếp theo là cấu hình đúng `Scanner On`, `Wedge`, `Terminator`, hoặc `Custom Intent` trên thiết bị.

## 2026-06-03 15:10 — Dev auto-login shortcut for PDA test

- Prompt summary: Tạm bỏ qua phần cấu hình thông tin kết nối trên app native, vào thẳng giao diện login và dùng sẵn tài khoản test `scan / Xcloudwms@123` để test nhanh trên PDA.
- Ticket/Issue ID: (none)
- Scope: `app/android` - chỉ thêm bootstrap dev-only cho login native; không đổi DB/RPC/API/status contract, không sửa `scanner/`, `webapp/`, `supabase/`.
- Main files changed:
  - `app/android/app/build.gradle.kts`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/config/AppConfig.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/navigation/AppNavHost.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/login/LoginScreen.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/login/LoginUiState.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/login/LoginViewModel.kt`
  - `app/android/local.properties` (local-only, không commit)
- Tests run:
  - `cd app/android && GRADLE_USER_HOME=/private/tmp/xcloud-gradle ./gradlew :app:assembleDevDebug` ✅
  - `cd app && git diff --check` ✅
- Commit message: `fix(app-auth): add dev auto-login shortcut for pda testing`
- Notes/Risks:
  - Shortcut này chỉ hoạt động khi bản `dev` có cấu hình local tương ứng; staging/prod mặc định không tự điền và không auto-login.
  - `local.properties` chứa thông tin local-only, không được commit.
  - Nếu project Supabase dev đổi hoặc user `scan` không tồn tại ở tenant hiện tại thì app vẫn dừng ở login và hiện lỗi backend.

## 2026-06-03 15:05 — Fix native Android login bootstrap on PDA

- Prompt summary: Khi cắm PDA chạy app bằng Android Studio, Android Studio báo lỗi `Error starting live edit`, còn app trên PDA dừng ở màn đăng nhập. Cần xử lý để app native bootstrap kết nối đúng hơn trên thiết bị thật.
- Ticket/Issue ID: (none)
- Scope: `app/android + app/docs` - không đổi DB/RPC/API/status contract; chỉ vá cơ chế nạp cấu hình kết nối mặc định và cập nhật tài liệu chạy PDA.
- Main files changed:
  - `app/android/app/build.gradle.kts`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/config/AppConfig.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/di/AppContainer.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/core/storage/AppPreferences.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/data/session/SessionRepository.kt`
  - `app/android/app/src/main/java/vn/delfi/xcloudwms/feature/login/LoginViewModel.kt`
  - `app/docs/08_BUILD_DEPLOY_TEST_A_TO_Z.md`
  - `app/prompts/prompt_map.md`
- Tests run:
  - `cd app/android && GRADLE_USER_HOME=/private/tmp/xcloud-gradle ./gradlew :app:assembleDevDebug` ✅ pass
  - `cd app && git diff --check` ✅
- Commit message: `fix(app-auth): bootstrap default connection config for pda builds`
- Notes/Risks:
  - Popup `Error starting live edit` là lỗi tooling của Android Studio/Compose Live Edit, không phải lỗi business của app; cần xử lý ở IDE hoặc tắt Live Edit khi chạy PDA.
  - Build hiện còn warning unchecked cast trong `SupabaseAuthRepository.kt` từ phase auth cũ, nhưng không chặn assemble và không nằm trong phạm vi fix PDA lần này.
  - App native auth vẫn bám audit cũ: không hard-code host/key trong source. URL/key chỉ được nạp từ `local.properties`, env hoặc cấu hình người dùng lưu trên máy.

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
