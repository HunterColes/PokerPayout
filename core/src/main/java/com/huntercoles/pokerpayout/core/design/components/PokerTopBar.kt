package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * The one header every screen uses (replaces the app bar, `PokerHeaderWithAction`, the emoji
 * titles and the floating reset button).
 *
 * The title is gold Barlow and never ellipsizes: at large font scales it wraps and the bar grows.
 * The subtitle is live state ("Level 6 of 9 · running") and may ellipsize. Put up to three
 * [PokerIconButton]s in [actions] and the rest behind a "More" menu. Sub-screens pass [onBack].
 * The bar pads itself below the status bar once the app draws edge to edge.
 */
@Composable
fun PokerTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = PokerDimens.TopBarHeight)
            .padding(start = if (onBack == null) PokerDimens.Gutter else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            PokerIconButton(
                icon = PokerIcons.Back,
                contentDescription = stringResource(R.string.design_back),
                onClick = onBack,
                tint = PokerColors.CardWhite,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 0.dp else 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = PokerType.Title,
                color = PokerColors.PokerGold,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { mayTruncate = true },
                )
            }
        }
        actions()
    }
}

/**
 * A 48 dp icon-only button. [contentDescription] is required: TalkBack reads it, and an icon
 * alone never carries the meaning on screen either (pair it with a visible label where you can).
 * The tint is Chalk by default, gold for the screen's live action, CardWhite for "back".
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun PokerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PokerColors.Chalk,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(PokerDimens.MinTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else PokerColors.ChalkDim,
        )
    }
}

@Preview(name = "PokerTopBar", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerTopBarPreview() {
    PokerPreviewPage(gutter = false) {
        PokerStage {
            PokerTopBar(title = "Clock", subtitle = "Level 6 of 9 · running") {
                PokerIconButton(PokerIcons.Bell, "Mute chime", onClick = {})
                PokerIconButton(PokerIcons.Fullscreen, "Table view", onClick = {})
                PokerIconButton(PokerIcons.More, "More options", onClick = {})
            }
        }
        PokerStage {
            PokerTopBar(title = "Odds", subtitle = "Flop · exact · 990 runouts", onBack = {}) {
                PokerIconButton(PokerIcons.Restart, "New hand", onClick = {})
            }
        }
        PokerStage {
            PokerTopBar(
                title = "Hand ranks",
                subtitle = "Ten hands, strongest first, with how often each is dealt in seven cards",
                onBack = {},
            )
        }
    }
}
