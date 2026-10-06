package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tournament.R

// The bubble (who is in the money, and how close the rest are) and the bounties card.

/**
 * How far the money is: every finishing place from last to 1st, decided ones marked (out, or
 * cashed), the paid places in gold, and how many more have to go out before everyone left is paid.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BubbleCard(bubble: BubbleModel) {
    if (bubble.seats.isEmpty()) return
    PayoutsCard {
        CardHeading {
            PokerEyebrow(stringResource(R.string.payouts_bubble), Modifier.align(Alignment.CenterVertically))
            val status = when {
                bubble.nextOutPlace == null -> stringResource(R.string.payouts_bubble_finished)
                bubble.moreOutToMoney > 0 -> pluralStringResource(
                    R.plurals.payouts_bubble_to_money,
                    bubble.moreOutToMoney,
                    bubble.moreOutToMoney
                )
                else -> stringResource(R.string.payouts_bubble_in_money)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
        }
        bubble.seats.chunked(SEATS_PER_ROW).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.forEach { Seat(it, Modifier.weight(1f)) }
                repeat(SEATS_PER_ROW - line.size) { Box(Modifier.weight(1f)) }
            }
        }
        val worst = ordinalOf(bubble.seats.first().place)
        Text(
            text = bubble.nextOutPlace?.let { stringResource(R.string.payouts_bubble_caption, worst, ordinalOf(it)) }
                ?: stringResource(R.string.payouts_bubble_caption_done, worst),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

private const val SEATS_PER_ROW = 10

@Composable
private fun Seat(seat: SeatModel, modifier: Modifier = Modifier) {
    val place = ordinalOf(seat.place)
    val description = stringResource(
        when (seat.state) {
            SeatState.Out -> R.string.payouts_seat_out
            SeatState.Cashed -> R.string.payouts_seat_cashed
            SeatState.Bubble -> R.string.payouts_seat_bubble
            SeatState.Money -> R.string.payouts_seat_money
        },
        place,
    )
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dot = Modifier
            .size(24.dp)
            .clip(CircleShape)
        when (seat.state) {
            SeatState.Out -> Box(dot.background(PokerColors.DangerWash), contentAlignment = Alignment.Center) {
                Icon(PokerIcons.Close, contentDescription = null, tint = PokerColors.Danger, modifier = Modifier.size(14.dp))
            }
            SeatState.Cashed -> Box(dot.background(PokerColors.PokerGold), contentAlignment = Alignment.Center) {
                Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.FeltDeep, modifier = Modifier.size(14.dp))
            }
            SeatState.Bubble -> Box(dot.border(1.5.dp, PokerColors.FeltEdge, CircleShape))
            SeatState.Money -> Box(dot.background(PokerColors.PokerGold))
        }
        // A fixed size, like the dot above it: 10 seats share a row even at the largest font size.
        val size = with(LocalDensity.current) { 12.dp.toSp() }
        Text(
            text = seat.place.toString(),
            style = PokerType.NumberS.copy(fontSize = size, lineHeight = size),
            color = PokerColors.Chalk
        )
    }
}

/**
 * Bounties claimed so far ("Dana · Ben" $5), what is still out there, and once there is a champion
 * their own bounty plus the unclaimed ones (PP-055). Food is noted as kept out of the prize pool.
 * Progressive and mystery bounties (PP-035) say so in the title, and each claim is what it paid.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BountiesCard(bounties: BountiesModel) {
    if (bounties.perHeadCents <= 0L && bounties.foodCents <= 0L) return
    PayoutsCard {
        if (bounties.perHeadCents > 0L) {
            CardHeading {
                PokerEyebrow(bountiesTitle(bounties), Modifier.align(Alignment.CenterVertically))
                if (bounties.stillOutCents > 0L) {
                    Text(
                        text = stringResource(R.string.payouts_bounties_still_out, formatMoney(bounties.stillOutCents)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.Chalk,
                    )
                }
            }
            if (bounties.claims.isEmpty() && bounties.championName == null) {
                Text(
                    stringResource(R.string.payouts_bounties_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk
                )
            }
            bounties.claims.forEach { claim -> BountyLine(claim.name, claim.victims.joinToString(", "), claim.cents) }
            bounties.championName?.takeIf { bounties.championCents > 0L }?.let { name ->
                val detail = if (bounties.mode == BountyMode.MYSTERY) {
                    R.string.payouts_bounty_champion_mystery
                } else {
                    R.string.payouts_bounty_champion
                }
                BountyLine(name, stringResource(detail), bounties.championCents)
            }
        }
        if (bounties.foodCents > 0L) {
            Text(
                text = stringResource(R.string.payouts_food, formatMoney(bounties.foodCents)),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}

/** "Bounties · $5 a head", "Progressive bounties · $5 to start", "Mystery bounties · 9 envelopes". */
@Composable
private fun bountiesTitle(bounties: BountiesModel): String = when (bounties.mode) {
    BountyMode.STANDARD -> stringResource(R.string.payouts_bounties_title, formatMoney(bounties.perHeadCents))
    BountyMode.PROGRESSIVE -> stringResource(R.string.payouts_bounties_title_pko, formatMoney(bounties.perHeadCents))
    BountyMode.MYSTERY ->
        pluralStringResource(R.plurals.payouts_bounties_title_mystery, bounties.envelopes, bounties.envelopes)
}

@Composable
private fun BountyLine(name: String, detail: String, cents: Long) {
    Row(
        modifier = Modifier.heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(name)
                withStyle(SpanStyle(color = PokerColors.Chalk)) { append(" · $detail") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
        Text(text = formatMoney(cents), style = PokerType.NumberM.copy(fontSize = 18.sp), color = PokerColors.CardWhite)
    }
}
