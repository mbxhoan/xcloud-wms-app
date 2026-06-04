# 12 — Versioning & Rollback Policy

Chính sách đánh version và rollback cho native Android PDA app, phát hành nội bộ
(sideload / MDM), chưa qua Google Play.

Nguồn sự thật: `app/android/app/build.gradle.kts` (`versionCode`, `versionName`).

## 1. Hai con số version

Android có 2 trường, nằm trong `defaultConfig`:

| Trường | Vai trò | Ai thấy |
|---|---|---|
| `versionCode` (Int) | Số nguyên tăng dần, dùng để so sánh "mới hơn" | hệ thống / update logic |
| `versionName` (String) | Nhãn người đọc, ví dụ `1.4.0` | user / tester / support |

Flavor thêm hậu tố vào `versionName`: `-dev`, `-staging`. `prod` không hậu tố.
Ví dụ cùng versionName `1.4.0`: prod = `1.4.0`, staging = `1.4.0-staging`.

## 2. Quy tắc versionName (SemVer rút gọn)

Dạng `MAJOR.MINOR.PATCH`:

- MAJOR: thay đổi lớn/không tương thích flow nghiệp vụ kho hoặc đổi API contract bắt buộc.
- MINOR: thêm module/feature (ví dụ thêm flow IC), vẫn tương thích ngược.
- PATCH: fix bug, chỉnh UX nhỏ, không thêm feature.

Pre-release vẫn ở `0.x.y` cho tới khi go-live kho thật bản đầu → bump `1.0.0`.

## 3. Quy tắc versionCode

- `versionCode` PHẢI tăng nghiêm ngặt mỗi lần phát hành ra ngoài (kể cả hotfix).
- Không bao giờ dùng lại hoặc giảm `versionCode` đã phát hành — thiết bị sẽ từ chối "cài đè bằng bản thấp hơn".
- Đề xuất công thức ổn định, suy ra trực tiếp từ versionName:

  ```txt
  versionCode = MAJOR*10000 + MINOR*100 + PATCH
  ví dụ: 1.4.0 -> 10400 ; 1.4.1 -> 10401 ; 2.0.0 -> 20000
  ```

- Mỗi lần bump phải sửa CẢ `versionCode` và `versionName` trong `build.gradle.kts`, trong 1 commit riêng dạng `chore(app-release): bump version x.y.z`.

## 4. Quy trình release

1. Chốt scope bản phát hành.
2. Bump `versionCode` + `versionName`.
3. Chạy gate: `./gradlew test assembleRelease` (xem doc 11).
4. Build + ký bản `prod` (hoặc `staging` cho UAT).
5. Chạy smoke test (`checklists/smoke_test_checklist.md`) + QA report nếu là bản kho thật.
6. **Archive artifact bản này** (xem mục 5) trước khi rollout.
7. Phân phối qua MDM / trang download nội bộ, kèm release notes.
8. Ghi vào release log: versionName, versionCode, commit, ngày, người build.

## 5. Artifact archive (điều kiện để rollback được)

Rollback chỉ khả thi nếu CÒN GIỮ APK/AAB cũ. Bắt buộc:

- Lưu mỗi bản release ra (APK + AAB nếu có) vào kho artifact nội bộ (Drive/MDM/Nexus), đặt tên theo version:
  `xcloud-wms-prod-1.4.0-vc10400.apk`.
- Giữ tối thiểu **2 bản ổn định gần nhất**.
- Ghi lại `versionCode`/`versionName`/commit hash của từng bản.
- Keystore phải cố định giữa các bản (cùng identity) — nếu không, thiết bị không cho cài đè/rollback.

## 6. Rollback policy

### Khi nào rollback

Rollback ngay nếu bản mới có dấu hiệu blocker (cùng tiêu chí no-go của QA):

- Có thể âm kho / trùng serial.
- Sai tenant/warehouse isolation, user thấy data ngoài quyền.
- Complete/submit chạy 2 lần (commit trùng) khi double tap/scan nhanh.
- Auth/login hỏng hàng loạt, hoặc app crash khi mở.
- Hardware scan hỏng trên thiết bị PDA chính.

Bug nhỏ không blocker → ưu tiên hotfix tiến lên (PATCH) thay vì rollback.

### Cách rollback (sideload/MDM) — thực hiện theo thứ tự

1. **Đóng băng rollout**: ngừng phát bản mới cho thiết bị chưa cập nhật.
2. **Lấy artifact bản ổn định trước đó** từ kho archive.
3. **Vấn đề "versionCode thấp hơn"**: Android không cho cài đè bằng `versionCode` nhỏ hơn. Có 2 cách:
   - Khuyến nghị: phát hành lại bản code cũ nhưng **bump versionCode lên cao hơn bản lỗi** (ví dụ lỗi ở vc 10401 → build lại nội dung 10400 thành vc 10402, versionName `1.4.0-rollback`). Đây là "roll-forward to previous code".
   - Hoặc gỡ cài bản lỗi rồi cài bản cũ: `adb uninstall vn.delfi.xcloudwms` rồi cài lại — **mất data/local cache trên thiết bị**, chỉ dùng khi bắt buộc.
4. **Đẩy bản rollback** qua MDM/trang download; với thiết bị lẻ dùng:
   ```bash
   adb install -r xcloud-wms-prod-1.4.0-vc10402.apk
   ```
5. **Smoke test** lại trên 1 PDA mẫu (`smoke_test_checklist.md`).
6. **Thông báo**: báo tester/thủ kho version đang chạy + lý do rollback.
7. **Ghi log** rollback: bản lỗi, bản thay thế, nguyên nhân, người thực hiện, thời gian.

### Sau rollback

- Tạo issue cho root cause của bản lỗi.
- Fix trên nhánh, bump PATCH, qua lại full gate + QA report trước khi phát lại.
- Không tái sử dụng `versionCode` của bản lỗi.

## 7. Server-side compatibility (quan trọng cho WMS)

App là client của Supabase/RPC dùng chung với scanner PWA + webapp.

- Rollback app KHÔNG được kéo theo rollback DB/RPC nếu bản cũ phụ thuộc contract cũ.
- Trước khi rollback, kiểm tra bản app cũ còn tương thích DB/RPC/status hiện tại không
  (xem `02-shared-db-contract.md`, `specs/api_endpoints_draft.md`).
- Nếu contract đã đổi không tương thích ngược → phải có versioned RPC hoặc giữ tương thích;
  business rule commit kho vẫn ở backend, app cũ không được bypass.

## 8. Liên kết

- Build/variants/signing: `11_GRADLE_TASKS_BUILD_VARIANTS_SIGNING.md`
- Release checklist: `../checklists/android_release_checklist.md`
- Smoke test: `../checklists/smoke_test_checklist.md`
- QA execution report: `../checklists/qa_execution_report_template.md`
