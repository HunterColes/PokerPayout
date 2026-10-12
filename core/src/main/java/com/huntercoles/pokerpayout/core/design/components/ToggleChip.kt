package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * A labelled on/off chip ("Four-colour deck" in Odds, a player's "Folded" in Side pots).
 *
 * On is shown three ways: a FeltHigh fill, a gold check and white text. The chip is 36 dp tall
 * inside a 48 dp touch box. TalkBack reads it as a checkbox: "Four-colour deck, checked".
 *
 * @param icon shown while off (a plus by default).
 * @param spokenLabel what TalkBack reads instead of [label] where the chip repeats on every row and needs
 *   its row's name ("Dana folded"); null reads [label].
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = PokerIcons.Plus,
    spokenLabel: String? = null,
) {
    val colors = toggleColors(checked)
    val shape = CircleShape
    val interactions = remember { MutableInteractionSource() }
    val description = spokenLabel ?: label
    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                interactionSource = interactions,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            // Read as one checkbox; the checked state is announced by the role.
            .semantics(mergeDescendants = true) { contentDescription = description }
            .minimumInteractiveComponentSize()
            .heightIn(min = PokerDimens.ChipToggleHeight)
            .clip(shape)
            .background(colors.container)
            .drawBehind { drawChipEdge(colors.edge) }
            .indication(interactions, ripple(color = colors.content))
            .padding(start = 9.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (checked) PokerIcons.Check else icon,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(17.dp),
        )
        Text(text = label, color = colors.content, style = ChipLabel)
    }
}

private val ChipLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

private class ToggleColors(val container: Color, val content: Color, val icon: Color, val edge: Color?)

private fun toggleColors(checked: Boolean): ToggleColors =
    if (checked) {
        ToggleColors(PokerColors.FeltHigh, PokerColors.CardWhite, PokerColors.PokerGold, null)
    } else {
        ToggleColors(Color.Transparent, PokerColors.Chalk, PokerColors.Chalk, PokerColors.FeltEdge)
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChipEdge(color: Color?) {
    if (color == null) return
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.height / 2),
        style = Stroke(width = stroke),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "ToggleChip", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun ToggleChipPreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Four-colour deck", checked = true, onCheckedChange = {}, icon = PokerIcons.Diamond)
                ToggleChip("Four-colour deck", checked = false, onCheckedChange = {}, icon = PokerIcons.Diamond)
                ToggleChip("Folded", checked = true, onCheckedChange = {}, icon = PokerIcons.Cards)
                ToggleChip("Folded", checked = false, onCheckedChange = {}, icon = PokerIcons.Cards)
                ToggleChip("Add-on", checked = false, onCheckedChange = {})
            }
        }
    }
}
