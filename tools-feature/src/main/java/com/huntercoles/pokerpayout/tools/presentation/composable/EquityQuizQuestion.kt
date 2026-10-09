package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizIntent
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.quiz.EquityRange
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizJudge
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion

/**
 * The question, then the answer. Asked who's ahead, the hands above are the buttons; asked how
 * often the first hand wins, five ranges are. Once answered: right or not, the number that
 * decided it, a note when it was too close to call, and Deal again. TalkBack hears the verdict.
 */
@Composable
internal fun QuizQuestionCard(state: EquityQuizUiState, onIntent: (EquityQuizIntent) -> Unit) {
    ChipSetSection {
        when {
            state.revealed -> QuizVerdict(state, onDealAgain = { onIntent(EquityQuizIntent.NextDeal) })
            state.working -> Text(
                text = stringResource(R.string.quiz_working),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.Chalk,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            else -> QuizAsk(state, onIntent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizAsk(state: EquityQuizUiState, onIntent: (EquityQuizIntent) -> Unit) {
    val first = LocalContext.current.resources.getStringArray(R.array.quiz_hand_names).first()
    val leader = state.question == QuizQuestion.Leader
    Text(
        text = if (leader) stringResource(R.string.quiz_question_leader) else stringResource(R.string.quiz_question_range, first),
        style = PokerType.Title,
        color = PokerColors.CardWhite,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(if (leader) R.string.quiz_hint_leader else R.string.quiz_hint_range),
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
    )
    if (!leader) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EquityRange.entries.forEach { range ->
                PokerButton(
                    text = rangeText(range),
                    onClick = { onIntent(EquityQuizIntent.Answer(QuizAnswer.InRange(range))) },
                    variant = PokerButtonVariant.Secondary,
                    size = PokerButtonSize.Small,
                )
            }
        }
    }
}

/** "Right!" or "Not this time", what decided it, and Deal again. */
@Composable
private fun QuizVerdict(state: EquityQuizUiState, onDealAgain: () -> Unit) {
    val right = state.right == true
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (right) PokerIcons.Check else PokerIcons.Close,
            contentDescription = null,
            tint = if (right) PokerColors.Live else PokerColors.Danger,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(if (right) R.string.quiz_right else R.string.quiz_wrong),
            style = PokerType.Title,
            color = if (right) PokerColors.Live else PokerColors.Danger,
        )
    }
    Text(text = verdictLine(state), style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
    val range = (state.answer as? QuizAnswer.InRange)?.range
    if (range != null) {
        Text(
            text = stringResource(R.string.quiz_your_range, rangeText(range)),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
    if (state.closeCall) {
        val close = if (state.question == QuizQuestion.Leader) R.string.quiz_close_leader else R.string.quiz_close_range
        Text(stringResource(close), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
    PokerButton(
        text = stringResource(R.string.quiz_deal_again),
        onClick = onDealAgain,
        icon = PokerIcons.Cards,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Hand B wins 63.4% of the time." or "Hand A wins 63.4%: 60–80%." */
@Composable
private fun verdictLine(state: EquityQuizUiState): String {
    val equity = state.equity.orEmpty()
    val names = LocalContext.current.resources.getStringArray(R.array.quiz_hand_names)
    return if (state.question == QuizQuestion.Leader) {
        val best = equity.indices.maxByOrNull { equity[it] } ?: 0
        stringResource(R.string.quiz_leader_explained, names[best], OddsFormat.oneDecimal(equity.getOrElse(best) { 0.0 }))
    } else {
        val first = equity.firstOrNull() ?: 0.0
        val ranges = QuizJudge.ranges(first).sortedBy { it.ordinal }.map { rangeText(it) }
        val counted = if (ranges.size > 1) {
            stringResource(R.string.quiz_ranges_or, ranges[0], ranges[1])
        } else {
            ranges.firstOrNull().orEmpty()
        }
        stringResource(R.string.quiz_range_explained, names[0], OddsFormat.oneDecimal(first), counted)
    }
}

/** "Under 20%", "20–40%", "Over 80%". */
@Composable
private fun rangeText(range: EquityRange): String = when (range) {
    EquityRange.Under20 -> stringResource(R.string.quiz_range_under, range.to)
    EquityRange.Over80 -> stringResource(R.string.quiz_range_over, range.from)
    else -> stringResource(R.string.quiz_range_between, range.from, range.to)
}
