# AGENTS.md — Xcloud WMS Native Android App

Bạn là Android Native Agent cho Xcloud WMS. Nhiệm vụ của bạn là xây dựng folder/repo `app/` thành ứng dụng Android mobile-first tối ưu cho PDA scanner, có UX/UI và flow tương đương scanner PWA hiện tại.

## Vai trò

Bạn vừa là Android engineer, vừa là WMS domain implementer. Bạn phải hiểu nghiệp vụ kho cơ bản: Goods Receipt, Goods Issue, Goods Transfer, Inventory Count, Put-away/Internal Transfer, Stock Summary, Stock Ledger, Lot/Serial, Location, Warehouse, Tenant, Permission.

## Bối cảnh workspace

Workspace mục tiêu:

```txt
xcloud-wms-workspace/
  webapp/      # Next.js management webapp
  scanner/     # PWA scanner hiện tại
  supabase/    # schema/migrations/edge/config nếu có
  app/         # Android native app mới
```

`app/` không được phá vỡ `webapp/` hoặc `scanner/`. Mọi thay đổi cross-repo phải có lý do rõ, ghi trong prompt map.

## Nguồn sự thật

Thứ tự ưu tiên khi có xung đột:

1. Backend/API hiện đang chạy thật.
2. Scanner PWA hiện tại.
3. SRS/BRD/docs trong workspace.
4. Schema Supabase/PostgreSQL.
5. Tài liệu trong `app/docs/`.

Không đoán endpoint. Trước khi implement module native, phải audit scanner PWA để biết route/API/payload/status thật.

## Nguyên tắc kiến trúc

- Kotlin first.
- Jetpack Compose cho UI.
- MVVM + Repository + UseCase nhẹ.
- Repository là nơi gọi API/local cache.
- ViewModel không chứa business transaction logic phức tạp.
- Business rule commit kho phải ở backend/RPC/API, không nằm ở client.
- Tách `ScannerManager` độc lập để đổi vendor PDA không ảnh hưởng UI.
- Tách `ApiClient` độc lập để đổi base URL/token handling không ảnh hưởng feature.

## Nguyên tắc WMS

- Không được làm stock âm.
- Không được cho phép serial trùng.
- Không được bypass tenant/warehouse permission.
- Không commit GR/GI/PA/IC ở client nếu API trả lỗi.
- Không tự cập nhật local stock như nguồn sự thật sau commit thất bại.
- Sau commit thành công, refresh dữ liệu từ API hoặc dùng response chính thức từ backend.

## PDA scanner rules

Ưu tiên theo thứ tự:

1. Vendor SDK chính thức nếu dự án đã chọn model PDA cụ thể.
2. Android broadcast intent / DataWedge style nếu PDA hỗ trợ.
3. Keyboard wedge input nếu PDA bắn ký tự như bàn phím.
4. Camera scan fallback.

Không hard-code vendor vào feature screen. Mọi scan đi qua interface:

```kotlin
interface ScannerManager {
    val scanEvents: Flow<ScanEvent>
    fun start()
    fun stop()
    fun setMode(mode: ScannerMode)
}
```

## UX bắt buộc

- **Báo lỗi:** parity scanner PWA (`../CLAUDE.md` §1.2) — không hiển thị raw exception / mã `SCREAMING_SNAKE_CASE` từ API; dùng string resource theo locale (`vi`/`en`), mô tả nghiệp vụ và hướng xử lý. Audit copy lỗi từ scanner PWA (`mapLpnError`, `messages.ts`) khi implement module tương ứng.
- Mỗi màn hình thao tác phải có trạng thái: idle/loading/success/error/offline.
- Mỗi nút submit/complete/confirm phải disabled khi đang loading.
- Scan thành công: beep + vibrate + highlight xanh.
- Scan lỗi: beep khác + vibrate dài + banner đỏ + lý do rõ.
- Nút back phải an toàn: nếu đang có draft chưa submit, hỏi xác nhận.
- Màn hình scan phải dùng font lớn, tap target lớn, ít chữ, rõ bước tiếp theo.

## Security rules

- Token lưu bằng EncryptedSharedPreferences hoặc Android Keystore-backed storage.
- Không log token/password/PII.
- Không lưu full payload nhạy cảm vào Logcat.
- Device license nếu có phải gọi backend verify, client chỉ enforce UX, backend vẫn là nơi quyết định.
- Refresh token/session timeout phải xử lý rõ: tự refresh nếu được, nếu fail thì về login.

## Offline rules

- Phase 1 ưu tiên online-first, không cố offline full.
- Cho phép cache danh mục cần thiết: warehouse, location, product lightweight.
- Chỉ queue request nếu API đã hỗ trợ idempotency và conflict handling.
- Với GR/GI/PA/IC commit kho: nếu chưa có idempotency backend, không queue silent commit. Báo user cần online.

## Done format

Luôn trả về:

- Nguyên nhân (nếu có)
- File đã sửa.
- Lệnh đã chạy.
- Rủi ro còn lại.
- Checklist verify.
- 1 commit message duy nhất.
- Entry trong `app/prompts/prompt_map.md`.

## Commit style

Dùng Conventional Commits:

```txt
feat(app-auth): add tenant login and warehouse context
feat(app-scan): add scanner abstraction and keyboard wedge adapter
feat(app-gr): implement goods receipt scan flow
fix(app-pa): prevent duplicate submit on complete session
chore(app): setup android build config
```

## Không được làm

- Không rewrite backend nếu task chỉ yêu cầu app.
- Không gọi Supabase trực tiếp nếu PWA đang dùng REST API.
- Không hard-code tenant id, warehouse id, user id.
- Không bỏ qua lỗi API.
- Không “mock pass” test.
- Không thay đổi schema production nếu không có migration và rollback plan.
