package vn.delfi.xcloudwms.core.ui.components

import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import vn.delfi.xcloudwms.core.scanner.ScannerSubmitMode

@Stable
data class PdaScanFieldSettings(
    val blockSoftKeyboard: Boolean = true,
    val submitMode: ScannerSubmitMode = ScannerSubmitMode.ENTER,
    val allowManualInputFallback: Boolean = false,
)

val LocalPdaScanFieldSettings = compositionLocalOf { PdaScanFieldSettings() }

@Composable
fun PdaScanField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keepFocused: Boolean = true,
    focusKey: Any? = Unit,
    placeholder: String = label,
    supportingText: String? = null,
    onSubmit: (() -> Unit)? = null,
    onMoveNext: (() -> Unit)? = null,
    settingsOverride: PdaScanFieldSettings? = null,
) {
    val settings = settingsOverride ?: LocalPdaScanFieldSettings.current
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val latestOnMoveNext by rememberUpdatedState(onMoveNext)
    val latestKeepFocused by rememberUpdatedState(keepFocused)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSettings by rememberUpdatedState(settings)
    val fieldRef = remember { mutableStateOf<AppCompatEditText?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(enabled, keepFocused, focusKey, settings, fieldRef.value) {
        val field = fieldRef.value ?: return@LaunchedEffect
        if (!enabled) {
            return@LaunchedEffect
        }
        repeat(3) { attempt ->
            if (attempt > 0) {
                kotlinx.coroutines.delay(if (attempt == 1) 90L else 220L)
            }
            runCatching {
                field.requestFocus()
                if (!settings.softKeyboardEnabled) {
                    field.hideKeyboard()
                }
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
        )

        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val containerColor = MaterialTheme.colorScheme.surface.toArgb()
        val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
        val focusedOutlineColor = MaterialTheme.colorScheme.primary.toArgb()
        val disabledOutlineColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
        val horizontalPadding = with(density) { 16.dp.roundToPx() }
        val verticalPadding = with(density) { 14.dp.roundToPx() }
        val radius = with(density) { 16.dp.toPx() }
        val strokeWidth = with(density) { 1.dp.roundToPx() }
        val focusedStrokeWidth = with(density) { 2.dp.roundToPx() }
        val clearButtonSpace = with(density) { 48.dp.roundToPx() }

        Box(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                factory = { context ->
                    AppCompatEditText(context).apply {
                        fieldRef.value = this
                        isSingleLine = true
                        maxLines = 1
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        hint = placeholder
                        setTextColor(textColor)
                        setHintTextColor(hintColor)
                        setPadding(
                            horizontalPadding,
                            verticalPadding,
                            horizontalPadding + clearButtonSpace,
                            verticalPadding,
                        )
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        setTextIsSelectable(false)
                        updateIme(settings)
                        updateBorder(
                            hasFocus = false,
                            enabled = enabled,
                            containerColor = containerColor,
                            outlineColor = outlineColor,
                            focusedOutlineColor = focusedOutlineColor,
                            disabledOutlineColor = disabledOutlineColor,
                            radius = radius,
                            strokeWidth = strokeWidth,
                            focusedStrokeWidth = focusedStrokeWidth,
                        )
                        doAfterTextChanged { editable ->
                            val newValue = editable?.toString().orEmpty()
                            if (newValue != latestValue) {
                                latestOnValueChange(newValue)
                            }
                        }
                        setOnFocusChangeListener { view, hasFocus ->
                            updateBorder(
                                hasFocus = hasFocus,
                                enabled = latestEnabled,
                                containerColor = containerColor,
                                outlineColor = outlineColor,
                                focusedOutlineColor = focusedOutlineColor,
                                disabledOutlineColor = disabledOutlineColor,
                                radius = radius,
                                strokeWidth = strokeWidth,
                                focusedStrokeWidth = focusedStrokeWidth,
                            )
                            if (hasFocus && !latestSettings.softKeyboardEnabled) {
                                hideKeyboard()
                            } else if (
                                latestEnabled &&
                                latestKeepFocused &&
                                !hasFocus &&
                                latestSettings.submitMode != ScannerSubmitMode.TAB
                            ) {
                                view.post {
                                    requestFocus()
                                    if (!latestSettings.softKeyboardEnabled) {
                                        hideKeyboard()
                                    }
                                }
                            }
                        }
                        setOnEditorActionListener { _, actionId, event ->
                            if (shouldHandleSubmit(actionId = actionId, event = event)) {
                                dispatchSubmit(
                                    settings = latestSettings,
                                    field = this,
                                    onSubmit = latestOnSubmit,
                                    onMoveNext = latestOnMoveNext,
                                )
                                true
                            } else {
                                false
                            }
                        }
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action != KeyEvent.ACTION_UP) {
                                return@setOnKeyListener false
                            }
                            if (
                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                keyCode == KeyEvent.KEYCODE_TAB
                            ) {
                                dispatchSubmit(
                                    settings = latestSettings,
                                    field = this,
                                    onSubmit = latestOnSubmit,
                                    onMoveNext = latestOnMoveNext,
                                )
                                true
                            } else {
                                false
                            }
                        }
                    }
                },
                update = { editText ->
                    fieldRef.value = editText
                    editText.isEnabled = enabled
                    editText.isFocusable = enabled
                    editText.isFocusableInTouchMode = enabled
                    editText.hint = placeholder
                    editText.setPadding(
                        horizontalPadding,
                        verticalPadding,
                        horizontalPadding + clearButtonSpace,
                        verticalPadding,
                    )
                    editText.updateIme(settings)
                    editText.updateBorder(
                        hasFocus = editText.hasFocus(),
                        enabled = enabled,
                        containerColor = containerColor,
                        outlineColor = outlineColor,
                        focusedOutlineColor = focusedOutlineColor,
                        disabledOutlineColor = disabledOutlineColor,
                        radius = radius,
                        strokeWidth = strokeWidth,
                        focusedStrokeWidth = focusedStrokeWidth,
                    )
                    if (editText.text?.toString().orEmpty() != value) {
                        editText.setText(value)
                        editText.setSelection(editText.text?.length ?: 0)
                    }
                    if (!settings.softKeyboardEnabled && editText.hasFocus()) {
                        editText.hideKeyboard()
                    }
                },
            )

            if (enabled && value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        latestOnValueChange("")
                        fieldRef.value?.let { field ->
                            field.requestFocus()
                            if (!latestSettings.softKeyboardEnabled) {
                                field.hideKeyboard()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Xoá nội dung",
                    )
                }
            }
        }

        supportingText?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PdaScanFieldSettings.softKeyboardEnabled: Boolean
    get() = !blockSoftKeyboard || allowManualInputFallback

private fun shouldHandleSubmit(
    actionId: Int,
    event: KeyEvent?,
): Boolean {
    val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
    val isEnterKey =
        event?.action == KeyEvent.ACTION_DOWN &&
            (
                event.keyCode == KeyEvent.KEYCODE_ENTER ||
                    event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                )
    val isTabKey =
        event?.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_TAB
    return isDoneAction || isEnterKey || isTabKey
}

private fun dispatchSubmit(
    settings: PdaScanFieldSettings,
    field: AppCompatEditText,
    onSubmit: (() -> Unit)?,
    onMoveNext: (() -> Unit)?,
) {
    when (settings.submitMode) {
        ScannerSubmitMode.ENTER -> onSubmit?.invoke()
        ScannerSubmitMode.TAB -> onMoveNext?.invoke()
        ScannerSubmitMode.NONE -> Unit
    }
    if (settings.submitMode != ScannerSubmitMode.TAB) {
        field.post {
            field.requestFocus()
            if (!settings.softKeyboardEnabled) {
                field.hideKeyboard()
            }
        }
    }
}

private fun AppCompatEditText.updateIme(settings: PdaScanFieldSettings) {
    showSoftInputOnFocus = settings.softKeyboardEnabled
    imeOptions = if (settings.softKeyboardEnabled && settings.submitMode == ScannerSubmitMode.ENTER) {
        EditorInfo.IME_ACTION_DONE
    } else {
        EditorInfo.IME_ACTION_NONE
    }
}

private fun AppCompatEditText.updateBorder(
    hasFocus: Boolean,
    enabled: Boolean,
    containerColor: Int,
    outlineColor: Int,
    focusedOutlineColor: Int,
    disabledOutlineColor: Int,
    radius: Float,
    strokeWidth: Int,
    focusedStrokeWidth: Int,
) {
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(containerColor)
        val borderColor = when {
            !enabled -> disabledOutlineColor
            hasFocus -> focusedOutlineColor
            else -> outlineColor
        }
        setStroke(if (hasFocus) focusedStrokeWidth else strokeWidth, borderColor)
    }
}

private fun View.hideKeyboard() {
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java) ?: return
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}
