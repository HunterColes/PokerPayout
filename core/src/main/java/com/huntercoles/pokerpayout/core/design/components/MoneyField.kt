package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.huntercoles.pokerpayout.core.utils.MoneyInput
import java.util.Locale

/**
 * An amount in dollars and cents on a [PokerField]: "$" in front, the decimal keyboard, and the
 * money rules of [MoneyInput].
 *
 * - The field owns its text while focused. Nothing coming back in rewrites it, so the cursor stays
 *   where it is (typing 1, 2, ., 5, 0 gives "12.50"; v1.1.12 gave "120.5", B10). Every amount typed
 *   goes to [onValueChange] (null while the field is empty).
 * - Leaving the field commits: focus moving away, Done, hardware Enter, or the field going away
 *   (a sheet closing, a tab switch). [onCommit] gets the amount, null for an empty field, and only
 *   when it differs from [valueCents].
 * - Not focused, it shows [valueCents] in the device locale ("12,50" in Germany); null shows empty
 *   and 0 shows "0", so "not entered" and "nothing" look different.
 *
 * [description] names the field for TalkBack, with its label by default ("Dana's chips at the end").
 */
@Suppress("LongParameterList") // a component API: one parameter per option
@Composable
fun MoneyField(
    valueCents: Long?,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (Long?) -> Unit = {},
    onCommit: (Long?) -> Unit = {},
    supportingText: String? = null,
    isError: Boolean = false,
    description: String = label,
) {
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var text by remember { mutableStateOf(moneyText(valueCents, locale)) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val latestValue by rememberUpdatedState(valueCents)
    val latestOnCommit by rememberUpdatedState(onCommit)
    // Typed since the last commit: what makes leaving the field a commit, once.
    var edited by remember { mutableStateOf(false) }

    fun commit() {
        if (!edited) return
        edited = false
        val cents = MoneyInput.parseCents(text)
        if (cents != latestValue) latestOnCommit(cents)
    }

    LaunchedEffect(focused) {
        if (!focused) commit()
    }
    // Outside changes (Undo, a reset) show once nobody is typing.
    LaunchedEffect(valueCents, focused, locale) {
        val saved = moneyText(valueCents, locale)
        if (!focused && text != saved) text = saved
    }
    // A sheet closing or a tab switch disposes the field without a focus change; keep what was typed.
    DisposableEffect(Unit) {
        onDispose { commit() }
    }

    PokerField(
        value = text,
        onValueChange = { typed ->
            if (MoneyInput.isAcceptable(typed) && typed != text) {
                text = typed
                edited = true
                onValueChange(MoneyInput.parseCents(typed))
            }
        },
        label = label,
        modifier = modifier,
        prefix = "$",
        supportingText = supportingText,
        isError = isError,
        keyboardType = KeyboardType.Decimal,
        interactionSource = interactions,
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        fieldModifier = Modifier
            .leaveOnHardwareEnter {
                commit()
                focusManager.clearFocus(force = true)
            }
            .semantics { contentDescription = description },
    )
}

/** What the field shows for [cents] when not being typed in: empty for null, "0" for nothing. */
private fun moneyText(cents: Long?, locale: Locale): String = cents?.let { MoneyInput.format(it, locale) }.orEmpty()
