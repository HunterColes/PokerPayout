package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf

/** The badges' diameter: a 34 dp disc, inside a 48 dp touch box where it is tappable. */
val PlaceBadgeSize: Dp = 34.dp

/**
 * A finishing place: "8th" on a DangerFill disc for a player knocked out, or the champion's crown in
 * a DarkGold ring. Replaces the Orbitron place number and the 👑 emoji. The ordinal is the text, so
 * the place never depends on colour; it stays a fixed size at large font scales, like card ranks.
 */
@Composable
fun PlaceBadge(place: Int, modifier: Modifier = Modifier, champion: Boolean = false) {
    val description = if (champion) {
        stringResource(R.string.place_badge_champion)
    } else {
        stringResource(R.string.place_badge_finished, ordinalOf(place))
    }
    Box(
        modifier = modifier
            .size(PlaceBadgeSize)
            .clip(CircleShape)
            .then(
                if (champion) {
                    Modifier.border(1.5.dp, PokerColors.DarkGold, CircleShape)
                } else {
                    Modifier.background(PokerColors.DangerFill)
                },
            )
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (champion) {
            Icon(PokerIcons.Crown, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(18.dp))
        } else {
            FixedSizeText(ordinalOf(place))
        }
    }
}

/** The ordinal at 13.5 sp whatever the font scale: it has to fit the 34 dp disc. */
@Composable
private fun FixedSizeText(text: String) {
    Text(
        text = text,
        color = PokerColors.CardWhite,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = PokerType.NumberS.copy(fontSize = BadgeText.fixedSp(), lineHeight = BadgeText.fixedSp()),
    )
}

private val BadgeText = 13.5.dp

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "PlaceBadge", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PlaceBadgePreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceBadge(place = 1, champion = true)
                PlaceBadge(place = 2)
                PlaceBadge(place = 3)
                PlaceBadge(place = 8)
                PlaceBadge(place = 9)
                PlaceBadge(place = 11)
                PlaceBadge(place = 23)
                PlaceBadge(place = 30)
            }
        }
    }
}
