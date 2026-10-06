@file:Suppress("MatchingDeclarationName") // PokerPillTone is the one class; PokerPill the component

package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import java.util.Locale

/** A pill's colours. Colour backs the word up and never stands in for it. */
enum class PokerPillTone {
    /** Gold fill, dark text: "Favourite", "Break", "Now". */
    Gold,

    /** Live outline: "Balanced", "Running". */
    Live,

    /** Danger on DangerWash: "Final minute", "Out". */
    Danger,

    /** FeltHigh fill, white text: "Paused". */
    Muted,

    /** FeltEdge outline, Chalk text: "Exact". */
    Outline,
}

/**
 * A short state label in caps ("PAUSED", "FINAL MINUTE"). Always a word, sometimes with an icon.
 * It never truncates: keep the text short.
 */
@Composable
fun PokerPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PokerPillTone = PokerPillTone.Gold,
    icon: ImageVector? = null,
) {
    val (container, content, outline) = pillColors(tone)
    val shape = CircleShape
    Row(
        modifier = modifier
            .heightIn(min = PokerDimens.PillHeight)
            .clip(shape)
            .background(container)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text.uppercase(Locale.ROOT),
            color = content,
            style = PokerType.Eyebrow.copy(fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
            maxLines = 1,
        )
    }
}

/** The knockout count next to a player: a skull and the number, read as "2 knockouts". */
@Composable
fun KnockoutBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.design_knockouts, count, count)
    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(CircleShape)
            .background(PokerColors.FeltDeep)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PokerIcons.Skull, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(15.dp))
        Text(
            text = count.toString(),
            color = PokerColors.PokerGold,
            style = PokerType.NumberS.copy(fontSize = 14.sp, lineHeight = 16.sp),
        )
    }
}

private data class PillColors(val container: Color, val content: Color, val outline: Color?)

private fun pillColors(tone: PokerPillTone): PillColors = when (tone) {
    PokerPillTone.Gold -> PillColors(PokerColors.PokerGold, PokerColors.FeltDeep, null)
    PokerPillTone.Live -> PillColors(Color.Transparent, PokerColors.Live, PokerColors.Live)
    PokerPillTone.Danger -> PillColors(PokerColors.DangerWash, PokerColors.Danger, null)
    PokerPillTone.Muted -> PillColors(PokerColors.FeltHigh, PokerColors.CardWhite, null)
    PokerPillTone.Outline -> PillColors(Color.Transparent, PokerColors.Chalk, PokerColors.FeltEdge)
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "PokerPill", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerPillPreview() {
    PokerPreviewPage {
        PokerStage {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PokerPill("Favourite")
                PokerPill("Balanced", tone = PokerPillTone.Live, icon = PokerIcons.Check)
                PokerPill("Final minute", tone = PokerPillTone.Danger)
                PokerPill("Paused", tone = PokerPillTone.Muted)
                PokerPill("Exact", tone = PokerPillTone.Outline)
                KnockoutBadge(count = 1)
                PokerPill("Break", icon = PokerIcons.Coffee)
                PokerPill("Now")
                KnockoutBadge(count = 12)
            }
        }
    }
}
