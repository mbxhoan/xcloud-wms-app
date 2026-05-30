# API Endpoints Draft / Audit Sheet

Không dùng file này như sự thật tuyệt đối. Đây là nơi ghi contract thực tế tìm thấy sau khi audit `scanner/`.

## Phase 0 conclusions

- Scanner PWA hiện không có backend REST `/scanner/*` riêng cho GR/GI/PA/IC/Lookup.
- Transport thực tế là Supabase JS SDK: `auth.*`, `rpc(...)`, `from(...).select/insert/update/delete`.
- Bảng dưới ghi endpoint tương đương ở tầng mạng: Supabase Auth, PostgREST `/rest/v1/rpc/*`, PostgREST table CRUD và route nội bộ Next.js.
- Supabase base URL và anon key được nạp động từ QR/manual config, nên native app không được hard-code host nếu muốn parity với scanner hiện tại.

## Auth and runtime bootstrap

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| SDK | `supabase.auth.signInWithPassword` | Login form | `{ email, password }` sau bước resolve username nếu có | Session + user | Raw auth path không hard-code trong source; scanner gọi qua SDK |
| POST | `/rest/v1/rpc/fn_auth_email_by_username` | Login bằng username | `{ p_username }` | `email` string | Chỉ gọi nếu user nhập identifier không có `@` |
| SDK | `supabase.auth.getUser` | App resume, pending device, layout guard | Không có body | Current auth user | Dùng để re-check session trước khi resolve permission/scope |
| SDK | `supabase.auth.signOut` | Logout, app gate fail | Không có body | Success/error | Sau sign-out còn clear cookie `_xc_dl` |
| POST | `/rest/v1/rpc/fn_scanner_check_device_license` | Login, app layout guard, pending-device revalidate | `{ p_user_id, p_device_id, p_device_fingerprint?, p_device_name?, p_device_type?, p_device_os?, p_device_os_version?, p_device_model? }` | `{ allowed, reason, message?, device_name?, device_code? }` | Nếu `PENDING_APPROVAL` thì route sang `/pending-device`; app layout cũng re-check mỗi request |
| POST | `/rest/v1/rpc/fn_scanner_redeem_device_license_key` | Pending device nhập key | `{ p_user_id, p_device_id, p_license_key, p_device_fingerprint?, p_device_name?, p_device_type?, p_device_os?, p_device_os_version?, p_device_model? }` | `{ allowed, reason }` | Thành công thì server action set lại cookie `_xc_dl` |
| POST | `/rest/v1/login_history` | Login success notice | `{ tenant_id?, user_id?, event_type, device_name?, device_type?, device_os?, device_model?, browser_name?, browser_version?, ip_address? }` | Inserted row/error | Ghi audit khi mở app sau login thành công |
| GET | `/api/device-gate?reason=...` | Block device sau khi layout guard fail | Query `reason`, `device_id?` | Redirect sang `/login` hoặc `/pending-device` | Route nội bộ Next.js, không phải backend kho |
| GET | `/api/tenant-asset?src=...` | Hiển thị asset tenant | `src` | Image proxy stream | Chỉ proxy asset host hợp lệ của Supabase |

## Shared context: profile, permission, warehouse scope, menu stats

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/rest/v1/profiles` | Resolve display name, avatar, warehouse scope fallback | Filter theo `id`, `user_id`, `email` | Profile row | `warehouse-scope.ts` và layout thử nhiều cột/shape khác nhau |
| GET | `/rest/v1/users` | Resolve tenant/user directory, warehouse scope fallback | Filter theo `id`, `user_id`, `auth_user_id`, `email` | User row | GR/GI create dùng bảng này để lấy `tenant_id` |
| GET | `/rest/v1/user_roles` | Resolve role của user | Filter `user_id in (...)` | Role assignment rows | Dùng cùng `role_permissions` để tổng hợp permission |
| GET | `/rest/v1/user_permissions` | Direct permission của user | Filter `user_id in (...)` | User permission rows | Có thể chứa code trực tiếp hoặc permission id |
| GET | `/rest/v1/role_permissions` | Permission theo role | Filter `role_id in (...)` | Role permission rows | Có thể chứa code trực tiếp hoặc permission id |
| GET | `/rest/v1/permissions` | Resolve permission code theo id | Filter `id in (...)` | Permission rows | Permission cuối cùng được normalize về lowercase |
| GET | `/rest/v1/user_warehouses` | Warehouse options cho create GR/GI và app context | Filter theo `user_id` | User warehouse rows | Không có global warehouse switch endpoint riêng |
| GET | `/rest/v1/warehouses` | Warehouse options, PA selector | Filter `is_active = true`, `deleted_at is null` | Warehouse rows | PA load trực tiếp danh sách kho user được phép thấy |
| GET | `/rest/v1/gr_headers` | Menu stats | `select=status` + status filters | Countable header rows | Layout count trạng thái `created/new` vs `receiving` |
| GET | `/rest/v1/gi_headers` | Menu stats | `select=status` + status filters | Countable header rows | Layout count trạng thái `created/new` vs `picking` |
| GET | `/rest/v1/ic_headers` | Menu stats | `select=status` + status filters | Countable header rows | Layout count trạng thái `created/new/draft` vs `in_progress` |

## Scanner lookup

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/rest/v1/rpc/rpc_traceability_lookup` | `/app/lookup` | `{ p_query }` | `LookupResult { query, match, summary, currentRows, activeLpns, lpnContentsPreview, events, warnings }` | Không có endpoint lookup REST riêng ngoài RPC này |

