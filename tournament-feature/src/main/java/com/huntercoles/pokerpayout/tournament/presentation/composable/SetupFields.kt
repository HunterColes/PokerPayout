package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * The Tournament setup's fields, in the design system's look (PokerField: label above, a 48 dp
 * DarkGreen box, FeltEdge outline, 2 dp gold edge when focused). The label is drawn inside the text
 * field's own decoration, so it is part of the field for TalkBack ("Buy-in, 40") and for the device
 * tour's selectors, as it was with the Material fields these replace.
 */
internal object SetupFieldStyle {
    val Label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp)
    val Number = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp, color = PokerColors.CardWhite)
    val Text = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = PokerColors.CardWhite)
    val Prefix = Number.copy(fontWeight = FontWeight.Medium, color = PokerColors.Chalk)
    val Suffix = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, color = PokerColors.Chalk)
    val Shape = RoundedCornerShape(PokerDimens.CornerControl)
}

/**
 * What goes around a setup field's text: the label, then the box with an optional prefix and suffix.
 * [symbolAfter] is a money symbol written after the amount ("€", PP-114), as large as a prefix.
 */
internal class FieldDecor(
    val label: String,
    val prefix: String? = null,
    val suffix: String? = null,
    val placeholder: String? = null,
    val symbolAfter: String? = null,
)

@Composable
internal fun SetupFieldDecoration(decor: FieldDecor, focused: Boolean, isEmpty: Boolean, inner: @Composable () -> Unit) {
    val edgeWidth = if (focused) 2.dp else 1.dp
    val edgeColor = if (focused) PokerColors.PokerGold else PokerColors.FeltEdge
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(decor.label, style = SetupFieldStyle.Label, color = PokerColors.Chalk)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.ControlHeight)
                .clip(SetupFieldStyle.Shape)
                .background(PokerColors.DarkGreen)
                .border(edgeWidth, edgeColor, SetupFieldStyle.Shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            decor.prefix?.let { Text(it, style = SetupFieldStyle.Prefix) }
            Box(Modifier.weight(1f)) {
                if (isEmpty && decor.placeholder != null) {
                    Text(decor.placeholder, style = SetupFieldStyle.Text, color = PokerColors.ChalkDim)
                }
                inner()
            }
            decor.symbolAfter?.let { Text(it, style = SetupFieldStyle.Prefix) }
            decor.suffix?.let { Text(it, style = SetupFieldStyle.Suffix) }
        }
    }
}

/**
 * A whole-number setup field (hours, minutes, chips) that commits when it is left: focus moves
 * away, Done, or hardware Enter (on release, so the release can't click whatever gets focus next).
 * Out-of-range or empty input puts the saved value back. Unfocused, thousands are grouped ("5,000").
 */
@Suppress("LongParameterList") // a field: value, commit, label, size, unit and range
@Composable
internal fun SetupNumberField(
    value: Int,
    onCommit: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    range: IntRange = 1..MAX_NUMBER,
) {
    val focusManager = LocalFocusManager.current
    val formatter = rememberChipFormatter()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var text by remember { mutableStateOf(TextFieldValue(formatter.format(value))) }
    var wasFocused by remember { mutableStateOf(false) }

    fun commit() {
        val parsed = text.text.filter { it.isDigit() }.take(MAX_DIGITS).toIntOrNull()
        if (parsed != null && parsed >= range.first) onCommit(parsed.coerceAtMost(range.last))
    }

    // Outside changes (a fix, a reset, a refused mid-game change) show once the field isn't being typed in.
    LaunchedEffect(value, focused) {
        if (!focused) text = TextFieldValue(formatter.format(value))
    }
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            if (typed.text.all { it.isDigit() } && typed.text.length <= MAX_DIGITS) text = typed
        },
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    val digits = value.toString()
                    text = TextFieldValue(digits, selection = TextRange(digits.length))
                }
                if (!state.isFocused && wasFocused) commit()
                wasFocused = state.isFocused
            }
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) },
        textStyle = SetupFieldStyle.Number,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        decorationBox = { inner ->
            SetupFieldDecoration(FieldDecor(label, suffix = suffix), focused, isEmpty = text.text.isEmpty(), inner = inner)
        },
    )
}

/**
 * One choice from a short list ("Every 4", "End of L4", "From L5"), in a menu under the field. The
 * current one is gold, bold and ticked in the menu, and TalkBack hears it as selected.
 */
@Suppress("LongParameterList") // label, options, selection, wording, pick, modifier
@Composable
internal fun <T> SetupSelectField(
    label: String,
    options: List<T>,
    selected: T,
    optionText: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { expanded = true },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = SetupFieldStyle.Label, color = PokerColors.Chalk)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PokerDimens.ControlHeight)
                    .clip(SetupFieldStyle.Shape)
                    .background(PokerColors.DarkGreen)
                    .border(1.dp, PokerColors.FeltEdge, SetupFieldStyle.Shape)
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(optionText(selected), style = SetupFieldStyle.Number, modifier = Modifier.weight(1f))
                Icon(PokerIcons.ChevronDown, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PokerColors.DarkGreen,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionText(option),
                            color = if (isSelected) PokerColors.PokerGold else PokerColors.CardWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                    trailingIcon = if (isSelected) {
                        { Icon(PokerIcons.Check, null, tint = PokerColors.PokerGold, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private const val MAX_DIGITS = 9
private const val MAX_NUMBER = 999_999_999
