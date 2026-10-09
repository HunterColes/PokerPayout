package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.payouts.NightSave
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutRowModel
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * PP-111: the champion's screen (S25), what the champion moment leads into: their name with a burst
 * of gold, what the paid places won, and the way to "Save this night" in History. It is the Payouts
 * tab's own picture of the night ([PayoutsUiState]), so the names and the amounts are the Bank's to
 * the cent, and saving is the Payouts tab's save.
 */
@Immutable
data class WinnerModel(
    val championName: String,
    /** 1st place's prize, in cents. */
    val prizeCents: Long,
    /** The champion's bounties: their own and the unclaimed (or the envelopes left), in cents. */
    val bountyCents: Long,
    /** Every paid place, 1st first, with who finished there. */
    val rows: List<PayoutRowModel>,
    val night: NightSave,
) {
    companion object {
        /** The screen for [payouts]; null until there is a champion. */
        fun from(payouts: PayoutsUiState): WinnerModel? {
            val first = payouts.rows.firstOrNull { it.place == 1 }
            val name = first?.holderName ?: payouts.bounties.championName ?: return null
            return WinnerModel(
                championName = name,
                prizeCents = first?.amountCents ?: 0L,
                bountyCents = payouts.bounties.championCents,
                rows = payouts.rows,
                night = payouts.night,
            )
        }
    }
}

/** What the champion's screen can do. */
enum class WinnerAction {
    /** "Save this night": into History, once (the night is over and everyone is paid). */
    SaveNight,

    /** Someone is still owed: the Bank, to pay them. */
    OpenBank,

    /** Saved: History, to see it. */
    OpenHistory,

    /** ✕ or Back: the clock again. */
    Close,
}

/** The champion's screen and what it does, for the table view to show over the clock. */
@Immutable
class WinnerPane(val model: WinnerModel, val onAction: (WinnerAction) -> Unit)

/**
 * The champion's screen, stateless: upright, one column (the champion's card, the payouts, then
 * saving); sideways, the card on the left and the rest on the right. Under Reduce motion the gold
 * burst doesn't play: the card's steady gold edge is the moment.
 */
@Composable
internal fun WinnerContent(model: WinnerModel, onAction: (WinnerAction) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PokerColors.PokerBlack)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ChampionCard(model, Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp), scroll = true)
                Column(Modifier.weight(1f)) {
                    WinnerTopRow(onAction)
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WinnerPayouts(model.rows)
                        WinnerSave(model.night, onAction)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                WinnerTopRow(onAction)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ChampionCard(model, Modifier.fillMaxWidth())
                    WinnerPayouts(model.rows)
                    WinnerSave(model.night, onAction)
                }
            }
        }
    }
}

/** ✕, back to the clock, top right. */
@Composable
private fun WinnerTopRow(onAction: (WinnerAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        PokerIconButton(
            icon = PokerIcons.Close,
            contentDescription = stringResource(R.string.winner_close),
            onClick = { onAction(WinnerAction.Close) },
            tint = PokerColors.CardWhite,
        )
    }
}

/**
 * The crown, the name, what they won; a burst of gold behind them as the screen opens. [scroll]
 * when the card has a height of its own (sideways), so the largest text still reads in full.
 */
