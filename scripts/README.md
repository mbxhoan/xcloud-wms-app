# Scripts

Helper scripts cho build/test/release native Android app. Chạy từ folder `app/`
(scripts tự `cd` vào `app/android`). Tham chiếu chi tiết: `../docs/11_GRADLE_TASKS_BUILD_VARIANTS_SIGNING.md`.

| Script | Tác dụng | Ví dụ |
|---|---|---|
| `build-debug.sh` | Build debug APK theo flavor | `./scripts/build-debug.sh staging` |
| `build-release.sh` | Build release APK/AAB (ký nếu có `keystore.properties`) | `./scripts/build-release.sh prod aab` |
| `install-pda.sh` | Cài APK lên PDA/thiết bị qua adb | `./scripts/install-pda.sh app/android/app/build/outputs/apk/staging/debug/app-staging-debug.apk` |
| `collect-logcat.sh` | Dump logcat cho bug report, đã lọc token/password | `./scripts/collect-logcat.sh bug.txt` |
| `smoke-test.sh` | Build + cài + mở app + bắt crash khởi động, rồi nhắc checklist thủ công | `./scripts/smoke-test.sh staging` |

- `commands-cheatsheet.md`: cheat sheet gradle/adb thủ công.
- Smoke test thủ công: `../checklists/smoke_test_checklist.md`.

Không commit keystore/password/secrets vào đây. `keystore.properties`, `*.jks`, `*.keystore`
đã nằm trong `.gitignore`.