## Goods Receipt

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/rest/v1/rpc/fn_tenant_settings_preview` | Create GR | `{ p_scope: "document_codes" }` | Object có `gr_headers` code preview | Dùng để gợi ý số phiếu trước khi insert |
| POST | `/rest/v1/gr_headers` | Create GR từ scanner | `{ tenant_id, warehouse_id, reference_type: "PDA_INITIATED", status: "CREATED", assigned_scanner_user_id, code, partner_id?, notes? }` | Inserted `gr_headers` row | Create GR trong scanner luôn tạo phiếu `PDA_INITIATED` |
| GET | `/rest/v1/gr_headers` | GR list, GR detail header | Filter theo `id`, `status`, `assigned_scanner_user_id`, join warehouse | `gr_headers[]` hoặc single row | List loại bỏ `COMPLETED` |
| GET | `/rest/v1/gr_lines` | GR detail | Filter `gr_header_id = ...` | `gr_lines[]` | PDA flow có thể tạo thêm line mới sau scan |
| GET | `/rest/v1/gr_details` | GR detail | Filter `gr_line_id in (...)` hoặc `gr_header_id` qua join | `gr_details[]` | Bao gồm serial/lot/location/date fields |
| GET | `/rest/v1/locations` | GR detail | Filter theo warehouse hoặc `id in (...)` | `locations[]` | Dùng cho chọn vị trí nhận |
| GET | `/rest/v1/products` | PDA GR lookup | Filter `id in (...)` | `products[]` | Kết hợp `tracking_type` để quyết định flow scan |
| GET | `/rest/v1/uoms` | GR detail | Filter `id in (...)` | `uoms[]` | Standard GR cho phép đổi UOM line |
| GET | `/rest/v1/lots` | GR detail | Filter `id in (...)` | `lots[]` | Dùng khi render/resolve detail hiện có |
| GET | `/rest/v1/serials` | GR detail | Filter `id in (...)` | `serials[]` | Dùng khi render/resolve detail hiện có |
| POST | `/rest/v1/rpc/rpc_gr_start_receiving` | Standard GR, PDA GR submit/save | `{ p_gr_id }` | Success/error | Scanner gọi trước khi save/submit nếu phiếu chưa ở trạng thái nhận |
| POST | `/rest/v1/rpc/rpc_check_serial_scan` | Standard GR scan | `{ p_tenant_id, p_warehouse_id, p_code, p_gr_header_id, p_gr_line_id, p_ic_header_id: null, p_ic_line_id: null }` | Validation/result object | Shared RPC dùng lại cho IC |
| POST | `/rest/v1/rpc/rpc_check_lot_scan` | Standard GR scan | `{ p_tenant_id, p_warehouse_id, p_code, p_gr_header_id, p_gr_line_id }` | Validation/result object | Shared RPC dùng lại cho IC |
| POST | `/rest/v1/rpc/rpc_gr_resolve_serial_scan` | Standard GR scan | `{ p_gr_line_id, p_serial_number, p_allow_create, p_manufacture_date?, p_expiry_date? }` | Resolved serial object | Có thể auto-create serial mới nếu backend cho phép |
| POST | `/rest/v1/rpc/rpc_gr_resolve_lot_scan` | Standard GR scan | `{ p_gr_line_id, p_lot_number, p_allow_create, p_manufacture_date?, p_expiry_date? }` | Resolved lot object | Có thể auto-create lot mới nếu backend cho phép |
| POST | `/rest/v1/rpc/rpc_gr_pda_lookup_barcode` | PDA GR scan | `{ p_gr_header_id, p_code }` | Barcode match / product-tracking payload | Flow PDA lookup khác flow standard |
| POST | `/rest/v1/rpc/rpc_gr_pda_ensure_line` | PDA GR scan | `{ p_gr_header_id, p_product_id, p_quantity_expected: 1, p_uom_id? }` | Ensured `gr_line` | Cho phép thêm line mới theo barcode scan |
| POST | `/rest/v1/gr_details` | Standard GR, PDA GR | `{ gr_line_id, serial_id?, lot_id?, quantity, location_id?, inbound_date?, manufacture_date?, manufactured_date?, expiry_date?, tenant_id? }` | Inserted `gr_details` row | Code có retry thêm `tenant_id` nếu backend yêu cầu not-null |
| PATCH | `/rest/v1/gr_details` | PDA GR chỉnh qty | Filter `id = ...` | Updated `gr_details` row | Dùng khi chỉnh quantity cho lot/none |
| DELETE | `/rest/v1/gr_details` | Standard GR, PDA GR | Filter `id = ...` | Deleted row count | Cho phép xoá detail đã sync |
| PATCH | `/rest/v1/gr_lines` | Standard GR đổi UOM | `{ uom_id }` với filter `id = ...` | Updated `gr_lines` row | Không thấy scanner đổi planned qty ở standard GR |
| POST | `/rest/v1/rpc/rpc_gr_submit_receive` | PDA GR complete | `{ p_gr_id }` | Success/error | Chỉ thấy bằng chứng submit hoàn tất trong PDA GR client, chưa thấy ở standard GR client |

## Goods Issue

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/rest/v1/rpc/fn_tenant_settings_preview` | Create GI | `{ p_scope: "document_codes" }` | Object có `gi_headers` code preview | Dùng để gợi ý số phiếu trước khi insert |
| POST | `/rest/v1/gi_headers` | Create GI từ scanner | `{ tenant_id, warehouse_id, reference_type: "PDA_INITIATED", issue_mode: "SUMMARY", status: "CREATED", assigned_scanner_user_id, code, partner_id?, notes? }` | Inserted `gi_headers` row | Create GI trong scanner luôn tạo phiếu `PDA_INITIATED` |
| GET | `/rest/v1/gi_headers` | GI list, GI detail header | Filter theo `id`, `status`, warehouse scope, join warehouse | `gi_headers[]` hoặc single row | List loại bỏ `COMPLETED` |
| GET | `/rest/v1/gi_lines` | GI detail | Filter `gi_header_id = ...` | `gi_lines[]` | PDA GI có thể insert/update line mới |
| GET | `/rest/v1/gi_details` | GI detail | Filter `gi_line_id in (...)` | `gi_details[]` | Dùng cho picked qty hiện tại |
| GET | `/rest/v1/locations` | GI detail | Filter theo warehouse hoặc `id in (...)` | `locations[]` | Dùng cho render from-location |
| GET | `/rest/v1/stock_summary` | GI detail, PDA GI | Filter theo warehouse/product/lot/serial | `stock_summary[]` | PDA GI dựa nhiều vào table này thay vì RPC aggregate riêng |
| GET | `/rest/v1/products` | PDA GI | Filter `id in (...)` | `products[]` | Dùng để hiển thị thông tin scan |
| GET | `/rest/v1/lots` | GI detail, PDA GI | Filter `id in (...)` | `lots[]` | Dùng khi resolve lot lines |
| GET | `/rest/v1/serials` | GI detail, PDA GI | Filter `id in (...)` | `serials[]` | Dùng khi resolve serial lines |
| POST | `/rest/v1/rpc/rpc_gi_start_picking` | Standard GI, PDA GI submit | `{ p_gi_id }` | Success/error | Thường gọi trước khi bắt đầu mutate picked qty |
| POST | `/rest/v1/rpc/rpc_gi_check_serial_scan` | Standard GI scan | `{ p_tenant_id, p_warehouse_id, p_code, p_gi_header_id, p_gi_line_id }` | Validation/result object | Dùng cho serial path |
| POST | `/rest/v1/rpc/rpc_gi_check_lot_scan` | Standard GI scan | `{ p_tenant_id, p_warehouse_id, p_code, p_gi_header_id, p_gi_line_id }` | Validation/result object | Dùng cho lot path |
| POST | `/rest/v1/rpc/rpc_gi_bind_serial_to_summary_line` | Standard GI summary line scan | `{ p_tenant_id, p_warehouse_id, p_gi_header_id, p_gi_line_id, p_code }` | Binding result | Flow serial scan của summary issue mode |
| POST | `/rest/v1/rpc/rpc_lpn_resolve_scan_code` | PDA GI whole-LPN | `{ p_code }` | LPN resolution payload | PDA GI hỗ trợ scan nguyên LPN |
| POST | `/rest/v1/rpc/rpc_gi_scan_whole_lpn` | PDA GI whole-LPN | `{ p_gi_id, p_lpn_id, p_expected_version? }` | Whole-LPN pick result | Dùng sau khi resolve mã LPN |
| POST | `/rest/v1/gi_details` | Standard GI, PDA GI | `{ gi_line_id, serial_id?, lot_id?, quantity, picked_quantity, ... }` | Inserted `gi_details` row | Chủ yếu dùng cho path `NONE` hoặc PDA-created detail |
| PATCH | `/rest/v1/gi_details` | Standard GI, PDA GI | `{ picked_quantity }` hoặc `{ quantity, picked_quantity }` với filter `id = ...` | Updated `gi_details` row | Standard flow thường update row có sẵn |
| POST | `/rest/v1/gi_lines` | PDA GI | `{ gi_header_id, product_id, uom_id, quantity_needed }` | Inserted `gi_lines` row | PDA GI có thể tự tạo line mới |
| PATCH | `/rest/v1/gi_lines` | PDA GI | `{ quantity_needed }` với filter `id = ...` | Updated `gi_lines` row | Dùng khi cùng sản phẩm được scan thêm |
| POST | `/rest/v1/rpc/rpc_gi_confirm` | PDA GI submit fallback | `{ p_gi_id }` | Success/error | Chỉ thấy trong PDA GI client; dùng nếu `start_picking` cần phiếu rời `CREATED` |
| POST | `/rest/v1/rpc/rpc_gi_submit` | Standard GI code path, PDA GI submit | `{ p_gi_id }` | Success/error | `GiPickClient` có code này nhưng UI hard-disable direct submit với `const canDirectSubmit = false`; submit hoạt động thấy rõ ở PDA GI |

