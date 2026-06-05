Đúng rồi, native app của bạn **không nên hành xử giống web/PWA scanner** ở điểm này. Với PDA thật, các ô “quét mã” phải hoạt động như **scan target**, không phải ô nhập text thông thường. Ảnh bạn gửi đang bị lỗi UX đúng kiểu web: focus vào ô scan là Android bật bàn phím ảo lên.

Với native Android, hướng đúng là: **mặc định bật “Chặn bàn phím ảo”**, mọi ô quét mã dùng chung một component riêng, ví dụ `PdaScanField`, và component này phải nhận dữ liệu từ scanner manager / broadcast / keyboard wedge thay vì để hệ điều hành mở soft keyboard.

## Kết luận kỹ thuật

Nên làm theo 3 lớp:

```txt
ScannerSettings
  blockSoftKeyboard = true   // default
  submitMode = ENTER | TAB | NONE
  inputMode = PDA_BROADCAST | KEYBOARD_WEDGE | MANUAL_FALLBACK

ScannerManager
  nhận dữ liệu từ PDA SDK / Broadcast / Keyboard Wedge / FakeScanner

PdaScanField
  hiển thị value + focus
  không bật bàn phím ảo nếu blockSoftKeyboard = true
  xử lý auto enter/tab theo submitMode
```

Với Android View truyền thống, Android có API `TextView.setShowSoftInputOnFocus(false)` để quyết định soft keyboard có hiện khi field được focus hay không; mặc định là `true`, nên nếu không set thì bàn phím sẽ nhảy lên như ảnh của bạn. ([Android Developers][1]) Jetpack Compose `TextField` có `KeyboardOptions`/`imeAction`, nhưng Android docs cũng nói các keyboard option có thể không được đảm bảo nếu bàn phím phần mềm không tuân thủ option đó, nên với PDA production tôi không khuyến nghị chỉ dựa vào `OutlinedTextField` mặc định. ([Android Developers][2])

## Hướng triển khai tôi khuyên

### 1. Không dùng `OutlinedTextField` thường cho ô quét mã

Các field như:

```txt
Mã hàng / lô / serial / vị trí
From Location
To Location
Product barcode
Serial barcode
Lot barcode
```

phải đổi sang component riêng:

```kotlin
PdaScanField(
    value = code,
    label = "Mã hàng / lô / serial / vị trí",
    blockSoftKeyboard = scannerSettings.blockSoftKeyboard,
    submitMode = scannerSettings.submitMode,
    onScanChanged = { ... },
    onScanSubmit = { ... },
    onMoveNext = { ... }
)
```

Không để mỗi màn tự xử lý riêng, vì sau này GR/GI/PA/IC sẽ rất dễ lệch behavior.

---

### 2. Dùng `AndroidView + EditText` cho scan field nếu cần khóa bàn phím thật chắc

Trong Compose, cách ổn định nhất cho PDA là bọc `EditText` bằng `AndroidView`, rồi set:

```kotlin
editText.showSoftInputOnFocus = false
```

Ví dụ component mẫu:

```kotlin
@Composable
fun PdaScanField(
    value: String,
    label: String,
    blockSoftKeyboard: Boolean,
    submitMode: ScannerSubmitMode,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMoveNext: () -> Unit,
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            AppCompatEditText(context).apply {
                hint = label
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_NONE

                // Quan trọng nhất cho PDA:
                showSoftInputOnFocus = !blockSoftKeyboard

                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus && blockSoftKeyboard) {
                        view.post {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm?.hideSoftInputFromWindow(view.windowToken, 0)
                        }
                    }
                }

                setOnEditorActionListener { _, _, event ->
                    val isEnter =
                        event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_UP

                    if (isEnter) {
                        when (submitMode) {
                            ScannerSubmitMode.ENTER -> onSubmit()
                            ScannerSubmitMode.TAB -> onMoveNext()
                            ScannerSubmitMode.NONE -> Unit
                        }
                        true
                    } else {
                        false
                    }
                }

                setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false

                    when (keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            when (submitMode) {
                                ScannerSubmitMode.ENTER -> onSubmit()
                                ScannerSubmitMode.TAB -> onMoveNext()
                                ScannerSubmitMode.NONE -> Unit
                            }
                            true
                        }

                        KeyEvent.KEYCODE_TAB -> {
                            onMoveNext()
                            true
                        }

                        else -> false
                    }
                }
            }
        },
        update = { editText ->
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(editText.text.length)
            }

            editText.showSoftInputOnFocus = !blockSoftKeyboard

            if (editText.hint != label) {
                editText.hint = label
            }
        }
    )
}
```

