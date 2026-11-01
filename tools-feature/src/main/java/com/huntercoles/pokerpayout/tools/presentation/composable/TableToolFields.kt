package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.MAX_CHIP_DIGITS
import com.huntercoles.pokerpayout.tools.presentation.MAX_PLAYER_NAME

/**
 * A player's name in a table tool, in the app's field style. Blank shows "Player [number]" in
 * Chalk, which is also the name the tool uses. TalkBack: "Player 2 name".
 */
@Composable
internal fun PlayerNameField(number: Int, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.table_tools_name_label, number)
    OwnTextField(
        value = value,
        onChange = { onChange(it.take(MAX_PLAYER_NAME)) },
        placeholder = stringResource(R.string.table_tools_default_player, number),
        description = description,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/**
 * A whole number of chips: digits only, at most nine, shown grouped ("12,000") as the results show
 * them. Empty is null, which a tool treats as "not typed yet". [placeholder] shows while it is empty;
 * [description] names it for TalkBack ("Chips Dana put in").
 */
@Composable
internal fun ChipsField(
    value: Long?,
    onChange: (Long?) -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    OwnTextField(
        value = value?.toString().orEmpty(),
        onChange = { typed -> onChange(typed.toLongOrNull()) },
        placeholder = placeholder,
        description = description,
        textStyle = PokerType.NumberM, // chip counts in the number face
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier,
        accept = { typed -> typed.length <= MAX_CHIP_DIGITS && typed.all(Char::isDigit) },
        display = GroupedDigits,
    )
}

/**
 * Typed digits shown with the results' grouping ("1200" reads "1,200"); the cursor keeps its place
 * among the digits. What is typed and saved stays plain digits.
 */
private object GroupedDigits : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = digits.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let { chips(it.toLong()) }.orEmpty()
        // Where each typed digit sits in what's shown; a separator never counts as a digit
        val at = shown.indices.filter { shown[it].isDigit() }
        if (shown.isEmpty() || at.size != digits.length) return TransformedText(text, OffsetMapping.Identity)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset >= at.size) shown.length else at[offset]

            override fun transformedToOriginal(offset: Int): Int = at.count { it < offset }
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/**
 * A single-line field that owns its text while focused, so fast typing never races the value
 * coming back (the cursor stays put); the value replaces the text only while nobody is typing
 * (New hand, Start over, Undo). Done and hardware Enter leave the field.
 */
@Suppress("LongParameterList") // one field for names and numbers: what differs between them
@Composable
private fun OwnTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    description: String,
    textStyle: TextStyle,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier,
    accept: (String) -> Boolean = { true },
    display: VisualTransformation = VisualTransformation.None,
) {
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value, focused) { if (!focused && value != text) text = value }
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            if (typed != text && accept(typed)) {
                text = typed
                onChange(typed)
            }
        },
        modifier = modifier
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
            .semantics { contentDescription = description },
        textStyle = textStyle.copy(color = PokerColors.CardWhite),
        singleLine = true,
        visualTransformation = display,
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PokerDimens.ControlHeight)
                    .clip(shape)
                    .background(PokerColors.DarkGreen)
                    .border(if (focused) 2.dp else 1.dp, if (focused) PokerColors.PokerGold else PokerColors.FeltEdge, shape)
                    .padding(horizontal = PokerDimens.SpacingMedium, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(text = placeholder, style = textStyle, color = PokerColors.Chalk)
                    field()
                }
            }
        },
    )
}
