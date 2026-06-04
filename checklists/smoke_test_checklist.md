# Smoke Test Checklist — Native Android PDA App

Smoke test = kiểm tra nhanh sau khi cài 1 build, đủ tin để cho QA/UAT chạy tiếp.
Không thay thế QA đầy đủ (`../docs/09_QA_UAT_CHECKLIST.md`).

Tự động hóa phần install/launch/crash-watch bằng `../scripts/smoke-test.sh`.
Phần thao tác nghiệp vụ bên dưới làm thủ công trên thiết bị.

## A. Tiền đề

- [ ] Đúng flavor/env cần test (staging cho UAT, prod cho release).
- [ ] Đúng versionName / versionCode mong đợi.
- [ ] Build release đã ký (với bản phát hành thật).
- [ ] PDA mục tiêu sẵn sàng, scanner mode đúng (keyboard wedge / broadcast / SDK).

## B. Cài đặt (script lo phần này)

- [ ] `adb devices` thấy thiết bị.
- [ ] Cài fresh thành công.
- [ ] Cài đè (upgrade) từ bản trước thành công, không mất login.
- [ ] App mở không crash trong ~10s đầu (logcat không có FATAL EXCEPTION).

## C. Auth & context

- [ ] Login user scanner thành công.
- [ ] Login sai password báo lỗi rõ.
- [ ] Logout clear session.
- [ ] Chỉ thấy đúng tenant.
- [ ] Đổi kho reload đúng data.
- [ ] Menu hiện đúng theo permission.

## D. Scanner hardware

- [ ] Nút scan vật lý hoạt động ở màn Stock Lookup.
- [ ] Scan location ra kết quả.
- [ ] Scan product ra kết quả.
- [ ] Beep + vibrate khi scan thành công.
- [ ] Scan sai bước báo lỗi rõ (banner đỏ).

## E. Nghiệp vụ tối thiểu (trên staging, dùng data test)

- [ ] Stock lookup trả đúng tồn theo kho hiện tại.
- [ ] PA: tạo draft, add 1 dòng, available hiển thị đúng (submit chỉ khi dùng data test).
- [ ] GI: mở 1 phiếu assigned, thấy allocation/reserved.
- [ ] GR: mở 1 phiếu assigned, thấy expected/received.
- [ ] Double tap nút complete/submit KHÔNG tạo commit trùng.

## F. Network

- [ ] Tắt Wi-Fi khi đang ở list → báo offline rõ.
- [ ] Tắt Wi-Fi khi đang complete → KHÔNG hiển thị success giả.
- [ ] Bật mạng lại → retry/refresh được.

## G. Kết luận smoke

- [ ] PASS — đủ tin để chuyển QA/UAT đầy đủ hoặc rollout.
- [ ] FAIL — dừng, ghi issue, không rollout.

**Build:** ____________  **Thiết bị:** ____________  **Người test:** ____________  **Ngày:** ________

**Ghi chú / issue:**