Lưu ý: `InputMethodManager.hideSoftInputFromWindow` chỉ là biện pháp phụ để đóng keyboard nếu nó lỡ bật; gốc vẫn phải là `showSoftInputOnFocus = false`. Android docs mô tả `hideSoftInputFromWindow` là request ẩn soft input từ window đang nhận input. ([Android Developers][3])

---

### 3. Tách rõ “scan xong làm gì”: ENTER, TAB, NONE

Nên định nghĩa enum:

```kotlin
enum class ScannerSubmitMode {
    ENTER, // scan xong tự submit / tra cứu / lưu dòng
    TAB,   // scan xong nhảy sang field tiếp theo
    NONE   // chỉ điền mã, user tự bấm nút
}
```

Mapping nghiệp vụ nên như sau:

```txt
Stock Lookup:
  ENTER = tự bấm Tra cứu
  TAB = ít dùng
  NONE = điền mã rồi chờ bấm Tra cứu

PA Putaway:
  From Location scan xong -> TAB qua Product
  Product scan xong -> TAB qua Qty hoặc tự lookup tồn
  To Location scan xong -> ENTER lưu dòng

GI Picking:
  Location scan xong -> TAB
  Product/Serial scan xong -> ENTER confirm picked line

GR Receiving:
  Product scan xong -> TAB
  Lot/Serial scan xong -> TAB hoặc ENTER tùy flow
  Location scan xong -> ENTER add line

IC Counting:
  Location scan xong -> TAB
  Product/Serial scan xong -> ENTER mark counted
```

Compose `KeyboardActions` có các action như `onDone`, `onNext`, `onSearch`, trong đó `onNext` mặc định move focus sang item tiếp theo nếu không override. ([Android Developers][4]) Nhưng với scanner PDA, đừng phụ thuộc hoàn toàn vào IME action; hãy xử lý `KEYCODE_ENTER`/`KEYCODE_TAB` hoặc scan event từ `ScannerManager`.

---

## Điểm rất quan trọng với PDA thật

Có 2 kiểu scanner output:

```txt
1. Keyboard wedge:
   Scanner bắn mã như bàn phím gõ vào field đang focus.
   Có thể kèm Enter/Tab.

2. Intent/Broadcast/SDK:
   Scanner gửi barcode event thẳng vào app.
   App không cần field text đang focus thật.
```

Zebra DataWedge có “Keystroke Output”, tức gửi dữ liệu scan như chuỗi phím bấm, và có hỗ trợ TAB/ENTER/special characters để submit hoặc chuyển focus. ([Zebra TechDocs][5]) Honeywell cũng có Barcode Data Intent, cho phép nhận barcode data mà không cần SDK/library riêng. ([Honeywell SPS Community][6])

Với Xcloud native app, tôi khuyên thứ tự ưu tiên:

```txt
Ưu tiên 1: Broadcast/Intent/SDK provider
Ưu tiên 2: Keyboard wedge provider
Ưu tiên 3: Manual fallback cho emulator/test
```

Nếu dùng Broadcast/Intent, UX sẽ “native PDA” hơn rất nhiều: người dùng bấm nút scan vật lý, app nhận mã, tự điền field active, tự Enter/Tab theo setting, **không bao giờ cần bật bàn phím ảo**.

---

## Prompt cho Codex/agent để sửa đúng vấn đề này

Bạn có thể dán prompt này ngay:

