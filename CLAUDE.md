# CLAUDE.md — Native Android PDA Scanner App

Mục tiêu của folder `app/`: xây dựng native Android app cho Xcloud WMS, mobile-first, tối ưu PDA scanner, parity với scanner PWA hiện tại.

Đọc trước:

1. `README.md`
2. `AGENTS.md`
3. `docs/00_APP_NATIVE_ANDROID_MASTER_PLAN.md`
4. `docs/03_PDA_SCANNER_SDK_INTEGRATION.md`
5. `prompts/00_ORCHESTRATOR_PROMPT.md`

## Working agreement

- Audit trước, code sau.
- Mỗi module phải map với scanner PWA hiện tại.
- Không tự bịa endpoint.
- Không tự quyết business rule stock ở client.
- Mọi commit kho phải qua backend transaction.
- Tối ưu UX cho kho: nhanh, rõ, ít tap, nhiều feedback.
- Lỗi user-facing: parity scanner PWA — message có nghĩa, đúng locale, không lộ machine code từ API (`../CLAUDE.md` §1.2).

## Target app capabilities

- Login/logout.
- Tenant/org context.
- Warehouse context switch.
- Role-based home/menu.
- Scanner input abstraction.
- GR receive.
- GI picking.
- PA put-away/internal transfer.
- IC counting.
- Stock lookup.
- Draft/session handling.
- Offline-lite/cache.
- Device/license verification.
- APK/AAB build for internal distribution.

## Native-specific focus

- PDA physical trigger.
- Broadcast/keyboard wedge/SDK adapters.
- Vibration/beep feedback.
- Large buttons and glove-friendly UI.
- Network resilience in warehouse Wi-Fi/4G.
- App update path for non-technical warehouse users.
