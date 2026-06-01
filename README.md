# Xcloud WMS Native Android App (`/app`)

Bộ tài liệu này dùng để tạo một repo/folder native Android mới trong workspace `xcloud-wms-workspace/app`.

Mục tiêu: xây dựng ứng dụng Android mobile-first tối ưu cho PDA scanner, vận hành tương đương app scanner PWA hiện tại của Xcloud WMS, nhưng có khả năng tích hợp tốt hơn với phần cứng scan, SDK vendor, trigger vật lý, offline-lite, device license và quy trình build APK/AAB.

## Tư duy triển khai

Không viết lại nghiệp vụ kho từ đầu. Native app chỉ là lớp client mới cho nhân viên kho. Toàn bộ business rule quan trọng vẫn phải nằm ở backend/API/DB transaction hiện tại.

Native app phải giữ parity với scanner PWA:

- Đăng nhập đúng tenant/org.
- Chọn kho đang thao tác.
- Menu theo role/permission.
- Scan location/product/lot/serial giống PWA.
- GR/GI/PA/IC cập nhật đúng status flow.
- Không âm kho, không trùng serial, không bypass permission.
- Lỗi phải hiển thị rõ, không làm người dùng tưởng app bị đơ.

## Cách dùng bộ tài liệu này

1. Copy nguyên folder `app/` vào root workspace:

```bash
xcloud-wms-workspace/
  webapp/
  scanner/
  supabase/
  app/
```

2. Mở `app/docs/00_APP_NATIVE_ANDROID_MASTER_PLAN.md` để hiểu roadmap.
3. Giao `app/prompts/00_ORCHESTRATOR_PROMPT.md` cho Codex/Claude trước.
4. Làm lần lượt các prompt theo thứ tự trong `app/prompts/`.
5. Sau mỗi prompt, yêu cầu agent cập nhật `app/prompts/prompt_map.md`.
6. Trước khi test thật trên PDA, dùng checklist trong `app/checklists/`.

## Stack đề xuất

- Language: Kotlin.
- UI: Jetpack Compose.
- Architecture: MVVM + Clean-ish modular layers.
- Network: Retrofit/OkHttp hoặc Ktor Client.
- Local storage: Room + DataStore.
- DI: Hilt/Koin.
- Background: WorkManager.
- Barcode/QR fallback: CameraX + ML Kit/ZXing nếu không có hardware scanner.
- PDA scan priority: Hardware scanner SDK/Broadcast/Keyboard wedge trước, camera fallback sau.

## Nguyên tắc bắt buộc

- Native app không gọi trực tiếp Supabase/Postgres nếu hệ thống scanner PWA đang gọi qua Node REST API.
- Không tự tính stock ở client. Client chỉ hiển thị, validate UX sơ bộ, rồi gửi request để backend commit transaction.
- Mọi flow commit kho phải idempotent hoặc có request id để tránh scan/tap lặp.
- Mọi màn hình thao tác phải có loading, disabled button, retry rõ ràng.
- Mọi scan phải có beep/vibrate/visual feedback.
- Mọi lỗi nghiệp vụ phải hiển thị ngắn gọn và có cách sửa: scan lại, đổi location, sửa qty, refresh tồn.

## Tài liệu chính

- `docs/00_APP_NATIVE_ANDROID_MASTER_PLAN.md`: kế hoạch tổng thể.
- `docs/01_PRODUCT_SCOPE_PARITY_WITH_SCANNER_PWA.md`: scope parity với scanner PWA.
- `docs/02_TECH_STACK_AND_ARCHITECTURE.md`: kiến trúc Android native.
- `docs/03_PDA_SCANNER_SDK_INTEGRATION.md`: cách tích hợp PDA SDK/hardware scan.
- `docs/04_API_CONTRACT_AND_BACKEND_ADAPTER.md`: hợp đồng API cần có.
- `docs/05_UI_UX_MOBILE_FIRST_GUIDELINES.md`: UI/UX mobile-first cho kho.
- `docs/06_OFFLINE_SYNC_ERROR_HANDLING.md`: offline-lite, retry, idempotency.
- `docs/07_SECURITY_AUTH_LICENSE_DEVICE.md`: bảo mật, session, device/license.
- `docs/08_BUILD_DEPLOY_TEST_A_TO_Z.md`: hướng dẫn build/deploy/test từ A-Z.
- `docs/09_QA_UAT_CHECKLIST.md`: checklist QA/UAT.
- `docs/10_BACKLOG_PHASES.md`: backlog triển khai theo phase.

## Output mong muốn khi agent triển khai

Mỗi lần agent/code assistant hoàn thành một task phải trả về:

- Nguyên nhân (nếu có)
- File đã sửa.
- Lệnh đã chạy.
- Rủi ro còn lại.
- Checklist verify.
- 1 commit message duy nhất.
- Entry mới trong `app/prompts/prompt_map.md`.
