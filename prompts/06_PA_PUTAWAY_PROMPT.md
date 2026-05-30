# 06 — PA Put-away/Internal Transfer Prompt

Triển khai module PA native app, parity với scanner PWA.

## Goal

Nhân viên kho tạo phiên sắp xếp DRAFT, scan from location/product/qty/to location, lưu nhiều dòng, review và submit transaction.

## Requirements

- PA list/session optional nếu PWA có.
- Start session API.
- Stepper UI:
  1. Scan from location.
  2. Scan product/lot/serial.
  3. Enter qty.
  4. Scan to location.
  5. Add line.
- Draft lines list.
- Delete draft line.
- Submit session.
- Conflict handling.
- Loading/disabled states.

## Validation UX

- from location required.
- product required.
- qty > 0.
- from != to.
- qty <= available if API lookup returns available.
- Backend still validates on submit.

## Safety

- Add line may save draft; stock does not change until submit.
- Submit uses idempotency request id.
- Double tap submit must not commit twice.
- If submit fails, keep draft lines.

## Verify

- Add 1 line successfully.
- Add multiple lines.
- Delete draft line.
- Submit moves stock correctly.
- Conflict shows line error.
- PWA/webapp sees PA result.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-pa): implement putaway scan session flow`
- Entry trong `app/prompts/prompt_map.md`.
