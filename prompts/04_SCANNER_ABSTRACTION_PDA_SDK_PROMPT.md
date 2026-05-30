# 04 — Scanner Abstraction & PDA Input Prompt

Triển khai scanner core cho Android native app.

## Goal

Mọi barcode/QR input đi qua `ScannerManager`, không phụ thuộc trực tiếp vào UI screen.

## Requirements

1. Domain models:
   - `ScanEvent`
   - `ScanSource`
   - `ScannerMode`
   - `ParsedBarcode`

2. Core interfaces:
   - `ScannerManager`
   - `ScannerAdapter`
   - `BarcodeParser`
   - `FeedbackManager`

3. Adapters:
   - Keyboard wedge adapter.
   - Broadcast intent adapter configurable action/extra keys.
   - Manual input adapter for debug.
   - Camera fallback placeholder hoặc implementation nếu dependency sẵn sàng.

4. Scanner Test screen:
   - Show current adapter.
   - Show last raw scan.
   - Show parsed type.
   - Beep/vibrate test.

5. Duplicate debounce:
   - Same raw value within configurable interval should not double-submit unless screen allows continuous serial scan.

## PDA config

Không hard-code vendor. Broadcast action/extra keys để config được trong settings/dev config.

## UX feedback

- Success beep/vibrate.
- Error beep/vibrate.
- Banner/message on scan.

## Verify

- Gõ text + Enter trên emulator tạo scan event.
- Manual input tạo scan event.
- Broadcast fake bằng adb nếu possible.
- Screen inactive không xử lý scan sai route.

## Done output

- Files changed.
- Commands run.
- Risks remaining.
- Checklist verify.
- Commit message: `feat(app-scan): add scanner abstraction and pda input adapters`
- Entry trong `app/prompts/prompt_map.md`.
