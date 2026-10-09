package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.presentation.MomentBanner
import com.huntercoles.pokerpayout.tournament.presentation.MomentSlotState
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import kotlinx.coroutines.delay

/**
 * PP-111: the clock's place for the night's big moments, above the upright clock and across the
 * top of the table view. A moment (the bubble, in the money, the final table, heads-up) comes in as
 * a banner with a glint of gold and goes after [MomentTiming.SHOW_MILLIS]; under Reduce motion it
 * appears and goes without moving or glinting. It takes no touch and never covers the time or a
 * control: the clock makes room for it. TalkBack reads it as it comes.
 *
 * Once there is a champion and their screen is closed, a card here leads back to it.
 *
 * Builds on the players-left pills (PP-135): the pill says where the field stands all along; the
 * banner marks the moment it gets there. [style] is how much room it has ([MomentStyle]). Its
 * input is [MomentSlotState] alone, so it skips the clock's every-second tick.
 */
@Composable
internal fun MomentSlot(
    slot: MomentSlotState,
    onIntent: (TimerIntent) -> Unit,
    modifier: Modifier = Modifier,
    style: MomentStyle = MomentStyle.Card,
) {
    val reduced = LocalReducedMotion.current
    val moment = slot.moment
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(moment?.id) {
        val id = moment?.id ?: return@LaunchedEffect
        delay(MomentTiming.SHOW_MILLIS)
        send(TimerIntent.MomentSeen(id))
    }
    Column(modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = moment,
            contentKey = { it?.id },
            transitionSpec = { momentTransition(reduced) },
            label = "bigMoment",
        ) { shown ->
            if (shown != null) MomentCard(shown, glint = !reduced, style = style)
        }
        val champion = slot.champion
        if (moment == null && champion != null) ChampionCard(champion, style) { onIntent(TimerIntent.OpenWinner) }
    }
}

/** A banner drops in and fades; the clock below slides to make room. Under Reduce motion: a cut. */
private fun AnimatedContentTransitionScope<MomentBanner?>.momentTransition(reduced: Boolean): ContentTransform =
    if (reduced) {
        EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false) { _, _ -> snap() }
    } else {
        val enter = fadeIn(tween(MomentTiming.ENTER_MILLIS)) + slideInVertically(tween(MomentTiming.ENTER_MILLIS)) { -it / 2 }
        val exit = fadeOut(tween(MomentTiming.EXIT_MILLIS))
        enter togetherWith exit using SizeTransform(clip = true) { _, _ -> tween(MomentTiming.ENTER_MILLIS) }
    }

@Composable
private fun MomentCard(banner: MomentBanner, glint: Boolean, style: MomentStyle) {
    val accent = if (banner.moment == BigMoment.BUBBLE) PokerColors.Danger else PokerColors.PokerGold
    val wash = if (banner.moment == BigMoment.BUBBLE) PokerColors.DangerWash else PokerColors.GoldWash
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val sweep = remember(banner.id) { Animatable(if (glint) GLINT_FROM else GLINT_DONE) }
    LaunchedEffect(banner.id, glint) {
        if (glint) sweep.animateTo(GLINT_DONE, tween(MomentTiming.GLINT_MILLIS, MomentTiming.ENTER_MILLIS, LinearEasing))
    }
    val title = momentTitle(banner.moment)
    val line = momentLine(banner)
    Row(
        modifier = Modifier
            .slotGap(style)
            .clip(shape)
            .background(wash)
            .border(BorderWidth, accent, shape)
            .drawWithContent {
                drawContent()
                drawGlint(sweep.value, accent)
            }
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                // Brief, the line isn't on screen: TalkBack still reads it
                if (style == MomentStyle.Brief) contentDescription = "$title. $line"
            }
            .padding(horizontal = 14.dp, vertical = style.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(momentIcon(banner.moment), contentDescription = null, tint = accent, modifier = Modifier.size(style.icon))
        MomentWords(title, line.takeUnless { style == MomentStyle.Brief }, style, accent, Modifier.weight(1f))
    }
}