@Composable
private fun ChampionCard(model: WinnerModel, modifier: Modifier = Modifier, scroll: Boolean = false) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    Box(
        modifier
            .clip(shape)
            .background(PokerColors.FeltDeep)
            .border(2.dp, PokerColors.PokerGold, shape),
        contentAlignment = Alignment.Center,
    ) {
        GoldBurst(Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(PokerIcons.Crown, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(CrownSize))
            PokerEyebrow(stringResource(R.string.winner_eyebrow), color = PokerColors.PokerGold)
            WithWidth(Modifier.fillMaxWidth()) { width ->
                val size = rememberFittedSize(model.championName, PokerType.DisplayL, width, NameCap)
                Text(
                    text = model.championName,
                    style = PokerType.DisplayL.copy(fontSize = size, lineHeight = size),
                    color = PokerColors.PokerGold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
            }
            if (model.prizeCents > 0L) {
                Text(
                    text = stringResource(R.string.winner_wins, money(model.prizeCents)),
                    style = PokerType.Title,
                    color = PokerColors.CardWhite,
                    textAlign = TextAlign.Center,
                )
            }
            if (model.bountyCents > 0L) {
                Text(
                    text = stringResource(R.string.winner_bounties, money(model.bountyCents)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Each paid place: "1st  Dana  $225". */
@Composable
private fun WinnerPayouts(rows: List<PayoutRowModel>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PokerEyebrow(stringResource(R.string.winner_payouts))
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    // TalkBack reads a row as one: "1st, Dana, $225"
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val first = row.place == 1
                Text(
                    text = ordinalOf(row.place),
                    style = PokerType.NumberM,
                    color = if (first) PokerColors.PokerGold else PokerColors.CardWhite,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                Text(
                    text = row.holderName.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = money(row.amountCents),
                    style = PokerType.NumberM,
                    color = if (first) PokerColors.PokerGold else PokerColors.CardWhite,
                )
            }
        }
    }
}

/**
 * The way to History: "Save this night" once everyone is paid; before that, to the Bank to pay
 * them; once saved, where it went.
 */
@Composable
private fun WinnerSave(night: NightSave, onAction: (WinnerAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (night) {
            NightSave.Offered -> PokerButton(
                text = stringResource(R.string.payouts_save_night),
                onClick = { onAction(WinnerAction.SaveNight) },
                icon = PokerIcons.Trophy,
                modifier = Modifier.fillMaxWidth(),
            )
            NightSave.Saved -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        PokerIcons.Check,
                        contentDescription = null,
                        tint = PokerColors.Live,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.payouts_night_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.weight(1f),
                    )
                }
                PokerButton(
                    text = stringResource(R.string.winner_open_history),
                    onClick = { onAction(WinnerAction.OpenHistory) },
                    variant = PokerButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NightSave.NotOver -> {
                Text(
                    text = stringResource(R.string.winner_pay_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                )
                PokerButton(
                    text = stringResource(R.string.winner_open_bank),
                    onClick = { onAction(WinnerAction.OpenBank) },
                    icon = PokerIcons.Wallet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A burst of gold from the crown, once, as the screen opens: rays and sparks fly out and fade in
 * [BURST_MILLIS]. Under Reduce motion nothing moves (the card's gold edge stays). It draws nothing
 * once done, takes no touch, and TalkBack skips it.
 */
@Composable
private fun GoldBurst(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing)) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val center = Offset(size.width / 2, BurstTop.toPx() + CrownSize.toPx() / 2)
        val reach = size.maxDimension * BURST_REACH * t
        val fade = 1f - t
        repeat(RAYS) { i ->
            val angle = 2 * PI * i / RAYS
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color = PokerColors.PokerGold.copy(alpha = fade * RAY_ALPHA),
                start = center + direction * (reach * RAY_START),
                end = center + direction * reach,
                strokeWidth = RayWidth.toPx(),
                cap = StrokeCap.Round,
            )
        }
        repeat(SPARKS) { i ->
            val angle = 2 * PI * (i + SPARK_TURN) / SPARKS
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            // Each spark flies its own way out: three distances, in turn
            val out = SPARK_NEAR + (i % SPARK_LANES) * SPARK_STEP
            drawCircle(
                color = PokerColors.PokerGold.copy(alpha = fade),
                radius = SparkRadius.toPx() * (1f - t / 2),
                center = center + direction * (reach * out),
            )
        }
    }
}

private val CrownSize = 48.dp
private val NameCap = 56.dp

/** The crown's top in the card: its padding. */
private val BurstTop = 20.dp
private val RayWidth = 3.dp
private val SparkRadius = 3.dp
private const val BURST_MILLIS = 1_400
private const val BURST_REACH = 0.6f
private const val RAYS = 16
private const val RAY_START = 0.35f
private const val RAY_ALPHA = 0.8f
private const val SPARKS = 24
private const val SPARK_TURN = 0.5
private const val SPARK_LANES = 3
private const val SPARK_NEAR = 0.7f
private const val SPARK_STEP = 0.2f
