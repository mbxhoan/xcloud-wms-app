# 08 — GR Receiving Prompt

Triển khai module Goods Receipt Receiving cho Android native app.

## Goal

User xem danh sách GR được assign, mở phiếu, scan nhận hàng theo location/product/lot/serial/qty, release stock và complete theo API.

## Requirements

- GR assigned list.
- GR detail with expected/received progress.
- Receive scan stepper.
- Product NONE: scan product + qty + location.
- Product LOT: scan/enter lot + expiry/manufacture date if required + qty + location.
- Product SERIAL: scan serial, qty usually 1 each.
- Release stock action if PWA/backend has it.
- Complete action.
- Loading/disabled states.
- Error/refresh on status conflict.

## Validation UX

- Location required and must be in current warehouse.
- Product must belong to GR or allowed by backend.
- Lot/date required according to config/product.
- Serial duplicate blocked by backend and reflected in UI.

## Safety

- Do not increase stock on client.
- Release/complete only through backend.
- Idempotency for receive/release/complete.
- Do not show success until backend confirms.

## Verify

- Receive NONE.
- Receive LOT with expiry.
- Missing expiry blocked if required.
- Receive SERIAL and duplicate serial blocked.
- Release stock updates summary.
- Complete status correct.
- PWA/webapp sees result.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-gr): implement goods receipt receiving flow`
- Entry trong `app/prompts/prompt_map.md`.
