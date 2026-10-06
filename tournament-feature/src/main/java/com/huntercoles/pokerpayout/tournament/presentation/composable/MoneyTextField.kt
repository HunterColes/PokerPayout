package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.utils.MoneyInput
import java.util.Locale

/** What a money field shows for [cents]: nothing for 0, so the label reads as a placeholder. */
private fun moneyFieldText(cents: Long, locale: Locale): String =
    if (cents == 0L) "" else MoneyInput.format(cents, locale)

/**
 * An amount from a money field: typed ([committed] false) or final ([committed] true, the field was
 * left). [centsBeforeEdit] is the saved amount when the user started editing, so a question about
 * the final amount can offer to put that back (backspacing "15" passes through "1").
 */
internal data class MoneyEntry(val cents: Long, val committed: Boolean, val centsBeforeEdit: Long)

/**
 * A money input that keeps the text the user is typing.
 *
 * - The field owns its text while focused. Only parsed amounts go out ([onAmount]); nothing coming
 *   back in rewrites the text, so the cursor stays put (typing 1, 2, ., 5, 0 gives "12.50", not
 *   "120.5" as in v1.1.12).
 * - An empty field sends nothing while typing: clearing a field to retype it is not a command.
 * - Leaving the field (focus loss, Done, tab switch) commits: empty means 0. Then the text shows
 *   the saved amount in the device locale ("12,50" in Germany).
 * - Both '.' and ',' work as the decimal separator, whatever the locale.
 *
 * It looks like the other setup fields: [label] above the box ("Buy-in"), a "$" before the amount.
 */
// The branches are the field's text/focus/commit rules listed above, kept together on purpose.
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun MoneyTextField(
    valueCents: Long,
    onAmount: (MoneyEntry) -> Unit,
    label: String,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var text by remember { mutableStateOf(TextFieldValue(moneyFieldText(valueCents, locale))) }
    var isFocused by remember { mutableStateOf(false) }
    var centsBeforeEdit by remember { mutableLongStateOf(valueCents) }
    val currentOnAmount by rememberUpdatedState(onAmount)

    fun send(cents: Long, committed: Boolean) = currentOnAmount(MoneyEntry(cents, committed, centsBeforeEdit))

    fun commit() {
        if (text.text.isBlank()) send(0L, committed = false)
        send(MoneyInput.parseCents(text.text) ?: 0L, committed = true)
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

    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            if (MoneyInput.isAcceptable(typed.text)) {
                val textChanged = typed.text != text.text
                text = typed
                if (textChanged) MoneyInput.parseCents(typed.text)?.let { send(it, committed = false) }
            }
        },
        enabled = !isLocked,
        singleLine = true,
        textStyle = SetupFieldStyle.Number,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        modifier = modifier
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) commit()
                if (!isFocused && focusState.isFocused) centsBeforeEdit = valueCents
                isFocused = focusState.isFocused
            },
        decorationBox = { inner ->
            SetupFieldDecoration(
                FieldDecor(label, prefix = CURRENCY_PREFIX),
                focused = focused,
                isEmpty = text.text.isEmpty(),
                inner = inner,
            )
        },
    )
}

/** The mockups' money prefix; amounts are typed in the device's own number format. */
private const val CURRENCY_PREFIX = "$"

/** Sends typed amounts to [onTyped] and committed ones to [onCommitted]. */
internal fun amountHandler(onTyped: (Long) -> Unit, onCommitted: (MoneyEntry) -> Unit = {}) =
    { entry: MoneyEntry -> if (entry.committed) onCommitted(entry) else onTyped(entry.cents) }
