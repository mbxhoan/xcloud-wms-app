# 00 — Native Android App Master Plan

## 1. Mục tiêu

Xây dựng ứng dụng Android native cho Xcloud WMS để thay thế/bổ sung scanner PWA trong môi trường kho dùng PDA. App phải vận hành y chang scanner PWA hiện tại về nghiệp vụ, nhưng tốt hơn ở các điểm native:

- Bắt sự kiện nút scan vật lý.
- Tích hợp SDK của PDA vendor.
- Scan barcode/QR nhanh, ít lỗi focus input.
- Beep/vibrate/visual feedback ổn định.
- Có device identity/license tốt hơn PWA.
- Cài APK/AAB cho thiết bị công ty.
- Có offline-lite/cache để giảm gián đoạn khi Wi-Fi kho yếu.

## 2. Phạm vi MVP

MVP không làm app quản trị đầy đủ. App chỉ dành cho nhân viên kho/PDA:

1. Auth & Context
   - Login.
   - Resolve tenant/org.
   - Chọn kho đang thao tác.
   - Menu theo permission.

2. Scan Core
   - Scanner abstraction.
   - Hardware scan input.
   - Keyboard wedge input.
   - Camera fallback.
   - Beep/vibrate/error feedback.

3. Warehouse Operations
   - GR Receiving: nhận hàng, scan location/product/lot/serial, qty, release/complete theo API hiện có.
   - GI Picking: danh sách phiếu được assign, scan location/product/serial, update picked qty, complete/ship.
   - PA Put-away/Internal Transfer: scan from location, product, qty, to location, save draft, submit transaction.
   - IC Counting: danh sách phiếu kiểm kê, scan/count, finish, sync.
   - Stock Lookup: scan product/location/serial để xem tồn khả dụng.

4. Reliability
   - Loading/disabled states.
   - Idempotency key cho submit.
   - Retry cho GET/list/detail.
   - Không queue commit kho nếu backend chưa hỗ trợ idempotency.

## 3. Phạm vi không làm trong MVP

- Không viết lại toàn bộ webapp admin.
- Không sửa business rule stock ở client.
- Không offline full transaction khi chưa có backend conflict policy.
- Không hard-code vendor PDA duy nhất.
- Không tự tạo schema riêng nếu API hiện tại đã có.

## 4. Roadmap đề xuất

### Phase 0 — Discovery & Parity Audit

Mục tiêu: hiểu scanner PWA đang làm gì, route nào, endpoint nào, payload nào.

Checklist:

- Audit folder `scanner/`.
- Ghi lại navigation map.
- Ghi lại API map.
- Ghi lại UI states/error states.
- Ghi lại permission check.
- Ghi lại cách PWA xử lý scan input.
- Ghi lại flow GR/GI/PA/IC hiện tại.

Output:

- `specs/scanner_pwa_parity_matrix.md` được cập nhật.
- `specs/api_endpoints_draft.md` được cập nhật bằng endpoint thật.

### Phase 1 — Android Project Foundation

Mục tiêu: app build được, chạy được trên emulator và ít nhất 1 máy Android thật.

Checklist:

- Tạo Android project Kotlin + Compose.
- Tạo build variants: dev/staging/prod.
- Tạo config base URL theo environment.
- Tạo theme, typography, colors theo scanner PWA.
- Tạo navigation shell.
- Tạo logger an toàn.
- Tạo network client.

Output:

- App mở được màn login.
- Build debug APK thành công.

### Phase 2 — Auth, Tenant, Warehouse Context

Mục tiêu: user login thật, chọn kho thật, permission menu đúng.

Checklist:

- Login API.
- Secure token storage.
- Refresh token/session handling.
- Load user profile.
- Load warehouses assigned.
- Set default warehouse.
- Logout/invalidate local session.

Output:

- User login/logout được.
- Vào home thấy đúng kho và menu.

### Phase 3 — Scanner Core & PDA Integration

Mục tiêu: mọi scan đi qua một pipeline duy nhất.

Checklist:

- `ScannerManager` interface.
- `KeyboardWedgeScannerAdapter`.
- `BroadcastScannerAdapter`.
- `CameraScannerAdapter` fallback.
- Sound/vibration feedback.
- Scan mode: LOCATION, PRODUCT, LOT, SERIAL, GENERIC.
- Duplicate scan debounce.

Output:

- Test scan trên emulator bằng nhập tay.
- Test scan trên PDA bằng hardware trigger.

### Phase 4 — Operation Modules

Triển khai theo thứ tự ít rủi ro:

1. Stock Lookup.
2. PA Put-away/Internal Transfer.
3. GI Picking.
4. GR Receiving.
5. IC Counting.

Lý do: Stock Lookup giúp kiểm tra API/scanner; PA có flow mobile rõ; GI/GR ảnh hưởng nhiều tới stock; IC cần nhiều edge case.

### Phase 5 — Offline-lite & Stability

Mục tiêu: giảm gián đoạn nhưng không làm sai stock.

Checklist:

- Cache master data lightweight.
- Retry GET/list/detail.
- Local draft cho PA/IC nếu chưa submit.
- Idempotency key cho submit.
- Conflict handling.
- Network status banner.

### Phase 6 — Device License & Enterprise Rollout

Mục tiêu: kiểm soát thiết bị được phép dùng app scanner.

Checklist:

- Device registration API.
- Device fingerprint.
- License check at login/resume.
- Max scanners/subscription enforcement.
- Block/warn mode theo backend config.
- App version check.

### Phase 7 — QA, UAT, Release

Checklist:

- Unit tests ViewModel/UseCase.
- Integration tests API fake.
- Manual PDA test.
- Regression GR/GI/PA/IC.
- Release signing.
- Internal APK distribution.
- Version notes.

## 5. Definition of Done tổng thể

- App build được APK debug/release.
- Login thật với backend staging/prod.
- User chỉ thấy đúng tenant/warehouse/permission.
- Scan hardware PDA hoạt động ổn định.
- GR/GI/PA/IC parity với scanner PWA.
- Không âm kho, không trùng serial, không sai status flow.
- Có checklist test trên ít nhất 2 loại thiết bị: Android phone thường và PDA thật.
- Có hướng dẫn build/deploy cho người không chuyên native.
