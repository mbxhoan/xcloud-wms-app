# 09 — QA & UAT Checklist for Native PDA App

## 1. Test matrix thiết bị

| Device | Android | Scanner mode | Camera | Network | Result | Note |
|---|---|---|---|---|---|---|
| Android phone | | Camera/manual | Yes | Wi-Fi/4G | | |
| PDA model 1 | | Keyboard/Broadcast/SDK | | Wi-Fi | | |
| PDA model 2 | | Keyboard/Broadcast/SDK | | 4G | | |

## 2. Auth & context

- [ ] Login đúng user scanner.
- [ ] Login sai password báo lỗi rõ.
- [ ] Token hết hạn tự refresh hoặc về login.
- [ ] Logout clear session.
- [ ] User chỉ thấy đúng tenant.
- [ ] User chỉ thấy kho được phân quyền.
- [ ] Đổi kho reload đúng data.
- [ ] Menu hiện theo permission.

## 3. Device license

- [ ] Thiết bị mới gửi register/verify.
- [ ] ACTIVE được dùng.
- [ ] PENDING bị chặn thao tác.
- [ ] BLOCKED/REVOKED bị logout/block.
- [ ] Vượt quota scanner hiển thị rõ.
- [ ] Heartbeat không làm app đơ.

## 4. Scanner core

- [ ] Scan QR.
- [ ] Scan Code128.
- [ ] Scan EAN13 nếu cần.
- [ ] Scan location.
- [ ] Scan product.
- [ ] Scan lot.
- [ ] Scan serial.
- [ ] Duplicate scan debounce.
- [ ] Scan sai bước báo lỗi.
- [ ] Beep/vibrate đúng.
- [ ] Camera fallback hoạt động.
- [ ] Manual input hoạt động.

## 5. Stock lookup

- [ ] Scan product ra tồn theo kho hiện tại.
- [ ] Scan location ra danh sách tồn tại vị trí.
- [ ] Scan serial ra đúng product/location/status.
- [ ] Không thấy tồn kho ngoài quyền.
- [ ] Mạng lỗi có retry.

## 6. GR Receiving

- [ ] Danh sách GR assigned đúng.
- [ ] Mở detail đúng expected/received.
- [ ] Receive product NONE.
- [ ] Receive LOT với expiry.
- [ ] Thiếu expiry khi bắt buộc bị chặn.
- [ ] Receive SERIAL qty=1 mỗi scan.
- [ ] Serial trùng bị chặn.
- [ ] Location sai kho bị chặn.
- [ ] Release stock thành công.
- [ ] Complete phiếu đúng status.
- [ ] Double tap không commit trùng.
- [ ] PWA/webapp refresh thấy dữ liệu native vừa nhập.

## 7. GI Picking

- [ ] Danh sách GI assigned đúng.
- [ ] Mở detail thấy allocation/reserved.
- [ ] Scan đúng location/product/serial.
- [ ] Scan sai product bị chặn.
- [ ] Overpick bị chặn.
- [ ] Partial pick nếu cho phép.
- [ ] Complete shipment trừ tồn đúng.
- [ ] Release excess reserved nếu partial.
- [ ] Double tap không xuất trùng.

## 8. PA Put-away/Internal Transfer

- [ ] Start session DRAFT.
- [ ] Scan from location.
- [ ] Scan product/lot/serial.
- [ ] Hiển thị available.
- [ ] Qty > available bị chặn.
- [ ] From = To bị chặn.
- [ ] Add nhiều dòng.
- [ ] Xóa dòng draft.
- [ ] Submit chuyển tồn đúng.
- [ ] Conflict stock khi submit hiển thị dòng lỗi.
- [ ] Completed session không sửa được.

## 9. IC Counting

- [ ] Danh sách IC assigned đúng.
- [ ] Mở detail đúng scope.
- [ ] Scan location filter lines.
- [ ] Count NONE/LOT qty.
- [ ] Count SERIAL found.
- [ ] Duplicate serial trong phiếu bị chặn.
- [ ] Misplaced serial hiển thị cảnh báo.
- [ ] Finish count đúng status.
- [ ] Webapp thấy kết quả count.

## 10. Network/reliability

- [ ] Tắt Wi-Fi khi đang ở list: báo offline.
- [ ] Tắt Wi-Fi khi đang draft PA: không mất draft.
- [ ] Tắt Wi-Fi khi complete: không hiển thị success giả.
- [ ] Bật mạng lại: retry/refresh được.
- [ ] API 500 hiển thị retry.
- [ ] API 409 conflict xử lý đúng.

## 11. Performance

- [ ] App mở < 3s trên PDA mục tiêu.
- [ ] Scan feedback < 200ms local.
- [ ] Lookup API có loading rõ.
- [ ] Danh sách 100 dòng vẫn mượt.
- [ ] Scan liên tục 50 serial không crash.
- [ ] Pin/heat chấp nhận sau 2 giờ test.

## 12. UAT kho thật

- [ ] 1 thủ kho test GR.
- [ ] 1 thủ kho test GI.
- [ ] 1 thủ kho test PA.
- [ ] 1 quản lý xem kết quả trên webapp.
- [ ] Ghi lại thao tác nào chậm/khó hiểu.
- [ ] Ghi lại barcode nào scan lỗi.
- [ ] Ghi lại điểm khác PWA nếu có.

## 13. Go/no-go

Go nếu:

- Không có bug blocker về stock/permission/auth.
- Không có commit trùng khi double tap/scan nhanh.
- PDA hardware scanner ổn định.
- User kho hiểu cách dùng sau hướng dẫn ngắn.

No-go nếu:

- Có thể âm kho/trùng serial.
- User thấy data tenant/kho khác.
- Complete/submit có thể chạy 2 lần.
- Hardware scan không ổn định trên thiết bị chính.
