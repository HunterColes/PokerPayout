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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.utils.MoneyInput
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import java.util.Locale

/**
 * The house rake (PP-145), with the money: a percentage of the prize pool the host keeps, "0 %"
 * until set. It types like the money fields: "5" or "2.5" (either decimal mark), the "%" after it.
 *
 * - The field owns its text while focused; each rate typed goes out at once, so the prize pool note
 *   under the money follows it. Focusing a 0% field empties it, so the first digit replaces the 0.
 * - Leaving it (focus loss, Done, hardware Enter, a tab switch) commits, and the field then shows what
 *   was saved: the rate on the scale of 0% to 25% in 0.5% steps ([HouseRake.of]), so "30" comes back
 *   as "25" and "2.3" as "2.5". Empty means 0%; text that isn't a rate keeps what is saved.
 */
@Composable
internal fun RakeField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val mark = MoneyInput.decimalSeparator(locale)
    val saved = amounts.rake
    val field = remember { RakeText(saved.fieldText(mark)) }
    val latest by rememberUpdatedState(saved)
    val send by rememberUpdatedState { rake: HouseRake -> onSetup(TournamentConfigIntent.UpdateRake(rake)) }

    // Outside changes (a preset loaded, a reset) show up once the host isn't typing
    LaunchedEffect(saved, field.isFocused, mark) { field.showSaved(saved, mark) }

    // A tab switch disposes the field without a focus change; don't lose what was typed
    DisposableEffect(Unit) {
        onDispose { if (field.isFocused) send(field.rateOr(latest)) }
    }

    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(
        value = field.text,
        onValueChange = { typed -> field.typed(typed)?.let(send) },
        singleLine = true,
        textStyle = SetupFieldStyle.Number,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        modifier = modifier
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
            .onFocusChanged { state -> field.focusChanged(state.isFocused, latest, mark)?.let(send) },
        decorationBox = { inner ->
            SetupFieldDecoration(
                FieldDecor(stringResource(R.string.setup_rake), symbolAfter = PERCENT),
                focused = focused,
                isEmpty = field.text.text.isEmpty(),
                inner = inner,
            )
        },
    )
}

/** The rake field's text and focus: what each keystroke and each focus change sends, if anything. */
private class RakeText(initial: String) {
    var text by mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
        private set
    var isFocused by mutableStateOf(false)
        private set

    /** What [typed] text is now, the rate to send while it reads as one; null for no send (empty, or "."). */
    fun typed(typed: TextFieldValue): HouseRake? {
        if (!HouseRake.isTypable(typed.text)) return null
        val changed = typed.text != text.text
        text = typed
        return HouseRake.parse(typed.text).takeIf { changed && typed.text.isNotEmpty() }
    }

    /** Focus came or went: leaving sends the rate (on the scale) and shows it; arriving at 0% empties the field. */
    fun focusChanged(nowFocused: Boolean, saved: HouseRake, mark: Char): HouseRake? {
        val left = isFocused && !nowFocused
        val rate = if (left) rateOr(saved).also { show(it, mark) } else null
        if (!isFocused && nowFocused && saved.isNone) text = TextFieldValue("")
        isFocused = nowFocused
        return rate
    }

    /** The rate the text holds, or [saved] when it holds none. */
    fun rateOr(saved: HouseRake): HouseRake = HouseRake.parse(text.text) ?: saved

    /** Shows [saved] unless the host is typing. */
    fun showSaved(saved: HouseRake, mark: Char) {
        if (!isFocused && text.text != saved.fieldText(mark)) show(saved, mark)
    }

    private fun show(rake: HouseRake, mark: Char) {
        val shown = rake.fieldText(mark)
        text = TextFieldValue(shown, selection = TextRange(shown.length))
    }
}

private const val PERCENT = "%"
