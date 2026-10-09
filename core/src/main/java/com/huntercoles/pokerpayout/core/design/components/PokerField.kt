package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A labelled number field: the label sits above the box, so it never truncates. The value uses
 * the number face; a [prefix] (a money symbol, "$") and [suffix] ("min") are in Chalk, and so is a
 * [trailingSymbol] (a money symbol written after the amount, "€", in the prefix's size). Focus is a 2 dp gold edge.
 * An error is a 2 dp Danger edge, and [supportingText] then says how to fix it ("Can't be made
 * from 25s. Try 5,000.").
 *
 * Stateless: the caller owns [value]. Callers that commit on blur (money fields, B18) watch focus
 * through [interactionSource]; [keyboardActions] and [fieldModifier] (the text field itself, for
 * key handling and its TalkBack name) are theirs too. A field for words rather than numbers (a
 * name) passes [textStyle] to type in the body face.
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun PokerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    supportingText: String? = null,
    trailingSymbol: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Number,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    val edge = when {
        isError -> FieldEdge(2.dp, PokerColors.Danger)
        focused -> FieldEdge(2.dp, PokerColors.PokerGold)
        else -> FieldEdge(1.dp, PokerColors.FeltEdge)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = FieldLabel, color = PokerColors.Chalk)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .semantics { if (isError && supportingText != null) error(supportingText) },
            textStyle = textStyle?.copy(color = PokerColors.CardWhite) ?: FieldValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(PokerColors.PokerGold),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .heightIn(min = PokerDimens.ControlHeight)
                        .clip(shape)
                        .background(PokerColors.DarkGreen)
                        .border(edge.width, edge.color, shape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prefix != null) Text(prefix, style = FieldPrefix)
                    Row(Modifier.weight(1f)) { innerTextField() }
                    if (trailingSymbol != null) Text(trailingSymbol, style = FieldPrefix)
                    if (suffix != null) Text(suffix, style = FieldSuffix, color = PokerColors.Chalk)
                }
            },
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = FieldHelp,
                color = if (isError) PokerColors.Danger else PokerColors.Chalk,
            )
        }
    }
}

private class FieldEdge(val width: Dp, val color: Color)

private val FieldLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp)
private val FieldValue = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp, color = PokerColors.CardWhite)
private val FieldPrefix = FieldValue.copy(fontWeight = FontWeight.Medium, color = PokerColors.Chalk)
private val FieldSuffix = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)
private val FieldHelp = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)

/** A preview-only interaction source that reports focus, to show the focused state without a cursor. */
@Composable
private fun rememberFocusedInteractionSource(): MutableInteractionSource = remember { FocusedInteractionSource() }

private class FocusedInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = flowOf(FocusInteraction.Focus())

    override suspend fun emit(interaction: Interaction) = Unit

    override fun tryEmit(interaction: Interaction): Boolean = true
}

@Preview(name = "PokerField", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerFieldPreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PokerField("40", {}, label = "Buy-in", prefix = AppCurrency.DOLLAR.symbol, modifier = Modifier.weight(1f))
                PokerField(
                    "5",
                    {},
                    label = "Bounty",
                    prefix = AppCurrency.DOLLAR.symbol,
                    modifier = Modifier.weight(1f),
                    interactionSource = rememberFocusedInteractionSource(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PokerField(
                    "5,010",
                    {},
                    label = "Starting stack",
                    supportingText = "Can't be made from 25s. Try 5,000.",
                    isError = true,
                    modifier = Modifier.weight(1f),
                )
                PokerStepperField(modifier = Modifier.weight(1f))
            }
            PokerField("20", {}, label = "Level length", suffix = "min", supportingText = "9 levels before the first break")
        }
    }
}

@Composable
private fun PokerStepperField(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Players", style = FieldLabel, color = PokerColors.Chalk)
        PokerStepper(value = 9, onValueChange = {}, range = 2..30, label = "Players", modifier = Modifier.fillMaxWidth())
    }
}
