# 07 — Security, Auth, Device License

## 1. Security goals

Native PDA app phải đảm bảo:

- Chỉ user hợp lệ đăng nhập.
- Chỉ thấy đúng tenant/org.
- Chỉ thao tác kho được phân quyền.
- Chỉ thiết bị được phép mới dùng scanner app nếu bật license.
- Không lộ token/password trong log/storage.
- Không bypass subscription/device limits.

## 2. Auth flow

```txt
Login screen
  -> POST /auth/login
  -> receive access token/refresh token/profile
  -> store token securely
  -> load warehouses/permissions
  -> device verify if enabled
  -> home
```

Session resume:

```txt
App open
  -> read secure token
  -> verify/refresh
  -> load profile/context
  -> device heartbeat/check
  -> home or login
```

## 3. Token storage

- Access/refresh token lưu bằng secure storage.
- Không lưu trong plain SharedPreferences.
- Không log token.
- Logout phải clear secure storage + local user context.

## 4. Tenant/warehouse context

Native app có thể lưu local:

- current tenant id/name.
- current warehouse id/name.
- allowed warehouses.
- role/menu snapshot.

Nhưng backend vẫn check ở mỗi request. Local context không phải security boundary.

## 5. Device identity thực tế

Android app thường không nên/không thể dựa tuyệt đối vào IMEI/serial phần cứng cho mọi thiết bị phổ thông. Với PDA enterprise, vendor SDK/MDM có thể cung cấp serial/device id tốt hơn.

Chiến lược thực dụng:

- `android_id` + app install id + brand/model + vendor serial nếu có.
- Tạo `device_fingerprint` gửi backend.
- Backend cấp `registered_device_id` sau khi admin duyệt/auto register.
- Không tin fingerprint 100% để chống gian lận cao cấp, nhưng đủ cho license vận hành nội bộ.

## 6. Device registration flow

Option A — Auto request approval:

```txt
User login từ PDA mới
  -> App gửi device fingerprint
  -> Backend tạo device status=PENDING
  -> App báo “Thiết bị chờ duyệt”
  -> Admin duyệt trên webapp
  -> User login lại/refresh được dùng
```

Option B — Auto allow trong quota:

```txt
User login từ PDA mới
  -> Backend kiểm tra subscription.max_scanners
  -> Nếu còn quota: ACTIVE
  -> Nếu vượt quota: BLOCKED
```

## 7. Device license states

| State | Meaning | App behavior |
|---|---|---|
| ACTIVE | Được phép | Cho dùng |
| PENDING | Chờ duyệt | Block operation, show contact admin |
| BLOCKED | Bị chặn | Logout/block |
| EXPIRED | License hết hạn | Block/write warning theo backend |
| REVOKED | Admin thu hồi | Logout/block |

## 8. API đề xuất

```txt
POST /scanner/devices/register
POST /scanner/devices/verify
POST /scanner/devices/heartbeat
GET  /scanner/devices/me
```

Payload register:

```json
{
  "fingerprint": "hash",
  "android_id_hash": "hash",
  "install_id": "uuid",
  "brand": "Zebra",
  "model": "TC26",
  "os_version": "Android ...",
  "app_version": "1.0.0",
  "scanner_adapter": "BROADCAST"
}
```

## 9. App lock/resume security

- Nếu app background quá lâu, re-check session.
- Nếu device revoked, block ngay khi heartbeat fail.
- Nếu user đổi password hoặc bị lock, backend trả 401/403 và app logout.

## 10. Permissions menu

Menu native phải dựa trên permissions từ backend:

- `SCANNER_GR_VIEW/RECEIVE`.
- `SCANNER_GI_VIEW/PICK`.
- `SCANNER_PA_CREATE/SUBMIT`.
- `SCANNER_IC_VIEW/COUNT`.
- `SCANNER_STOCK_LOOKUP`.

Tên permission thật phải audit từ hệ thống hiện tại.

## 11. Security testing checklist

- User tenant A không thấy data tenant B.
- User không có warehouse X không thấy phiếu warehouse X.
- User bị khóa không login được.
- Device chưa duyệt không thao tác được.
- Token hết hạn xử lý đúng.
- Logout clear token.
- API 403 hiển thị đúng.
- Không có token/password trong Logcat.
