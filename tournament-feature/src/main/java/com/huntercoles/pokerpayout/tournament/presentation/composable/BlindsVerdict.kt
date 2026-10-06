package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

private val VerdictShape = RoundedCornerShape(12.dp)

/** What the setup gives, or why it can't be played and the nearest setups that can (PP-020). */
@Composable
internal fun SetupVerdict(uiState: TimerUiState, onFix: (BlindSetupFix) -> Unit, isLocked: Boolean) {
    val problem = uiState.setupProblem
    if (problem == null) {
        ValidSummary(uiState)
    } else {
        ProblemBox(problem, onFix, showFixes = !isLocked)
    }
}

@Composable
private fun ValidSummary(uiState: TimerUiState) {
    val levels = uiState.baseBlindLevels
    if (levels.isEmpty()) return
    val formatter = rememberChipFormatter()
    val breaks = uiState.timeline.segments.count { it is BreakSegment }
    val summary = buildString {
        append("✓ ${levels.size} levels, ")
        append("${blindsText(levels.first(), formatter)} to ${blindsText(levels.last(), formatter)}")
        if (breaks > 0) {
            append(" · $breaks break${if (breaks == 1) "" else "s"}")
            append(" · ends at ${hoursMinutes(uiState.timeline.regularEndSeconds)}")
        }
    }
    Text(
        text = summary,
        color = PokerColors.AccentGreen,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen.copy(alpha = 0.6f), VerdictShape)
            .padding(12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProblemBox(problem: BlindSetupProblem, onFix: (BlindSetupFix) -> Unit, showFixes: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen.copy(alpha = 0.8f), VerdictShape)
            .border(1.dp, PokerColors.ErrorRed.copy(alpha = 0.8f), VerdictShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "⚠ Can't build blinds", color = PokerColors.ErrorRed, fontWeight = FontWeight.Bold)
        Text(text = problem.explanation, color = PokerColors.CardWhite, style = MaterialTheme.typography.bodyMedium)
        if (problem.fixes.isNotEmpty() && showFixes) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                problem.fixes.forEach { fix -> FixButton(fix, onFix) }
            }
        }
    }
}

/** "Use 20-min rounds (9 levels)", "Use 12,000 starting chips". */
@Composable
internal fun FixButton(fix: BlindSetupFix, onFix: (BlindSetupFix) -> Unit) {
    OutlinedButton(
        onClick = { onFix(fix) },
        border = BorderStroke(1.dp, PokerColors.PokerGold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PokerColors.PokerGold)
    ) {
        Text(fix.label, fontWeight = FontWeight.SemiBold)
    }
}

/** "3:20" for a schedule end. */
private fun hoursMinutes(seconds: Int): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes / SECONDS_PER_MINUTE, minutes % SECONDS_PER_MINUTE)
}
