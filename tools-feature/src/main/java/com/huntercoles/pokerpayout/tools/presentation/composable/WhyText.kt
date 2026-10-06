@file:Suppress("MatchingDeclarationName") // the file is about the WhyText composable; WhyFacts is its input

package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.NextCardBreakdown
import com.huntercoles.pokerpayout.tools.poker.OddsInsights
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.presentation.Street

/** What "Why" explains: the hand, every seat's equity, who leads after each next card, and the [focus] seat. */
internal data class WhyFacts(
    val request: OddsRequest,
    val equity: List<Double>,
    val breakdown: NextCardBreakdown,
    val focus: Int,
)

/**
 * "Why" (S9): who is ahead now and with what, how many outs the focus seat has (named the way
 * people count them), and what that makes the hand. The state words and the outs are bold.
 */
@Composable
internal fun WhyText(facts: WhyFacts, labels: OddsLabels) {
    PokerEyebrow(stringResource(R.string.odds_why), color = PokerColors.PokerGold)
    Text(
        text = whyText(facts, labels, LocalContext.current.resources),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = PokerColors.CardWhite,
    )
}

/** The sentence itself, with its bold parts. Pure, for tests: everything comes from [res]. */
internal fun whyText(facts: WhyFacts, labels: OddsLabels, res: Resources): AnnotatedString {
    val request = facts.request
    val equity = facts.equity
    val breakdown = facts.breakdown
    val focus = facts.focus
    val leader = breakdown.currentLeader
    if (leader == null || leader == focus) return AnnotatedString(res.getString(R.string.odds_why_level))
    val focusHand = OddsInsights.describe(request.seats[focus].cards, request.board)
    val leaderHand = OddsInsights.describe(request.seats[leader].cards, request.board)
    val outs = breakdown.leadCount(focus)
    val outsText = res.getQuantityString(
        // On the flop two cards are to come, so the outs come twice.
        if (request.board.size == Street.FLOP.boardCards) R.plurals.odds_outs_twice else R.plurals.odds_outs,
        outs,
        outs,
    )
    val groups = labels.outs(OddsInsights.groupOuts(breakdown.outs(focus), focusHand.flushDrawSuit))
    val others = request.seats.indices.filter { it != focus && !request.seats[it].folded }
    val favourite = others.all { equity[focus] > equity[it] }
    val f = labels.player(focus)
    val l = labels.player(leader)
    val behind = res.getString(R.string.odds_why_behind_now)
    val ahead = res.getString(R.string.odds_why_ahead_now)
    val focusPhrase = labels.phrase(focusHand.made)
    val leaderPhrase = labels.phrase(leaderHand.made)
    val (plain, state) = when {
        outs == 0 -> res.getString(R.string.odds_why_no_outs, l, ahead, leaderPhrase, focusPhrase, f) to ahead
        favourite -> res.getString(R.string.odds_why_favourite, f, behind, focusPhrase, leaderPhrase, outsText, groups) to behind
        else -> res.getString(
            R.string.odds_why_holds,
            l, ahead, leaderPhrase, focusPhrase, f, outsText, groups, OddsFormat.oneDecimal(equity[focus]),
        ) to ahead
    }
    return bold(plain, listOfNotNull(state, outsText.takeIf { outs > 0 }))
}

/** [text] with the first occurrence of each of [parts] in semibold. */
private fun bold(text: String, parts: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    parts.forEach { part ->
        val at = text.indexOf(part)
        if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), at, at + part.length)
    }
}
