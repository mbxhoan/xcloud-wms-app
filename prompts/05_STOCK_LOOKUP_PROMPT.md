# 05 — Stock Lookup Prompt

Triển khai Stock Lookup trước để kiểm chứng auth + warehouse context + scanner core + API.

## Goal

User scan location/product/serial hoặc nhập tay để xem tồn kho read-only theo kho hiện tại.

## Requirements

- Screen `StockLookupScreen`.
- Uses `ScannerManager`.
- Calls audited lookup API.
- Shows result cards:
  - Product.
  - Warehouse.
  - Location.
  - Lot/serial if any.
  - On hand.
  - Reserved.
  - Available.
  - Expiry/inbound date if any.
- Empty state.
- Error/retry.
- Offline banner.

## Rules

- Read-only, không mutate stock.
- Must include current warehouse context.
- Must respect backend permission errors.

## Verify

- Scan product returns stock.
- Scan location returns stock list.
- Scan unknown code shows clear error.
- Switch warehouse changes result.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-stock): add scanner stock lookup`
- Entry trong `app/prompts/prompt_map.md`.
