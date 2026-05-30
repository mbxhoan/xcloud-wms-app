# 04 — API Contract & Backend Adapter

## 1. Nguyên tắc

Native app nên dùng cùng backend REST API với scanner PWA. Không gọi Supabase/Postgres trực tiếp từ Android nếu scanner PWA hiện tại đang đi qua Node REST API.

Lý do:

- Backend đang enforce tenant, permission, subscription.
- Backend đang xử lý stock transaction.
- Backend đã có status flow và audit.
- Client native không nên chứa business rule commit kho.

## 2. Header chuẩn đề xuất

Mỗi request từ native app nên gửi:

```http
Authorization: Bearer <access_token>
X-App-Channel: SCANNER_NATIVE
X-App-Version: 1.0.0
X-Device-Id: <backend_registered_device_id_or_fingerprint>
X-Request-Id: <uuid>
X-Tenant-Id: <optional_if_backend_requires>
X-Warehouse-Id: <current_warehouse_id_if_contextual>
```

`X-Request-Id` dùng để idempotency/chống double submit.

## 3. API groups cần audit từ scanner PWA

Không implement theo tên dưới đây nếu backend hiện tại khác. Đây là contract định hướng để audit/chuẩn hóa.

### Auth & context

```txt
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /me
GET  /me/warehouses
POST /me/default-warehouse
```

### Device/license

```txt
POST /scanner/devices/register
POST /scanner/devices/verify
POST /scanner/devices/heartbeat
```

### Lookup

```txt
GET /scanner/lookup?code={barcode}&warehouse_id={id}
GET /scanner/stock/lookup?product_code={code}&warehouse_id={id}
GET /scanner/stock/by-location?location_code={code}&warehouse_id={id}
GET /scanner/stock/by-serial?serial={serial}&warehouse_id={id}
```

### GR Receiving

```txt
GET  /scanner/gr/assigned
GET  /scanner/gr/{id}
POST /scanner/gr/{id}/receive-line
POST /scanner/gr/{id}/release-stock
POST /scanner/gr/{id}/complete
```

### GI Picking

```txt
GET  /scanner/gi/assigned
GET  /scanner/gi/{id}
POST /scanner/gi/{id}/pick-line
POST /scanner/gi/{id}/complete
```

### PA Put-away

```txt
POST /scanner/pa/sessions
GET  /scanner/pa/sessions/{id}
POST /scanner/pa/sessions/{id}/lines
DELETE /scanner/pa/sessions/{id}/lines/{line_id}
POST /scanner/pa/sessions/{id}/submit
```

### IC Counting

```txt
GET  /scanner/ic/assigned
GET  /scanner/ic/{id}
POST /scanner/ic/{id}/count-line
POST /scanner/ic/{id}/finish-count
```

## 4. DTO rule

DTO không dùng trực tiếp trong UI. Map sang domain model.

Ví dụ:

```kotlin
data class GrHeaderDto(
    val id: Long,
    val code: String,
    val status: String,
    val warehouseId: Long,
    val lines: List<GrLineDto>
)

data class GoodsReceipt(
    val id: DocumentId,
    val code: DocumentCode,
    val status: GrStatus,
    val warehouseId: WarehouseId,
    val lines: List<GoodsReceiptLine>
)
```

## 5. Response envelope đề xuất

Nếu backend chưa có chuẩn, nên chuẩn hóa:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "request_id": "..."
}
```

Error:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "OUT_OF_STOCK",
    "message": "Tồn kho không đủ",
    "details": {
      "line_id": 123,
      "available": 5,
      "requested": 10
    }
  },
  "request_id": "..."
}
```

## 6. Idempotency

Các API commit phải hỗ trợ idempotency:

- receive-line.
- release-stock.
- complete.
- pick-line.
- submit PA.
- count-line nếu user scan nhanh.

Client tạo `request_id` theo action:

```txt
<device_id>:<feature>:<document_id>:<line_id>:<timestamp_or_uuid>
```

Backend nên lưu/nhận diện request đã xử lý để không commit trùng.

## 7. Permission & tenant

Native app không tự tin vào local permission. Local chỉ dùng để ẩn menu. Backend vẫn phải check:

- user belongs to tenant.
- user has warehouse permission.
- user has feature permission.
- subscription/device license còn hiệu lực.

## 8. API audit template

Khi audit scanner PWA, điền vào bảng:

| Feature | PWA file | Method | Endpoint | Request | Response | Error codes | Native screen |
|---|---|---|---|---|---|---|---|
| GR list | | GET | | | | | |
| GR receive | | POST | | | | | |
| GI list | | GET | | | | | |
| GI pick | | POST | | | | | |
| PA submit | | POST | | | | | |
| IC count | | POST | | | | | |

## 9. Backend change rule

Nếu native cần API mới:

1. Chứng minh PWA không có endpoint phù hợp.
2. Ghi rõ contract request/response.
3. Viết migration/test nếu cần.
4. Không làm breaking change với PWA.
5. Cập nhật docs và prompt map.
