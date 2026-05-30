# 07 — GI Picking Prompt

Triển khai module Goods Issue Picking cho Android native app.

## Goal

User xem danh sách GI được assign, mở phiếu, scan lấy hàng, update picked qty và complete/ship theo API.

## Requirements

- GI assigned list.
- GI detail with progress.
- Allocation/reserved lines.
- Scan location/product/lot/serial.
- Pick qty update.
- Overpick prevention.
- Complete/ship action.
- Partial pick handling according to PWA/backend.
- Loading/disabled states.
- Error/refresh on status conflict.

## UX

- Suggested location shown large.
- Current line highlighted.
- Picked/reserved progress visible.
- Serial scan continuous mode for serial products.
- Confirm modal if complete with partial picked quantity.

## Safety

- Do not reduce stock on client.
- Complete only through backend.
- Idempotency for pick/complete.
- If backend returns status conflict, refresh document.

## Verify

- Pick correct item.
- Wrong product blocked.
- Overpick blocked.
- Complete updates stock ledger/summary.
- PWA/webapp sees result.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-gi): implement goods issue picking flow`
- Entry trong `app/prompts/prompt_map.md`.
