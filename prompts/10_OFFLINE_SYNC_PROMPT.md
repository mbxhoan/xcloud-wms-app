# H 10 — Offline-lite & Sync Prompt

Triển khai offline-lite/reliability cho native app.

## Goal

App không mất dữ liệu draft khi mạng yếu, nhưng không làm sai stock.

## Requirements

- Connectivity monitor.
- Offline banner.
- Cache user/warehouse/product/location lightweight if APIs available.
- Local draft for PA/IC before submit.
- Retry GET/list/detail.
- Idempotency request id for commit APIs.
- Conflict handling UI.
- Clear “Cần có mạng để hoàn tất” for commit not queueable.

## Do not

- Không silent queue GR/GI/PA/IC commit nếu backend chưa có idempotency/conflict support.
- Không show success khi chưa có response thành công.

## Verify

- Turn off network while viewing list.
- Turn off network while editing PA draft.
- Submit while offline is blocked safely.
- Turn network on and retry.
- Timeout retry uses same request id if applicable.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-sync): add offline lite draft and retry handling`
- Entry trong `app/prompts/prompt_map.md`.
