package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

private val VerdictShape = RoundedCornerShape(12.dp)

/**
 * PP-020: what the setup gives ("Works: 9 levels, 25 / 50 to 1,000 / 2,000, 2 breaks, ends at
 * 3:20. Color-ups after Levels 4 and 8."), or why it can't be played and the nearest setups that
 * can, one tap each.
 */
@Composable
internal fun SetupVerdict(uiState: TimerUiState, onFix: (BlindSetupFix) -> Unit, modifier: Modifier = Modifier) {
    val problem = uiState.setupProblem
    if (problem == null) {
        ValidSummary(uiState, modifier)
    } else {
        ProblemBox(problem, onFix, modifier)
    }
}

@Composable
private fun ValidSummary(uiState: TimerUiState, modifier: Modifier) {
    val levels = uiState.baseBlindLevels
    if (levels.isEmpty()) return
    val formatter = rememberChipFormatter()
    val breaks = uiState.timeline.segments.count { it is BreakSegment }
    val levelCount = pluralStringResource(R.plurals.setup_levels, levels.size, levels.size)
    val span = stringResource(
        R.string.setup_works_levels,
        levelCount,
        blindsText(levels.first(), formatter),
        blindsText(levels.last(), formatter),
    )
    val breakCount = if (breaks > 0) {
        pluralStringResource(R.plurals.setup_breaks, breaks, breaks)
    } else {
        stringResource(R.string.setup_no_breaks)
    }
    val ends = stringResource(R.string.setup_works_breaks, breakCount, hoursMinutes(uiState.timeline.regularEndSeconds))
    val colorUps = colorUpPoints(uiState)
    val colorUpText = if (colorUps.isEmpty()) {
        ""
    } else {
        " " + pluralStringResource(R.plurals.setup_color_ups, colorUps.size, colorUps.size, levelList(colorUps))
    }
    val works = stringResource(R.string.setup_works)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltDeep, VerdictShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.Live, modifier = Modifier.size(18.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = PokerColors.Live, fontWeight = FontWeight.Medium)) { append(works) }
                append(" $span$ends.$colorUpText")
            },
            color = PokerColors.CardWhite,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The levels after which a chip colors up: at a break, after the level it follows; else before the level. */
private fun colorUpPoints(uiState: TimerUiState): List<Int> = uiState.timeline.segments
    .filter { it.colorUp.isNotEmpty() }
    .mapNotNull { segment ->
        when (segment) {
            is BreakSegment -> segment.afterLevel
            is LevelSegment -> (segment.level.level - 1).takeIf { !segment.isOvertime && it > 0 }
        }
    }
    .distinct()

/** "4", "4 and 8", "2, 4 and 8". */
@Composable
private fun levelList(levels: List<Int>): String = when (levels.size) {
    1 -> levels.single().toString()
    else -> stringResource(R.string.setup_and, levels.dropLast(1).joinToString(", "), levels.last().toString())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProblemBox(problem: BlindSetupProblem, onFix: (BlindSetupFix) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.DangerWash, VerdictShape)
            .border(1.dp, PokerColors.Danger, VerdictShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PokerIcons.Info, contentDescription = null, tint = PokerColors.Danger, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.setup_cant_build),
                color = PokerColors.Danger,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(text = problem.explanation, color = PokerColors.CardWhite, style = MaterialTheme.typography.bodyMedium)
        if (problem.fixes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                problem.fixes.forEach { fix -> FixButton(fix, onFix) }
            }
        }
    }
}

/** "Use 20-min rounds (9 levels)", "Use 12,000 starting chips". */
@Composable
internal fun FixButton(fix: BlindSetupFix, onFix: (BlindSetupFix) -> Unit) {
    PokerButton(
        text = fix.label,
        onClick = { onFix(fix) },
        variant = PokerButtonVariant.Secondary,
        size = PokerButtonSize.Small,
    )
}