```txt
Fix native Android PDA scan input behavior.

Context:
The native Android app is already built and running on a PDA. Current issue: scan input fields behave like normal text fields, so Android soft keyboard opens when focusing fields such as product/location/lot/serial. This is wrong for PDA operation.

Goal:
All scan-code fields must behave like PDA scanner targets, not regular manual input fields.

Requirements:
1. Add scanner settings:
   - blockSoftKeyboard: Boolean, default true
   - submitMode: ENTER | TAB | NONE, default ENTER or per-screen default
   - allowManualInputFallback: Boolean, default false for PDA mode, true for emulator/dev if needed

2. Implement a reusable PdaScanField component.
   - Must be used for all barcode/QR scan fields.
   - When blockSoftKeyboard = true, focusing/tapping the field must NOT show Android soft keyboard.
   - Keep focus/cursor behavior so keyboard-wedge scanner can still inject scan text.
   - Support hardware scanner suffix ENTER and TAB.
   - Support manual fallback only when allowManualInputFallback = true.

3. If Compose OutlinedTextField cannot reliably suppress soft keyboard, use AndroidView + AppCompatEditText and set:
   showSoftInputOnFocus = false
   Also hide keyboard defensively on focus.

4. Add ScannerSubmitMode:
   enum class ScannerSubmitMode { ENTER, TAB, NONE }

5. Implement behavior:
   - ENTER: trigger current field submit action
   - TAB: move focus to next scan field
   - NONE: only fill value, no auto action

6. Integrate this into current scanner screens:
   - Stock Lookup / Tra cứu tồn kho
   - Scanner Test
   - PA Putaway fields if present
   - GI Picking fields if present
   - GR Receiving fields if present
   - IC Counting fields if present

7. Do not change backend/API/RPC contracts.
8. Do not modify scanner/, webapp/, supabase/.
9. Do not invent REST /scanner/* APIs.
10. Keep dev/emulator testing possible with manual fallback or FakeScannerProvider.

Verification:
- Build:
  cd app/android && ./gradlew :app:assembleDevDebug
- On PDA:
  open Stock Lookup
  tap scan field
  verify soft keyboard does NOT open when blockSoftKeyboard=true
  scan barcode with physical scan button
  verify value is filled
  verify ENTER mode triggers Tra cứu
  verify TAB mode moves to next field
- Toggle blockSoftKeyboard=false if settings UI exists, then verify keyboard can open for manual typing.
- Ensure no regression in navigation.

Return:
- Files changed
- Implementation summary
- Screens updated
- Test commands
- PDA manual test checklist
- Remaining risks
- Commit message
```

## Test checklist trên thiết bị của bạn

Sau khi agent sửa xong, test đúng như này:

```txt
✅ Mở màn Tra cứu tồn kho
✅ Tap vào ô “Mã hàng / lô / serial / vị trí”
✅ Bàn phím ảo KHÔNG hiện
✅ Bấm nút scan vật lý trên PDA
✅ Mã được điền vào field
✅ Nếu submitMode = ENTER: tự Tra cứu
✅ Nếu submitMode = TAB: nhảy sang field tiếp theo
✅ Nếu submitMode = NONE: chỉ điền mã, không submit
✅ Tắt blockSoftKeyboard trong settings/dev mode thì bàn phím có thể hiện lại
```

Commit message nên là:

```txt
fix(app): enforce PDA scan fields without soft keyboard
```

Tóm lại: **đừng sửa từng màn lẻ**. Hãy bắt agent làm `PdaScanField + ScannerSettings + ScannerSubmitMode`, rồi thay toàn bộ ô quét mã sang component này. Đây là nền móng bắt buộc để native app khác biệt và tốt hơn PWA scanner.

[1]: https://developer.android.com/reference/android/widget/TextView "TextView  |  API reference  |  Android Developers"
[2]: https://developer.android.com/develop/ui/compose/text/user-input "Configure text fields  |  Jetpack Compose  |  Android Developers"
[3]: https://developer.android.com/reference/android/view/inputmethod/InputMethodManager "InputMethodManager  |  API reference  |  Android Developers"
[4]: https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/KeyboardActions "KeyboardActions  |  API reference  |  Android Developers"
[5]: https://techdocs.zebra.com/datawedge/latest/guide/output/keystroke/ "Keystroke Output - Zebra Technologies TechDocs"
[6]: https://sps-support.honeywell.com/s/article/How-to-use-the-Barcode-Data-Intent?utm_source=chatgpt.com "How to use the Barcode Data Intent?"