## Put-away

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/rest/v1/warehouses` | PA warehouse selector | Filter active warehouses in user scope | `warehouses[]` | PA tự chọn kho trong màn hình |
| GET | `/rest/v1/locations` | PA source/destination selectors | Filter `warehouse_id = ...`, `is_active = true` | `locations[]` | Dùng cho cả from/to location |
| GET | `/rest/v1/pa_headers` | Resume PA draft/session | Filter theo warehouse/status/header id | `pa_headers[]` | Single-screen workflow vẫn cần load lại session draft |
| GET | `/rest/v1/pa_details` | Resume PA draft/session | Filter `pa_header_id = ...` | `pa_details[]` | Có join location/product/uom/lot/serial |
| GET | `/rest/v1/stock_summary` | Resolve available qty | Filter theo warehouse/location/product/tracking | `stock_summary[]` | Client còn tự query `serials` và `lots` để render mã |
| GET | `/rest/v1/serials`, `/rest/v1/lots` | Resolve tracked code | Filter `id in (...)` hoặc theo mã | `serials[]`, `lots[]` | Dùng trong helper resolve code |
| POST | `/rest/v1/rpc/rpc_pa_list_products` | Product picker | `{ p_warehouse_id }` | Product options with available qty | Không thấy REST list riêng ngoài RPC này |
| POST | `/rest/v1/rpc/rpc_pa_list_source_locations` | Source-location resolver | `{ p_warehouse_id, p_product_id, p_lot_id?, p_serial_id? }` | Source location options | Quan trọng cho flow put-away đúng vị trí nguồn |
| POST | `/rest/v1/rpc/rpc_pa_start_session` | Start PA draft | `{ p_warehouse_id, p_notes }` | Started `pa_header` | Dùng khi user chưa có draft hiện tại |
| POST | `/rest/v1/rpc/rpc_pa_live_stock_check` | Validate line trước add | `{ p_warehouse_id, p_from_location_id, p_product_id, p_lot_id?, p_serial_id? }` | Live stock check result | Không được tự tính stock ở client |
| POST | `/rest/v1/rpc/rpc_pa_add_line` | Add line | `{ p_pa_header_id, p_from_location_id, p_to_location_id, p_product_id, p_qty, p_uom_id, p_lot_id?, p_serial_id?, p_notes? }` | Added detail/result | Client tự chặn duplicate nhưng source of truth vẫn là RPC |
| POST | `/rest/v1/rpc/rpc_pa_delete_line` | Delete line | `{ p_detail_id }` | Success/error | Không dùng REST delete table trực tiếp |
| POST | `/rest/v1/rpc/rpc_pa_submit` | Submit PA | `{ p_pa_header_id }` | Success/error | Hoàn tất session put-away |
| POST | `/rest/v1/rpc/rpc_process_inventory_threshold_events` | Post-submit background catch-up | `{ p_limit }` | Success/error | Scanner gọi ngay sau `rpc_pa_submit` |

## Inventory Count

| Method | Endpoint | Used by | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/rest/v1/rpc/rpc_scanner_list_ic_headers` | IC list | Không có payload riêng | `ic_headers[]` | Scanner ưu tiên RPC này cho list |
| GET | `/rest/v1/ic_headers` | IC list fallback, IC detail header | Filter `status = IN_PROGRESS` hoặc `id = ...` | `ic_headers[]` hoặc single row | Fallback khi RPC list không có/không dùng được |
| POST | `/rest/v1/rpc/rpc_scanner_validate_doc_access` | IC detail guard | `{ p_doc_type: "IC", p_doc_id }` | Access result | Explicit document access check trước khi load screen |
| GET | `/rest/v1/ic_lines` | IC detail | Filter `ic_header_id = ...` | `ic_lines[]` | Dùng làm line base cho count UI |
| GET | `/rest/v1/locations` | IC detail | Filter `id in (...)` hoặc `warehouse_id = ...` | `locations[]` | Dùng cho location picker/display |
| POST | `/rest/v1/rpc/rpc_ic_start_counting` | Start count | `{ p_id }` | Success/error | Nếu RPC thiếu thì client fallback sang `PATCH /rest/v1/ic_headers` với `status = "IN_PROGRESS"` |
| POST | `/rest/v1/rpc/rpc_check_serial_scan` | IC serial scan | `{ p_tenant_id, p_warehouse_id, p_code, p_ic_header_id, p_ic_line_id?, p_gr_header_id: null, p_gr_line_id: null }` | Validation/result object | Shared RPC với GR |
| POST | `/rest/v1/rpc/rpc_check_lot_scan` | IC lot scan | `{ p_tenant_id, p_warehouse_id, p_code, p_ic_header_id, p_ic_line_id? }` | Validation/result object | Shared RPC với GR |
| POST | `/rest/v1/rpc/rpc_ic_add_detail` | Add counted detail | `{ p_header_id, p_product_id, p_location_id, p_serial_id?, p_lot_id?, p_qty_counted, p_ic_line_id?, p_note }` | Success/result object | Client có fallback retry bỏ `p_ic_line_id` nếu backend dùng signature cũ |
| POST | `/rest/v1/rpc/rpc_ic_update_count` | Update counted qty | `{ p_line_id, p_qty_counted, p_note }` | Success/result object | Nếu RPC này thiếu thì client fallback sang `rpc_ic_add_detail` |
| POST | `/rest/v1/rpc/rpc_ic_scan_lpn` | Whole-LPN count | `{ p_ic_header_id, p_lpn_code }` | LPN count result | Flow quét nguyên LPN có hỗ trợ trong scanner hiện tại |
| POST | `/rest/v1/rpc/rpc_ic_remove_lpn_scan` | Undo whole-LPN count | `{ p_ic_header_id, p_lpn_id?, p_lpn_code? }` | Success/result object | Dùng để gỡ một lượt quét LPN |
| POST | `/rest/v1/rpc/rpc_ic_complete` | Chỉ thấy trong playbook, chưa thấy trong code scanner | Chưa đủ bằng chứng từ source hiện tại | Chưa đủ bằng chứng từ source hiện tại | Không implement Android step hoàn tất IC theo endpoint này nếu chưa audit lại backend/webapp |
