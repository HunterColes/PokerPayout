package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.EquityBar
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.OddsInsights
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizIntent
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion

/** The board: its street ("FLOP") and its cards, or a line saying none are out yet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuizBoard(state: EquityQuizUiState) {
    val board = state.deal?.board.orEmpty()
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.quiz_board), note = streetName(board.size))
        if (board.isEmpty()) {
            Text(stringResource(R.string.quiz_no_board), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                board.forEach { CardFace(it.toPlayingCard()) }
            }
        }
    }
}

/** Below this width a hand's panel can't hold its cards and its odds side by side with the others. */
private val PanelMinWidth = 140.dp

/** From this font scale on, the hands stack in one column (design spec, section 6). */
private const val LARGE_TEXT = 1.5f

/**
 * The hands, side by side when there's room for each and stacked when not. Asked who's ahead, each
 * hand is the button to pick it; once answered, every hand shows its real equity.
 */
@Composable
internal fun QuizHands(state: EquityQuizUiState, onIntent: (EquityQuizIntent) -> Unit) {
    val deal = state.deal ?: return
    val names = LocalContext.current.resources.getStringArray(R.array.quiz_hand_names)
    val large = LocalDensity.current.fontScale >= LARGE_TEXT
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val sideBySide = !large && (maxWidth - gap * (deal.hands.size - 1)) / deal.hands.size >= PanelMinWidth
        val panel: @Composable (Int, Modifier) -> Unit = { index, modifier ->
            val pick = { onIntent(EquityQuizIntent.Answer(QuizAnswer.Hand(index))) }
            HandPanel(state, index, names[index], onPick = pick, modifier)
        }
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                deal.hands.indices.forEach { panel(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                deal.hands.indices.forEach { panel(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/**
 * One hand: its name, its two cards and what it holds so far ("Top pair, jacks"), and once
 * answered its equity, as a number and a bar. The hands that were ahead turn gold-washed with an
 * "Ahead" pill; the one picked says "Your pick". Asked who's ahead, the whole panel is the button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandPanel(state: EquityQuizUiState, index: Int, name: String, onPick: () -> Unit, modifier: Modifier) {
    val deal = state.deal ?: return
    val cards = deal.hands.getOrNull(index) ?: return
    val labels = rememberOddsLabels()
    val ahead = state.revealed && index in state.leaders
    val picked = (state.answer as? QuizAnswer.Hand)?.index == index
    val canPick = state.question == QuizQuestion.Leader && state.answer == null
    val equity = state.equity?.getOrNull(index)?.takeIf { state.revealed }
    val description = handDescription(name, cards.map(labels::cardName), equity, ahead, picked)
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val pickLabel = stringResource(R.string.quiz_pick_hand, name)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (ahead) PokerColors.GoldWash else PokerColors.FeltGreen)
            .then(
                when {
                    picked -> Modifier.border(2.dp, PokerColors.PokerGold, shape)
                    // A hand to pick is outlined like any other control
                    canPick -> Modifier.border(1.dp, PokerColors.FeltEdge, shape)
                    else -> Modifier
                },
            )
            .then(
                if (canPick) {
                    Modifier.clickable(onClickLabel = pickLabel, role = Role.Button, onClick = onPick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = PokerDimens.MinTouch)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (ahead) PokerPill(stringResource(R.string.quiz_ahead))
            if (picked) PokerPill(stringResource(R.string.quiz_your_pick), tone = PokerPillTone.Outline)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEach { CardFace(it.toPlayingCard()) }
        }
        Text(
            text = labels.hand(OddsInsights.describe(cards, deal.board)),
            style = MaterialTheme.typography.bodySmall,
            color = if (ahead) PokerColors.CardWhite else PokerColors.Chalk,
        )
        if (equity != null) {
            EquityText(equity, estimate = false, style = equityStyle(EquitySize))
            val win = state.wins?.getOrNull(index) ?: equity
            EquityBar(win = (win / PERCENT).toFloat(), tie = ((equity - win) / PERCENT).toFloat())
        }
    }
}

/** TalkBack's one stop per hand: "Hand A: Ace of spades, King of hearts, 63.4%, Ahead, Your pick". */
@Composable
private fun handDescription(name: String, cards: List<String>, equity: Double?, ahead: Boolean, picked: Boolean): String {
    var text = stringResource(R.string.quiz_hand_description, name, cards[0], cards[1])
    if (equity != null) text = stringResource(R.string.quiz_hand_equity_description, text, OddsFormat.oneDecimal(equity))
    if (ahead) text = stringResource(R.string.quiz_description_more, text, stringResource(R.string.quiz_ahead))
    if (picked) text = stringResource(R.string.quiz_description_more, text, stringResource(R.string.quiz_your_pick))
    return text
}

private val EquitySize = 24.sp
private const val PERCENT = 100.0
