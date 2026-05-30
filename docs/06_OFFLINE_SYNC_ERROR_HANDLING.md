# 06 — Offline-lite, Sync & Error Handling

## 1. Quan điểm

WMS là hệ thống dữ liệu kho có tính chính xác cao. Offline full không nên làm vội vì có thể gây sai stock, trùng serial, conflict location. Giai đoạn đầu nên làm online-first + offline-lite.

## 2. Offline-lite là gì

Được phép:

- Cache danh mục nhẹ: warehouse, location, product basic, user context.
- Cache document detail vừa mở.
- Lưu draft local chưa submit.
- Retry GET/list/detail khi mạng lỗi.
- Hiển thị offline banner.

Không được phép nếu backend chưa hỗ trợ idempotency/conflict:

- Silent queue GR release/complete.
- Silent queue GI complete.
- Silent queue PA submit.
- Silent queue IC adjustment.

## 3. Local draft policy

PA/IC phù hợp local draft hơn GR/GI vì user có thể scan nhiều dòng trước khi submit.

Draft local cần lưu:

- feature/module.
- tenant id.
- warehouse id.
- user id.
- document/session id nếu có.
- list draft lines.
- created_at/updated_at.
- app version.

Không lưu token trong Room. Token lưu secure storage riêng.

## 4. Idempotency

Mọi POST commit phải kèm `X-Request-Id` hoặc field `request_id`.

Client behavior:

- Tạo request id trước khi gọi API.
- Nếu timeout, retry cùng request id.
- Nếu API trả already processed, hiển thị success và refresh.

Backend behavior cần có:

- Lưu request id trong log/idempotency table.
- Trả response cũ nếu request lặp.
- Không commit stock hai lần.

## 5. Network states

App cần monitor connectivity:

- Online.
- Offline.
- Captive/unstable.
- Server unreachable.

UI:

- Offline banner ở top.
- Chặn commit nếu offline.
- Cho nhập draft nếu an toàn.
- Cho retry khi online lại.

## 6. Conflict handling

Các conflict thường gặp:

| Conflict | Case | UI |
|---|---|---|
| Status changed | Phiếu đã completed/cancelled bởi user khác | Refresh detail, chặn submit |
| Stock changed | PA/GI location không còn đủ hàng | Highlight dòng lỗi |
| Serial duplicate | Serial đã được user khác nhận/xuất | Báo serial cụ thể |
| Permission changed | User bị thu quyền kho | Về home, reload permission |
| Subscription/device blocked | Tenant hết hạn/license vượt | Block login/operation theo backend |

## 7. Retry rules

Retry được:

- GET list/detail.
- Lookup barcode.
- Device heartbeat.
- Upload issue debug artifact nếu có.

Retry cẩn thận/idempotent:

- receive-line.
- pick-line.
- count-line.
- PA submit.
- complete document.

Không retry mù khi không có request id.

## 8. Error handling pattern

```txt
try API
  success -> update UI/refresh
  401 -> refresh token -> retry once -> login if fail
  403 -> show permission error
  409 -> conflict UI + refresh option
  422 -> field/business error
  5xx -> retry option, no local commit
  timeout -> retry option, no local commit unless idempotent
```

## 9. Logging

Log cần đủ debug nhưng không rò dữ liệu:

Allowed:

- request id.
- endpoint path, not full token.
- status code.
- feature/module.
- device model.
- app version.

Do not log:

- password.
- token.
- full user PII.
- secret key.

## 10. Crash reporting

Nếu dùng crash reporting:

- Tắt PII.
- Gắn context an toàn: tenant hash, user hash, app version, device model.
- Không gửi barcode raw nếu có thể chứa dữ liệu nhạy cảm.

## 11. Sync queue nếu làm Phase 2+

Chỉ làm queue commit khi backend có:

- idempotency.
- conflict response rõ.
- server-side validation đầy đủ.
- cơ chế reject/replay an toàn.

Queue item:

```json
{
  "id": "uuid",
  "feature": "PA_SUBMIT",
  "request_id": "uuid",
  "payload": {},
  "status": "PENDING|RUNNING|COMPLETED|FAILED|CONFLICT",
  "retry_count": 0,
  "created_at": "..."
}
```
