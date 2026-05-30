# 05 — UI/UX Mobile-first Guidelines for Warehouse PDA

## 1. Nguyên tắc UX kho

Nhân viên kho thao tác nhanh, thường cầm PDA một tay, ánh sáng kho không ổn định, mạng có thể chập chờn, đôi khi mang găng tay. UI phải rõ hơn đẹp.

Nguyên tắc:

- Ít chữ.
- Nút to.
- Step rõ.
- Feedback tức thì.
- Không để người dùng đoán app đang load hay đơ.
- Mỗi lỗi phải có hướng xử lý.

## 2. Layout chuẩn màn scan

Mẫu layout:

```txt
┌─────────────────────────────┐
│ Header: Module + Warehouse  │
├─────────────────────────────┤
│ Current document/context    │
│ GR-xxx / GI-xxx / PA Draft  │
├─────────────────────────────┤
│ Current Step Card           │
│ “Scan vị trí nguồn”         │
│ Last scan + status          │
├─────────────────────────────┤
│ Form fields / qty input     │
├─────────────────────────────┤
│ Draft lines / progress      │
├─────────────────────────────┤
│ Primary action button       │
└─────────────────────────────┘
```

## 3. Component sizing

- Primary button height: >= 52dp.
- Scan step card: lớn, dễ nhìn.
- Font chính: 16sp trở lên.
- Số lượng/qty: 24sp hoặc lớn hơn.
- Tap target: >= 48dp.
- Tránh icon-only nếu action nguy hiểm.

## 4. Color/status

- Success: xanh, “Đã nhận mã”.
- Warning: vàng/cam, “Không khớp gợi ý”.
- Error: đỏ, “Không đủ tồn”.
- Disabled/loading: xám + spinner.
- Offline: banner cố định trên cùng.

Không chỉ dựa vào màu. Cần text/icon kèm theo.

## 5. Loading states

Mọi action gọi API phải có:

- Button disabled.
- Spinner hoặc progress nhỏ.
- Text “Đang xử lý...” nếu > 500ms.
- Timeout/retry nếu mạng lỗi.

Ví dụ:

```txt
[Hoàn tất] -> [Đang hoàn tất...] disabled
```

## 6. Error copywriting

Không dùng lỗi kỹ thuật thô như “500 Internal Server Error”.

Mapping:

| Technical | User message |
|---|---|
| 401 | Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại. |
| 403 | Bạn không có quyền thao tác kho này. |
| 404 | Không tìm thấy dữ liệu. Vui lòng quét lại. |
| OUT_OF_STOCK | Tồn kho không đủ tại vị trí này. Vui lòng refresh hoặc chọn vị trí khác. |
| DUPLICATE_SERIAL | Serial này đã tồn tại hoặc đã được quét. |
| STATUS_INVALID | Phiếu đã đổi trạng thái. Vui lòng tải lại. |

## 7. Home screen

Home cần cực đơn giản:

- Kho hiện tại.
- User hiện tại.
- Cards chức năng:
  - Nhận hàng (GR)
  - Lấy hàng/Xuất hàng (GI)
  - Sắp xếp kho (PA)
  - Kiểm kê (IC)
  - Tra cứu tồn
- Badge số task pending nếu API có.

## 8. GR screen UX

- Hiển thị phiếu assigned hoặc cho scan mã GR.
- Progress: received / expected.
- Step-by-step scan.
- Với SERIAL: mỗi scan serial tự add qty = 1 nếu hợp lệ.
- Với LOT/NONE: sau scan product hiển thị qty keypad lớn.
- Nút “Lưu dòng”, “Release”, “Hoàn tất” phân cấp rõ.

## 9. GI screen UX

- Hiển thị location gợi ý lớn.
- Nếu scan khác location gợi ý, tùy rule: cảnh báo/chặn.
- Progress picked / reserved.
- Serial scan liên tục phải nhanh.
- Complete phải có confirm nếu picked < reserved.

## 10. PA screen UX

Step đề xuất:

1. Scan vị trí nguồn.
2. Scan sản phẩm/serial/lot.
3. Nhập số lượng.
4. Scan vị trí đích.
5. Lưu dòng.
6. Review & Hoàn tất.

Màn review phải cho xóa dòng draft trước khi submit.

## 11. IC screen UX

- Hiển thị phạm vi kiểm kê.
- Cho scan location để lọc dòng.
- Serial: scan là found.
- LOT/NONE: nhập qty counted.
- Có badge chênh lệch nếu API trả diff.

## 12. Accessibility thực dụng

- Hỗ trợ font lớn.
- Contrast đủ.
- Không yêu cầu thao tác quá nhỏ.
- Hỗ trợ landscape nếu PDA có màn ngang, nhưng portrait là primary.

## 13. UX anti-mistake

- Confirm modal cho action nguy hiểm: Complete/Submit/Logout khi có draft.
- Double submit guard.
- Scan debounce 300–800ms tùy module.
- Draft autosave local nếu nhập lâu.
- Refresh khi status conflict.
