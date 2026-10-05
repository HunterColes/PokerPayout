package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerTextFieldDefaults
import com.huntercoles.pokerpayout.core.utils.MoneyInput
import java.util.Locale

/** What a money field shows for [cents]: nothing for 0, so the label reads as a placeholder. */
private fun moneyFieldText(cents: Long, locale: Locale): String =
    if (cents == 0L) "" else MoneyInput.format(cents, locale)

/**
 * A money input that keeps the text the user is typing.
 *
 * - The field owns its text while focused. Only parsed amounts go out ([onValueChange]); nothing
 *   coming back in rewrites the text, so the cursor stays put (typing 1, 2, ., 5, 0 gives "12.50",
 *   not "120.5" as in v1.1.12).
 * - An empty field sends nothing while typing: clearing a field to retype it is not a command.
 * - Leaving the field (focus loss, Done, tab switch) commits: empty means 0. [onCommit] gets the
 *   final amount, then the text shows the saved amount in the device locale ("12,50" in Germany).
 * - Both '.' and ',' work as the decimal separator, whatever the locale.
 */
@Composable
internal fun MoneyTextField(
    valueCents: Long,
    onAmount: (cents: Long, committed: Boolean) -> Unit,
    label: String,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var text by remember { mutableStateOf(TextFieldValue(moneyFieldText(valueCents, locale))) }
    var isFocused by remember { mutableStateOf(false) }
    val currentOnAmount by rememberUpdatedState(onAmount)

    fun commit() {
        if (text.text.isBlank()) currentOnAmount(0L, false)
        currentOnAmount(MoneyInput.parseCents(text.text) ?: 0L, true)
    }

    // Outside changes (reset, an amount the ViewModel kept) show up once the user isn't typing.
    LaunchedEffect(valueCents, isFocused, locale) {
        val saved = moneyFieldText(valueCents, locale)
        if (!isFocused && text.text != saved) {
            text = TextFieldValue(saved, selection = TextRange(saved.length))
        }
    }

    // A tab switch disposes the field without a focus change; don't lose what was typed.
    DisposableEffect(Unit) {
        onDispose { if (isFocused) commit() }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            if (MoneyInput.isAcceptable(typed.text)) {
                val textChanged = typed.text != text.text
                text = typed
                if (textChanged) MoneyInput.parseCents(typed.text)?.let { currentOnAmount(it, false) }
            }
        },
        label = {
            Text(
                label,
                color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        enabled = !isLocked,
        colors = PokerTextFieldDefaults.colors(isLocked = isLocked),
        modifier = modifier
            .clearFocusOnEnter { focusManager.clearFocus(force = true) }
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) commit()
                isFocused = focusState.isFocused
            }
    )
}

/** Hardware Enter leaves the field (which commits it) instead of typing a newline. */
private fun Modifier.clearFocusOnEnter(clearFocus: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
    if (isEnter && event.type == KeyEventType.KeyUp) clearFocus()
    isEnter
}

/** Sends typed amounts with [onTyped] and committed ones with [onCommitted]. */
internal fun amountHandler(onTyped: (Long) -> Unit, onCommitted: (Long) -> Unit = {}) =
    { cents: Long, committed: Boolean -> if (committed) onCommitted(cents) else onTyped(cents) }
