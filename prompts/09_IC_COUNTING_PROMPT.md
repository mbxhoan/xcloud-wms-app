# 09 — IC Counting Prompt

Triển khai module Inventory Count cho Android native app.

## Goal

User xem phiếu kiểm kê được assign, scan/count theo scope, lưu kết quả, finish count. Phê duyệt/cân bằng có thể để webapp nếu ngoài scope MVP.

## Requirements

- IC assigned list.
- IC detail with scope/count mode.
- Count scan flow:
  - scan location.
  - scan product/lot/serial.
  - enter counted qty or mark serial found.
- Save count line.
- Duplicate serial prevention in UI + backend.
- Misplaced/new serial handling according to backend.
- Finish count.
- Loading/disabled states.

## UX

- Show count progress.
- Show counted vs expected/snapshot if API returns.
- Highlight diff.
- Clear error if serial already counted.

## Safety

- Count result does not adjust stock unless backend process says so.
- Adjustment approval should remain backend/webapp unless explicitly implemented.
- Idempotency for count line.

## Verify

- Count NONE qty.
- Count LOT qty.
- Count SERIAL found.
- Duplicate serial blocked.
- Product outside scope behavior correct.
- Finish count status correct.
- Webapp sees result.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-ic): implement inventory count scan flow`
- Entry trong `app/prompts/prompt_map.md`.