/** The title, and the line under it (a card) or beside it (a strip, wrapping when it must). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MomentWords(title: String, line: String?, style: MomentStyle, accent: Color, modifier: Modifier) {
    if (style == MomentStyle.Card) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PokerType.Title, color = accent)
            line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite) }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val titleStyle = if (style == MomentStyle.Brief) BriefTitle else CompactTitle
            Text(title, style = titleStyle, color = accent, modifier = Modifier.align(Alignment.CenterVertically))
            line?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** The gold glint's band at [at] (a share of the width) on its one pass; nothing once past. */
private fun ContentDrawScope.drawGlint(at: Float, accent: Color) {
    if (at >= GLINT_DONE) return
    val x = size.width * at
    val band = size.width * GLINT_BAND
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, accent.copy(alpha = GLINT_ALPHA), Color.Transparent),
            start = Offset(x - band, 0f),
            end = Offset(x + band, size.height),
        ),
    )
}

/**
 * How a moment shows: a [Card] above the upright clock; a [Strip] across the top of the table view;
 * [Brief] there when the window is short for its text size (a small phone on its side, large
 * text): the title alone, so the digits keep their room.
 */
enum class MomentStyle(val padding: Dp, val icon: Dp) {
    Card(padding = 12.dp, icon = 30.dp),
    Strip(padding = 6.dp, icon = 24.dp),
    Brief(padding = 3.dp, icon = 18.dp),
}

/** "Dana is the champion", and the way back to their screen ([BriefChampion] when the room is short). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionCard(name: String, style: MomentStyle, onOpen: () -> Unit) {
    if (style == MomentStyle.Brief) {
        BriefChampion(name, onOpen)
        return
    }
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    FlowRow(
        modifier = Modifier
            .slotGap(style)
            .clip(shape)
            .background(PokerColors.GoldWash)
            .border(BorderWidth, PokerColors.PokerGold, shape)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(PokerIcons.Crown, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.champion_card, name), style = CompactTitle, color = PokerColors.PokerGold)
        }
        PokerButton(
            text = stringResource(R.string.champion_card_open),
            onClick = onOpen,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/**
 * The champion's card in one short line, all of it the way back to their screen: the crown, the
 * name, "See the results". TalkBack reads "Dana is the champion" and the action.
 */
@Composable
private fun BriefChampion(name: String, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val words = stringResource(R.string.champion_card, name)
    val open = stringResource(R.string.champion_card_open)
    Row(
        modifier = Modifier
            .slotGap(MomentStyle.Brief)
            .clip(shape)
            .background(PokerColors.GoldWash)
            .border(BorderWidth, PokerColors.PokerGold, shape)
            .clickable(onClickLabel = open, role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics { contentDescription = words }
            .padding(horizontal = 14.dp, vertical = MomentStyle.Brief.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val crown = Modifier.size(MomentStyle.Brief.icon)
        Icon(PokerIcons.Crown, contentDescription = null, tint = PokerColors.PokerGold, modifier = crown)
        Text(name, style = BriefTitle, color = PokerColors.PokerGold, modifier = Modifier.weight(1f))
        Text(open, style = BriefTitle, color = PokerColors.CardWhite)
    }
}

/**
 * The gap between a card and the clock goes inside the slot, so an empty slot takes no room at
 * all: the clock is exactly where it was until a moment comes (upright: under the strip; the table
 * view: over the digits).
 */
private fun Modifier.slotGap(style: MomentStyle): Modifier = if (style == MomentStyle.Card) {
    fillMaxWidth().padding(top = SlotGap)
} else {
    fillMaxWidth().padding(bottom = SlotGapStrip)
}

/** How long a moment stays, and how it moves. */
internal object MomentTiming {
    /** On the clock this long, then it goes (the pills still say where the field stands). */
    const val SHOW_MILLIS = 12_000L
    const val ENTER_MILLIS = 350
    const val EXIT_MILLIS = 250

    /** The gold glint's one pass across the banner, once it is in. */
    const val GLINT_MILLIS = 900
}

private const val GLINT_FROM = -0.3f
private const val GLINT_DONE = 1.3f
private const val GLINT_BAND = 0.18f
private const val GLINT_ALPHA = 0.35f
private val BorderWidth = 1.5.dp
private val SlotGap = 12.dp
private val SlotGapStrip = 6.dp
private val CompactTitle = PokerType.Title.copy(fontSize = 22.sp, lineHeight = 26.sp)
private val BriefTitle = PokerType.Title.copy(fontSize = 15.sp, lineHeight = 18.sp)
