package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import kotlinx.coroutines.delay

/** Held past the long-press timeout, a stepper button repeats this often. */
internal const val STEPPER_REPEAT_INTERVAL_MS = 80L

/**
 * A count with gold minus and plus buttons (players, places, chips), instead of a slider. Each
 * button is 48 x 48 dp, stops at the ends of [range], and repeats while held. [label] names the
 * count for TalkBack: "Decrease Players", "Increase Players".
 */
@Composable
fun PokerStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    val current by rememberUpdatedState(value)
    Row(
        modifier = modifier
            .heightIn(min = PokerDimens.ControlHeight)
            .clip(shape)
            .background(PokerColors.DarkGreen)
            .border(1.dp, PokerColors.FeltEdge, shape),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            icon = PokerIcons.Minus,
            description = stringResource(R.string.design_decrease, label),
            enabled = value > range.first,
            onStep = { if (current > range.first) onValueChange(current - 1) },
        )
        Text(
            text = value.toString(),
            style = PokerType.NumberL.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = PokerColors.CardWhite,
            textAlign = TextAlign.Center,
            // Measured after the buttons, so they keep their 48 dp and the number takes what is left.
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(min = 44.dp),
        )
        StepButton(
            icon = PokerIcons.Plus,
            description = stringResource(R.string.design_increase, label),
            enabled = value < range.last,
            onStep = { if (current < range.last) onValueChange(current + 1) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }
    val step by rememberUpdatedState(onStep)
    LaunchedEffect(repeating, pressed, enabled) {
        if (repeating && pressed && enabled) {
            while (true) {
                delay(STEPPER_REPEAT_INTERVAL_MS)
                step()
            }
        } else {
            repeating = false
        }
    }
    Box(
        modifier = Modifier
            .size(PokerDimens.MinTouch)
            .combinedClickable(
                interactionSource = interactions,
                indication = ripple(bounded = false, radius = 22.dp, color = PokerColors.PokerGold),
                enabled = enabled,
                role = Role.Button,
                onLongClick = {
                    step()
                    repeating = true
                },
                onClick = step,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) PokerColors.PokerGold else PokerColors.ChalkDim,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Preview(name = "PokerStepper", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerStepperPreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            PokerStepper(value = 9, onValueChange = {}, range = 2..30, label = "Players")
            PokerStepper(value = 2, onValueChange = {}, range = 2..30, label = "Players")
            PokerStepper(value = 3, onValueChange = {}, range = 1..9, label = "Places paid", modifier = Modifier)
        }
    }
}
