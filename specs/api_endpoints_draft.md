# API Endpoints Draft / Audit Sheet

Không dùng file này như sự thật tuyệt đối. Đây là nơi ghi endpoint thật sau khi audit scanner PWA/backend.

## Auth

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/auth/login` | Login | | | TODO |
| POST | `/auth/refresh` | Session | | | TODO |
| POST | `/auth/logout` | Logout | | | TODO |
| GET | `/me` | App resume | | | TODO |
| GET | `/me/warehouses` | Warehouse switch | | | TODO |

## Scanner lookup

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/scanner/lookup` | Generic scan | `code`, `warehouse_id` | | TODO |
| GET | `/scanner/stock/lookup` | Stock lookup | | | TODO |

## Goods Receipt

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/scanner/gr/assigned` | GR list | | | TODO |
| GET | `/scanner/gr/{id}` | GR detail | | | TODO |
| POST | `/scanner/gr/{id}/receive-line` | Receive | | | TODO |
| POST | `/scanner/gr/{id}/release-stock` | Release | | | TODO |
| POST | `/scanner/gr/{id}/complete` | Complete | | | TODO |

## Goods Issue

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/scanner/gi/assigned` | GI list | | | TODO |
| GET | `/scanner/gi/{id}` | GI detail | | | TODO |
| POST | `/scanner/gi/{id}/pick-line` | Pick | | | TODO |
| POST | `/scanner/gi/{id}/complete` | Complete | | | TODO |

## Put-away

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/scanner/pa/sessions` | Start session | | | TODO |
| GET | `/scanner/pa/sessions/{id}` | Session detail | | | TODO |
| POST | `/scanner/pa/sessions/{id}/lines` | Add line | | | TODO |
| DELETE | `/scanner/pa/sessions/{id}/lines/{line_id}` | Delete line | | | TODO |
| POST | `/scanner/pa/sessions/{id}/submit` | Submit | | | TODO |

## Inventory Count

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/scanner/ic/assigned` | IC list | | | TODO |
| GET | `/scanner/ic/{id}` | IC detail | | | TODO |
| POST | `/scanner/ic/{id}/count-line` | Count | | | TODO |
| POST | `/scanner/ic/{id}/finish-count` | Finish | | | TODO |
