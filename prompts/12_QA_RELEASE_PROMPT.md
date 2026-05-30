# 12 — QA, Build & Release Prompt

Chuẩn hóa test/build/release cho Android native scanner app.

## Goal

Dev có thể build APK/AAB, test trên emulator/phone/PDA, và release nội bộ an toàn.

## Requirements

- Add/verify Gradle tasks docs.
- Add release signing docs without committing keystore.
- Add build variants config docs.
- Add QA checklist execution report template.
- Add smoke test script/checklist.
- Add versioning policy.
- Add rollback policy.

## Tests

- Unit tests for parser/ViewModel where available.
- Mock API tests if possible.
- Manual PDA tests documented.

## Verify commands

```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

If environment lacks Android SDK, write exact reason and commands for developer machine.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `chore(app-release): add android qa build and release workflow`
- Entry trong `app/prompts/prompt_map.md`.
