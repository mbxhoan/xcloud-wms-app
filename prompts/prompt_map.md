# Prompt Map — Native Android App

File này lưu mapping giữa prompt/user request và commit message để truy vết.

## Format

```md
## YYYY-MM-DD HH:mm — <short title>

- Prompt summary:
- Ticket/Issue ID:
- Scope:
- Main files changed:
- Tests run:
- Commit message: `<type>(<scope>): <summary>`
- Notes/Risks:
```

---

## 2026-05-30 10:44 — Phase 0 scanner parity discovery

- Prompt summary: Audit `scanner/` để chốt route, component, auth context, warehouse scope, GR/GI/PA/IC/Lookup contract cho native Android PDA app; điền parity matrix và API draft bằng endpoint thật đang dùng.
- Ticket/Issue ID: APP-PHASE0
- Scope: `app + scanner + docs` - chỉ audit/spec mapping, chưa code Android.
- Main files changed:
  - `app/specs/scanner_pwa_parity_matrix.md`
  - `app/specs/api_endpoints_draft.md`
  - `app/prompts/prompt_map.md`
  - `docs/commit_prompt_map.md`
- Tests run: Audit source bằng `rg` và `sed`; không chạy build/test vì chưa đổi runtime code.
- Commit message: `docs(app): audit scanner pwa parity and backend contracts`
- Notes/Risks:
  - Scanner PWA thực tế đang dựa vào Supabase Auth, RPC và table CRUD; không có bằng chứng về backend REST `/scanner/*` riêng cho các flow kho.
  - `rpc_ic_complete` chỉ thấy trong playbook tài liệu, không thấy scanner source gọi; `rpc_gi_submit` ở standard GI cũng đang bị UI hard-disable, nên Android phase sau phải bám đúng evidence này.

## Initial — Create native Android app documentation pack

- Prompt summary: Create `/app` folder documentation and implementation prompts for native Android PDA scanner app parity with scanner PWA.
- Ticket/Issue ID: APP-INIT
- Scope: docs/prompts/specs/checklists only.
- Main files changed: `app/**`
- Tests run: N/A
- Commit message: `docs(app): add native android pda scanner implementation pack`
- Notes/Risks: Requires agent to audit actual scanner PWA endpoints before coding.
