# 03 — Auth, Tenant & Warehouse Context Prompt

Triển khai auth/context cho native Android app dựa trên endpoint thật đã audit trong `app/specs/api_endpoints_draft.md`.

## Goal

User login được, app lưu token an toàn, load profile/tenant/warehouses, chọn kho hiện tại, menu theo permission.

## Requirements

- Login screen.
- Auth repository.
- Token secure storage.
- Session restore.
- Refresh token nếu backend hỗ trợ.
- Logout clear session.
- Load profile/current tenant.
- Load allowed warehouses.
- Warehouse switch screen.
- Current warehouse persisted local.
- Home menu theo permission.
- Error handling: 401/403/network.

## UX

- Login button disabled khi loading.
- Show password toggle.
- Error message rõ.
- Nếu user có 1 kho: auto select.
- Nếu nhiều kho: show selection list.
- Nếu không có kho: block với message “Bạn chưa được phân quyền kho nào.”

## Security

- Không log token/password.
- Không lưu token plain text.
- Không hard-code credentials.

## Verify

- Login success.
- Login wrong password error.
- Logout clear state.
- App restart restore session.
- User chỉ thấy warehouses assigned.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-auth): add login tenant and warehouse context`
- Entry trong `app/prompts/prompt_map.md`.

Before implementing Auth, first run cd app/android && ./gradlew :app:assembleDevDebug.
If build fails, fix build issues first and stop.
Do not modify scanner/, webapp/, or supabase/.
Do not invent REST /scanner/* APIs.
Follow the real scanner PWA contracts documented in app/specs/api_endpoints_draft.md.
Stop after native Auth + tenant + warehouse context compiles and basic login/navigation flow is verifiable.