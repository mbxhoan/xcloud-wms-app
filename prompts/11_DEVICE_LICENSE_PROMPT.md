# 11 — Device License Prompt

Triển khai device registration/license check cho native scanner app.

## Goal

Kiểm soát thiết bị PDA được phép dùng app scanner theo tenant/subscription/max scanners.

## Requirements

- Generate install id.
- Collect safe device info: brand, model, OS, app version, android id hash if allowed.
- Optional vendor serial if SDK available.
- Register/verify API based on backend contract.
- Device status screen.
- Block operation if PENDING/BLOCKED/REVOKED/EXPIRED.
- Heartbeat on app resume and periodically if needed.

## Security

- Hash sensitive hardware identifiers before sending if required.
- Do not log raw identifier.
- Backend is source of truth.

## UX

- If pending: “Thiết bị đang chờ quản trị viên duyệt.”
- If blocked: “Thiết bị này không được phép sử dụng app scanner.”
- If quota exceeded: “Số lượng thiết bị scanner đã vượt giới hạn gói.”

## Verify

- New device pending/active flow.
- Blocked device cannot operate.
- Revoked device gets blocked on resume.
- Logout clears local but not device registration.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-device): add scanner device license verification`
- Entry trong `app/prompts/prompt_map.md`.
