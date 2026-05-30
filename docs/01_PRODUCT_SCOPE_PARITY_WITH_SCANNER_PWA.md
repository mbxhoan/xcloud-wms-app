# 01 — Product Scope & Scanner PWA Parity

## 1. Nguyên tắc parity

Native app không được “sáng tạo lại” nghiệp vụ khác scanner PWA. Mục tiêu là cùng logic, cùng API, cùng status flow; chỉ thay đổi công nghệ client và tối ưu UX/PDA.

Mọi màn hình native cần audit scanner PWA trước khi code:

- Route PWA hiện tại là gì?
- API nào được gọi?
- Payload request/response ra sao?
- User role nào được vào?
- Loading/error/empty state hiện tại ra sao?
- Scan input hiện tại xử lý ra sao?
- Có debounce/chống submit lặp không?

## 2. Module parity bắt buộc

| Module | Native screen | PWA parity cần kiểm tra | Ghi chú |
|---|---|---|---|
| Auth | Login | Login API, token, tenant resolve | Không hard-code tenant |
| Context | Warehouse Switch | Danh sách kho user được quyền | Lưu default warehouse local |
| Home | Task Dashboard | Pending GR/GI/IC/PA nếu PWA có | Mobile-first cards |
| Stock Lookup | Scan lookup | Product/location/serial lookup | Read-only |
| GR | Receive | Expected vs received, lot/serial/date/location | Không tự cộng kho client |
| GI | Pick | Allocation/reserved/picked qty | Chặn overpick |
| PA | Put-away | Draft session, scan lines, submit | Commit atomic qua API |
| IC | Count | Snapshot lines, count detail, finish | Serial duplicate check |
| Issue Ticket | Report issue | app_channel = SCANNER nếu có | Optional MVP |

## 3. UX parity nhưng tối ưu native

PWA có thể phụ thuộc input focus. Native phải dùng scan pipeline trung tâm:

```txt
Hardware Trigger / SDK / Broadcast / Keyboard / Camera
        ↓
ScannerManager
        ↓
ScanParser
        ↓
Current Screen ScanIntent Handler
        ↓
API validation / local step transition
        ↓
Feedback: beep/vibrate/banner/highlight
```

## 4. Flow GR Receiving

Mục tiêu native:

1. Chọn phiếu GR assigned hoặc scan mã GR.
2. Xem danh sách dòng cần nhận.
3. Scan location.
4. Scan product.
5. Nhập/scan lot nếu sản phẩm tracking LOT.
6. Scan serial nếu sản phẩm tracking SERIAL.
7. Nhập qty nếu NONE/LOT; SERIAL thường qty = 1 mỗi scan.
8. Add/confirm line.
9. Release/complete theo API hiện có.

Validation UX:

- Location không thuộc kho hiện tại: chặn.
- Product không thuộc phiếu: cảnh báo.
- Lot/expiry thiếu khi bắt buộc: chặn.
- Serial trùng: chặn.
- Submit đang loading: disable nút.

## 5. Flow GI Picking

Mục tiêu native:

1. Danh sách GI assigned cho user/PDA.
2. Mở phiếu, thấy progress picked/total.
3. Scan suggested location.
4. Scan product/lot/serial.
5. Xác nhận picked qty.
6. Chặn picked > reserved.
7. Complete/ship theo API.

Validation UX:

- Scan sai location: cảnh báo nếu bắt buộc theo allocation.
- Scan sai product/serial: chặn.
- Overpick: chặn.
- Partial pick: cho phép nếu backend/PWA cho phép.

## 6. Flow PA Put-away/Internal Transfer

Mục tiêu native:

1. Start session DRAFT.
2. Scan from location.
3. Scan product/lot/serial.
4. Nhập qty.
5. App hiện available tại vị trí nguồn.
6. Scan to location.
7. Add line vào draft.
8. Review lines.
9. Submit toàn bộ phiên qua API transaction.

Validation UX:

- from = to: chặn.
- qty <= 0: chặn.
- qty > available: chặn.
- Submit phải check lại tồn ở backend, nếu conflict thì báo dòng cụ thể.

## 7. Flow IC Counting

Mục tiêu native:

1. Danh sách phiếu IC assigned.
2. Mở phiếu, thấy phạm vi kiểm kê.
3. Scan location/product/lot/serial.
4. Nhập counted qty.
5. Lưu tạm.
6. Finish count.
7. Manager duyệt trên webapp hoặc native nếu được scope.

Validation UX:

- Serial scan trùng trong cùng phiếu: chặn.
- Serial misplaced: cảnh báo rõ.
- Product ngoài scope: cảnh báo/chặn theo rule backend.

## 8. Acceptance parity checklist

- Cùng user, cùng kho, cùng phiếu: PWA và native hiển thị số liệu giống nhau.
- Native submit một dòng thì PWA refresh thấy đúng trạng thái.
- PWA submit một dòng thì native refresh thấy đúng trạng thái.
- Permission bị chặn giống nhau.
- Lỗi API giống nhau nhưng native hiển thị dễ hiểu hơn.
- Status flow không lệch.
- Stock summary/ledger sau thao tác native khớp với PWA/webapp.
