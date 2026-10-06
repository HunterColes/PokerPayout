package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/** What a button does, which sets its colours. At most one [Primary] per screen. */
enum class PokerButtonVariant {
    /** Gold with dark text: the screen's main action ("Start clock"). */
    Primary,

    /** Felt with a gold label and a FeltEdge outline. */
    Secondary,

    /** A gold label alone: dismiss and low-emphasis actions ("Cancel", "Keep"). */
    Text,

    /** DangerFill with white text, for confirmed destructive actions ("Clear 9 players"). */
    Destructive,

    /** A Danger outline: destructive but routine and undoable ("Out"). */
    DestructiveOutline,
}

/** Regular is 52 dp tall. Small is a 40 dp button inside a 48 dp touch box. */
enum class PokerButtonSize { Regular, Small }

/**
 * The app's button. The label is a verb and its object ("Knock out Theo", never "Okay"); it wraps
 * rather than truncates at large font sizes, and the button grows to fit.
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun PokerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PokerButtonVariant = PokerButtonVariant.Primary,
    size: PokerButtonSize = PokerButtonSize.Regular,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = buttonColors(variant, enabled)
    val metrics = if (size == PokerButtonSize.Regular) RegularMetrics else SmallMetrics
    val shape = RoundedCornerShape(metrics.corner)
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            // The whole 48 dp box takes the tap; the ripple stays on the visible button.
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = PokerDimens.MinTouch, minHeight = metrics.height)
            .clip(shape)
            .background(colors.container)
            .then(if (colors.outline != null) Modifier.border(colors.outline, shape) else Modifier)
            .indication(interactions, ripple(color = colors.content))
            .padding(horizontal = if (variant == PokerButtonVariant.Text) 12.dp else metrics.padding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(metrics.icon))
        }
        Text(
            text = text,
            color = colors.content,
            style = metrics.label,
            textAlign = TextAlign.Center,
        )
    }
}

private class ButtonColors(val container: Color, val content: Color, val outline: BorderStroke?)

private class ButtonMetrics(val height: Dp, val corner: Dp, val padding: Dp, val icon: Dp, val label: TextStyle)

private val RegularMetrics = ButtonMetrics(
    height = PokerDimens.ButtonHeight,
    corner = PokerDimens.CornerButton,
    padding = 18.dp,
    icon = 22.dp,
    label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
)

private val SmallMetrics = ButtonMetrics(
    height = PokerDimens.ButtonHeightSmall,
    corner = PokerDimens.CornerControl,
    padding = 14.dp,
    icon = 20.dp,
    label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
)

private fun buttonColors(variant: PokerButtonVariant, enabled: Boolean): ButtonColors = when {
    !enabled && variant == PokerButtonVariant.Text -> ButtonColors(Color.Transparent, PokerColors.ChalkDim, null)
    !enabled -> ButtonColors(PokerColors.FeltDeep, PokerColors.ChalkDim, null)
    else -> when (variant) {
        PokerButtonVariant.Primary -> ButtonColors(PokerColors.PokerGold, PokerColors.FeltDeep, null)
        PokerButtonVariant.Secondary ->
            ButtonColors(PokerColors.DarkGreen, PokerColors.PokerGold, BorderStroke(1.dp, PokerColors.FeltEdge))
        PokerButtonVariant.Text -> ButtonColors(Color.Transparent, PokerColors.PokerGold, null)
        PokerButtonVariant.Destructive -> ButtonColors(PokerColors.DangerFill, Color.White, null)
        PokerButtonVariant.DestructiveOutline ->
            ButtonColors(Color.Transparent, PokerColors.Danger, BorderStroke(1.5.dp, PokerColors.Danger))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "PokerButton", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerButtonPreview() {
    PokerPreviewPage {
        PokerStage {
            PokerButton("Start clock", onClick = {}, icon = PokerIcons.Play, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PokerButton("Use 20-min levels", {}, variant = PokerButtonVariant.Secondary, size = PokerButtonSize.Small)
                PokerButton("Cancel", {}, variant = PokerButtonVariant.Text, size = PokerButtonSize.Small)
                PokerButton("Out", {}, variant = PokerButtonVariant.DestructiveOutline, size = PokerButtonSize.Small)
                PokerButton("Clear 9 players", {}, variant = PokerButtonVariant.Destructive, size = PokerButtonSize.Small)
            }
        }
        PokerStage {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PokerButton("Deal the river", {}, variant = PokerButtonVariant.Secondary, icon = PokerIcons.Cards)
                PokerButton("Settle up", {}, enabled = false)
                PokerButton("Undo", {}, variant = PokerButtonVariant.Text, icon = PokerIcons.Undo)
            }
        }
    }
}
