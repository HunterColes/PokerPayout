package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.LocalShellSnackbars
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSnackbarHost
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.MoneyStage

// PP-135: knockouts on the full-screen clock. The panel itself is the Bank's (TableKnockouts).

/**
 * The table view's Knock out button, beside pause: a label and a skull, a 48 dp target. With large
 * text on a phone the label would squeeze the clock's digits out of the screen, so it is the skull
 * alone there, in a circle like pause and exit (TalkBack still reads "Knock out").
 */
@Composable
internal fun KnockOutButton(onClick: () -> Unit) {
    val label = stringResource(R.string.table_knock_out)
    val compact = LocalDensity.current.fontScale > LABEL_UP_TO_FONT_SCALE &&
        LocalConfiguration.current.screenWidthDp < LABEL_ALWAYS_FROM_DP
    if (!compact) {
        PokerButton(
            text = label,
            onClick = onClick,
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Skull,
        )
        return
    }
    Box(
        modifier = Modifier
            .size(PokerDimens.MinTouch)
            .clip(CircleShape)
            .background(PokerColors.FeltDeep)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PokerIcons.Skull, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(22.dp))
    }
}

/**
 * "On the bubble" or "In the money"; nothing before the bubble. The table view shows it beside the
 * players left, the upright clock with its other state pills ("Paused").
 */
@Composable
internal fun MoneyStagePill(stage: MoneyStage, modifier: Modifier = Modifier) {
    when (stage) {
        MoneyStage.BUBBLE -> PokerPill(stringResource(R.string.table_bubble), modifier, tone = PokerPillTone.Danger)
        MoneyStage.IN_THE_MONEY -> PokerPill(stringResource(R.string.table_in_the_money), modifier, tone = PokerPillTone.Gold)
        MoneyStage.NONE -> Unit
    }
}

/**
 * The app's snackbars on the table view, which has the whole window and so no shell snackbar host
 * around it: bottom left, over the table's numbers, clear of the controls (and sideways of the
 * knockout panel, which takes the right half), so Undo after a knockout is in reach and the time
 * stays in view.
 */
@Composable
internal fun BoxWithConstraintsScope.TableSnackbars() {
    val host = LocalShellSnackbars.current ?: return
    val share = if (maxWidth > maxHeight) SNACKBAR_SHARE_SIDEWAYS else SNACKBAR_SHARE_UPRIGHT
    PokerSnackbarHost(
        hostState = host,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 12.dp, bottom = 4.dp)
            .width((maxWidth * share).coerceAtMost(SnackbarMaxWidth)),
    )
}

/** The button keeps its label up to this text size, and from this window width at any size. */
private const val LABEL_UP_TO_FONT_SCALE = 1.3f
private const val LABEL_ALWAYS_FROM_DP = 840

private const val SNACKBAR_SHARE_SIDEWAYS = 0.5f
private const val SNACKBAR_SHARE_UPRIGHT = 0.6f
private val SnackbarMaxWidth = 600.dp
