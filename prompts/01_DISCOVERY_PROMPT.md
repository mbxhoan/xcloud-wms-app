# 01 — Discovery Prompt: Audit Scanner PWA Parity

Bạn đang trong workspace `xcloud-wms-workspace`. Hãy audit scanner PWA hiện tại để chuẩn bị xây dựng Android native app trong `app/`.

## Input folders

- `scanner/`
- `webapp/` nếu scanner dùng shared API/types.
- `supabase/` nếu cần đối chiếu schema/permissions.
- `app/specs/` để cập nhật kết quả.

## Tasks

1. Tìm toàn bộ màn hình scanner PWA:
   - Login.
   - Warehouse switch/context.
   - Home/menu/task list.
   - GR receiving.
   - GI picking.
   - PA put-away/internal transfer.
   - IC counting.
   - Stock lookup nếu có.

2. Tìm API calls:
   - File API client.
   - Service/hook dùng fetch/axios/supabase.
   - Endpoint, method, request payload, response shape.
   - Error handling.

3. Tìm permission logic:
   - Permission code.
   - Role/warehouse filtering.
   - Assigned scanner user logic.

4. Tìm scan handling hiện tại:
   - Input field/focus.
   - Keyboard wedge behavior.
   - Debounce.
   - Barcode parser nếu có.

5. Cập nhật:
   - `app/specs/scanner_pwa_parity_matrix.md`
   - `app/specs/api_endpoints_draft.md`
   - `app/specs/navigation_map.md` nếu cần.

## Constraints

- Không code Android ở bước này.
- Không sửa backend.
- Không đoán endpoint; ghi `UNKNOWN` nếu chưa tìm thấy.
- Nếu phát hiện gap của PWA, ghi vào Risks.

## Done output

- Files changed.
- Commands run.
- Findings summary.
- Risks remaining.
- Checklist verify.
- Commit message: `docs(app): audit scanner pwa parity for native android`
- Entry trong `app/prompts/prompt_map.md`.
