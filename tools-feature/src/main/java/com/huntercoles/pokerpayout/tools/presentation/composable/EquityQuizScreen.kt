package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.Stat
import com.huntercoles.pokerpayout.core.design.components.StatStrip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizIntent
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizViewModel
import com.huntercoles.pokerpayout.tools.quiz.QuizDealer
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion

/** The equity quiz route ("Equity quiz" in the Tools list). */
@Composable
fun EquityQuizRoute(onBack: () -> Unit, viewModel: EquityQuizViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EquityQuizContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/** From this wide, and wider than tall, the table and the question sit side by side. */
private val TwoPaneWidth = 600.dp
private const val WIDE_RATIO = 1.25f
private const val TABLE_SHARE = 0.55f

/**
 * The equity quiz: the score (streak, best, right of all answered), the spot (the board and two or
 * three hands, face up), the question and, once answered, the real odds under every hand with
 * whether the guess was right; then how many hands and which question. Upright, one scrolling
 * column; on a wide screen held sideways, the table beside the question.
 */
@Composable
fun EquityQuizContent(
    state: EquityQuizUiState,
    onIntent: (EquityQuizIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.quiz_title),
            subtitle = stringResource(R.string.quiz_subtitle, handsLabel(state.hands), questionLabel(state.question)),
            onBack = onBack,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            val table: @Composable ColumnScope.() -> Unit = {
                ScoreStrip(state)
                QuizBoard(state)
                QuizHands(state, onIntent)
            }
            val questions: @Composable ColumnScope.() -> Unit = {
                QuizQuestionCard(state, onIntent)
                QuizSettings(state, onIntent)
                QuizNote()
            }
            if (maxWidth >= TwoPaneWidth && maxWidth >= maxHeight * WIDE_RATIO) {
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    QuizPane(Modifier.weight(TABLE_SHARE), table)
                    QuizPane(Modifier.weight(1f - TABLE_SHARE), questions)
                }
            } else {
                QuizPane(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                    table()
                    questions()
                }
            }
        }
    }
}

/** One scrolling column. */
@Composable
private fun QuizPane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Streak, best streak, and right of all answered. */
@Composable
private fun ScoreStrip(state: EquityQuizUiState) {
    val score = state.score
    StatStrip(
        stats = listOf(
            Stat(stringResource(R.string.quiz_stat_streak), score.streak.toString(), valueColor = PokerColors.PokerGold),
            Stat(stringResource(R.string.quiz_stat_best), score.best.toString()),
            Stat(
                label = stringResource(R.string.quiz_stat_right),
                value = score.right.toString(),
                sub = stringResource(R.string.quiz_stat_of, score.answered),
            ),
        ),
    )
}

/** How many hands, and which question. Either one deals a new spot. */
@Composable
private fun QuizSettings(state: EquityQuizUiState, onIntent: (EquityQuizIntent) -> Unit) {
    val hands = (QuizDealer.MIN_HANDS..QuizDealer.MAX_HANDS).toList()
    val handLabels = hands.associateWith { handsLabel(it) }
    val questionLabels = QuizQuestion.entries.associateWith { questionLabel(it) }
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.quiz_settings_hands), note = null)
        PokerSegmentedControl(
            options = hands,
            selected = state.hands,
            onSelect = { onIntent(EquityQuizIntent.SetHands(it)) },
            label = { handLabels.getValue(it) },
        )
        SectionHeader(title = stringResource(R.string.quiz_settings_question), note = null)
        PokerSegmentedControl(
            options = QuizQuestion.entries,
            selected = state.question,
            onSelect = { onIntent(EquityQuizIntent.SetQuestion(it)) },
            label = { questionLabels.getValue(it) },
        )
    }
}

@Composable
private fun handsLabel(hands: Int): String =
    stringResource(if (hands == QuizDealer.MIN_HANDS) R.string.quiz_two_hands else R.string.quiz_three_hands)

@Composable
private fun questionLabel(question: QuizQuestion): String = stringResource(
    when (question) {
        QuizQuestion.Leader -> R.string.quiz_question_option_leader
        QuizQuestion.Range -> R.string.quiz_question_option_range
    },
)

/** How the odds are worked out, at the foot of the screen. */
@Composable
private fun QuizNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = PokerIcons.Info,
            contentDescription = null,
            tint = PokerColors.Chalk,
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        Text(
            text = stringResource(R.string.quiz_how),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
    }
}
