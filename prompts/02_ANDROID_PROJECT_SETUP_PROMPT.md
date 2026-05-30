# 02 — Android Project Setup Prompt

Hãy tạo Android native project cho Xcloud WMS Scanner trong folder `app/android`.

## Goal

Tạo project Kotlin + Jetpack Compose build được, có architecture foundation, chưa cần implement nghiệp vụ.

## Requirements

- Package: `vn.delfi.xcloudwms`
- App name: `Xcloud WMS Scanner`
- Kotlin.
- Jetpack Compose.
- Material 3.
- Navigation Compose.
- ViewModel + StateFlow.
- Network client placeholder.
- Build variants/flavors: dev, staging, prod nếu đơn giản tạo được ngay; nếu phức tạp, tạo config object trước.
- Safe logger.
- Base theme gần scanner PWA.
- Home placeholder.
- Login placeholder.
- Scanner Test placeholder.

## Suggested structure

```txt
app/android/
  app/src/main/java/vn/delfi/xcloudwms/
    core/
    data/
    domain/
    feature/
```

## Do not

- Không gọi API thật khi chưa có auth spec.
- Không hard-code tenant/warehouse.
- Không add SDK vendor cụ thể ở bước này.

## Verify

Chạy được:

```bash
./gradlew assembleDebug
```

Nếu môi trường không có Android SDK, vẫn tạo project/files và ghi rõ chưa build được vì thiếu SDK.

## Done output

- Files changed.
- Commands run.
- Build result.
- Risks remaining.
- Checklist verify.
- Commit message: `chore(app): initialize native android compose project`
- Entry trong `app/prompts/prompt_map.md`.
