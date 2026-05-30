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

## 2026-05-30 11:13 — Phase 1 Android Compose foundation

- Prompt summary: Tạo native Android project trong `app/android` cho Xcloud WMS Scanner với Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel + StateFlow, logger an toàn, network/scanner placeholders và ba màn chờ `Đăng nhập` / `Trang chủ` / `Kiểm tra máy quét`.
- Ticket/Issue ID: APP-PHASE1
- Scope: `app + docs` - scaffold project Android native phase 1, chưa gọi API thật, chưa đổi DB/RPC/API contract.
- Main files changed:
  - `app/android/**`
  - `app/prompts/prompt_map.md`
  - `docs/commit_prompt_map.md`
- Tests run:
  - `git -C app diff --check`
  - `rg -n '"[^"]*(Placeholder|placeholder|screen|foundation|pipeline|mobile-first|Mode:)[^"]*"' app/android/app/src/main/java/vn/delfi/xcloudwms/feature app/android/app/src/main/java/vn/delfi/xcloudwms/core/ui`
  - `cd app/android && java -version` ✅ OpenJDK 17.0.19
  - `cd app/android && ./gradlew :app:assembleDevDebug` ✅ pass
- Commit message: `chore(app): initialize native android compose project`
- Notes/Risks:
  - Đã scaffold flavors `dev/staging/prod`, `AppConfig`, `AppContainer`, `SessionRepository`, `ScannerManager`, theme paper/light gần scanner PWA và navigation shell.
  - Đã vá dependency `com.google.android.material:material`, suppress warning `compileSdk 35` và lỗi import `weight` trong Compose để `:app:assembleDevDebug` chạy thành công.

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
