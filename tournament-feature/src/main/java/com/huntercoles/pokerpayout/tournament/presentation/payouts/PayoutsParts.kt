package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tournament.R
import java.util.Locale
import kotlin.math.roundToInt

/** A card's eyebrow with its status at the other end; the status moves under it when the line is full. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardHeading(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A felt card, as the mockups' `.card`. */
@Composable
internal fun PayoutsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = PokerDimens.SpacingDefault, vertical = PokerDimens.SpacingMedium),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
        content = content,
    )
}

/**
 * What each place pays, with the winner once decided ("Still playing" until then) and a bar for its
 * share of the pool, then "Adds up to $450" (with a house rake, PP-145: "With the $20 house rake:
 * $400 taken in.") and the places stepper. The share is the rounded amount's, so the percentages
 * describe the money actually handed over, not the raw weights.
 */
@Composable
internal fun PlacesCard(state: PayoutsUiState, onIntent: (PayoutsIntent) -> Unit) {
    PayoutsCard {
        PayoutRows(state.rows)
        HorizontalDivider(color = PokerColors.FeltLine)
        AddsUp(state)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
        ) {
            val placesLabel = stringResource(R.string.payouts_places_stepper)
            Text(
                placesLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.Chalk,
                modifier = Modifier.weight(1f)
            )
            PokerStepper(
                value = state.places,
                onValueChange = { onIntent(PayoutsIntent.SetPlaces(it)) },
                range = if (state.isLocked) state.places..state.places else 1..state.maxPlaces.coerceAtLeast(1),
                label = placesLabel,
            )
        }
        if (state.places != state.recommendedPlaces && !state.isLocked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.payouts_recommend,
                        state.recommendedPlaces,
                        state.playerCount,
                        state.recommendedPlaces,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                    modifier = Modifier.weight(1f),
                )
                PokerButton(
                    text = stringResource(R.string.payouts_pay_recommended, state.recommendedPlaces),
                    onClick = { onIntent(PayoutsIntent.SetPlaces(state.recommendedPlaces)) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                )
            }
        }
    }
}

/**
 * "✓ Adds up to $450" under the rows; with a house rake (PP-145) a line under it says the places and
 * the rake make what was taken in, to the cent: "With the $22.50 house rake: $450 taken in."
 */
@Composable
private fun AddsUp(state: PayoutsUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall)) {
        if (state.addsUp) {
            Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.Live, modifier = Modifier.size(StatusIconSize))
        }
        Text(
            text = stringResource(R.string.payouts_adds_up, formatMoney(state.pool.prizePoolCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
        )
    }
    if (state.table.rakeCents > 0L) {
        Text(
            text = stringResource(
                R.string.payouts_rake_taken_in,
                formatMoney(state.table.rakeCents),
                formatMoney(state.table.takenInCents),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

/**
 * The night is over and everyone is paid (PP-037): "Save this night" puts it in History, once. Once
 * saved, the card says where to find it.
 */
@Composable
internal fun SaveNightCard(night: NightSave, onIntent: (PayoutsIntent) -> Unit) {
    PayoutsCard {
        if (night == NightSave.Saved) {
            Row(horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall)) {
                Icon(
                    PokerIcons.Check,
                    contentDescription = null,
                    tint = PokerColors.Live,
                    modifier = Modifier.padding(top = 2.dp).size(StatusIconSize),
                )
                Text(
                    text = stringResource(R.string.payouts_night_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.payouts_night_over),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
            PokerButton(
                text = stringResource(R.string.payouts_save_night),
                onClick = { onIntent(PayoutsIntent.SaveNight) },
                icon = PokerIcons.Trophy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The size of the small status icons on this tab: the lock, and the checks on "Adds up" and "Saved". */
internal val StatusIconSize = 18.dp

/** The table's rows: place, winner (or "Still playing"), amount, and the share bar under them. */
@Composable
internal fun PayoutRows(rows: List<PayoutRowModel>) {
    val placeWidth = rememberPlaceWidth(rows)
    rows.forEachIndexed { index, row ->
        if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
        PayoutRow(row, placeWidth)
    }
}

/**
 * The place column's width: the widest of the rows' places ("1st", "10th") at the user's font size,
 * and at least [PlaceWidth], so the names and bars line up down the card at any font size.
 */
@Composable
private fun rememberPlaceWidth(rows: List<PayoutRowModel>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val places = rows.map { ordinalOf(it.place) }
    return remember(places, density) {
        val widest = places.maxOfOrNull { measurer.measure(it, PokerType.NumberAmount, maxLines = 1).size.width } ?: 0
        maxOf(with(density) { widest.toDp() }, PlaceWidth)
    }
}

@Composable
private fun PayoutRow(row: PayoutRowModel, placeWidth: Dp) {
    val first = row.place == 1
    val holder = row.holderName ?: stringResource(R.string.payouts_still_playing)
    val share = shareText(row.sharePercent)
    // Place, name and amount share a baseline; the share bar starts under the name at any font size.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PokerDimens.SpacingSmall)
            // TalkBack reads the row as one: "1st, Still playing, $225, 50%".
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = ordinalOf(row.place),
            style = PokerType.NumberAmount,
            color = if (first) PokerColors.PokerGold else PokerColors.CardWhite,
            modifier = Modifier
                .width(placeWidth)
                .alignBy(FirstBaseline),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .alignBy(FirstBaseline),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NameAndAmount(
                holder = holder,
                amount = formatMoney(row.amountCents),
                nameColor = if (row.holderName != null) PokerColors.CardWhite else PokerColors.Chalk,
                amountColor = if (first) PokerColors.PokerGold else PokerColors.CardWhite,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
            ) {
                ShareBar(row.sharePercent, Modifier.weight(1f))
                Text(
                    text = share,
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 34.dp),
                )
            }
        }
    }
}

/** The place column's narrowest ("1st" at the default font size fits with room to spare). */
private val PlaceWidth = 44.dp

/** "29%"; one decimal for a share under 10% ("4.5%"). */
@Composable
private fun shareText(percent: Double): String {
    val text = if (percent < SMALL_SHARE) {
        String.format(Locale.US, "%.1f", percent).removeSuffix(".0")
    } else {
        percent.roundToInt().toString()
    }
    return stringResource(R.string.payouts_share_percent, text)
}

private const val SMALL_SHARE = 10.0
private const val PERCENT = 100f

@Composable
private fun ShareBar(percent: Double, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(shape)
            .background(PokerColors.FeltDeep),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent.toFloat() / PERCENT).coerceIn(0f, 1f))
                .height(8.dp)
                .background(PokerColors.PokerGold, shape),
        )
    }
}
