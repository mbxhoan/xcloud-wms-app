# 00 — Orchestrator Prompt: Build Native Android PDA App

Bạn là lead agent cho workspace `xcloud-wms-workspace`. Nhiệm vụ của bạn là điều phối triển khai folder `app/` thành native Android app cho Xcloud WMS, tối ưu PDA scanner, parity với scanner PWA hiện tại.

## Context

Workspace có các folder:

```txt
webapp/      # Next.js management app
scanner/     # PWA scanner hiện tại
supabase/    # schema/migrations nếu có
app/         # Android native app mới
```

Native app phải giữ nghiệp vụ y chang scanner PWA hiện tại, không tự sáng tạo flow làm sai GR/GI/PA/IC.

## Read first

- `app/README.md`
- `app/AGENTS.md`
- `app/docs/00_APP_NATIVE_ANDROID_MASTER_PLAN.md`
- `app/docs/01_PRODUCT_SCOPE_PARITY_WITH_SCANNER_PWA.md`
- `app/docs/03_PDA_SCANNER_SDK_INTEGRATION.md`
- `app/docs/04_API_CONTRACT_AND_BACKEND_ADAPTER.md`

## Mission

Lập kế hoạch và triển khai theo phase:

1. Audit scanner PWA.
2. Chốt endpoint/payload/status/permission.
3. Tạo Android project Kotlin + Compose.
4. Tạo auth/context.
5. Tạo scanner abstraction.
6. Triển khai Stock Lookup.
7. Triển khai PA.
8. Triển khai GI.
9. Triển khai GR.
10. Triển khai IC.
11. Tạo build/test/release docs.

## Hard rules

- Không gọi Supabase trực tiếp nếu scanner PWA đang dùng REST API.
- Không hard-code tenant/warehouse/user id.
- Không tự tính stock ở client.
- Không commit kho nếu API trả lỗi.
- Không bypass permission.
- Không implement endpoint dự đoán nếu chưa audit.
- Không xóa/sửa lớn PWA/webapp nếu không được yêu cầu.

## Required output after each task

Luôn trả về:

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- One commit message.
- New entry in `app/prompts/prompt_map.md`.

## Start now

Hãy bắt đầu bằng Phase 0 Discovery:

- Scan folder `scanner/`.
- Tìm route/page/component liên quan Auth, Warehouse context, GR, GI, PA, IC, Stock Lookup.
- Tìm API client/services/hooks.
- Điền `app/specs/scanner_pwa_parity_matrix.md`.
- Điền `app/specs/api_endpoints_draft.md` bằng endpoint thật tìm được.
- Không code Android ở bước này.
