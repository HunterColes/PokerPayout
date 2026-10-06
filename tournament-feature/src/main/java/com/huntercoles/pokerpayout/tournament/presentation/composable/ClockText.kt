package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import java.text.NumberFormat
import java.util.Locale

// The words and numbers the clock shows: blinds, times, chips.

internal const val SECONDS_PER_MINUTE = 60

@Composable
internal fun rememberChipFormatter(): NumberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

/** "300 / 600". */
internal fun blindsText(level: BlindLevel, formatter: NumberFormat) =
    "${formatter.format(level.smallBlind)} / ${formatter.format(level.bigBlind)}"

/** "12:41"; "1:02:41" from an hour. */
internal fun clockText(seconds: Int): String = ClockFormat.clock(seconds)

/** "in 12:41": how long until a level or break starts. */
@Composable
internal fun inTime(seconds: Int): String = stringResource(R.string.clock_in, clockText(seconds))

/** "18", or "0.6" under 10: the average stack in big blinds. */
internal fun bigBlinds(stack: Int, bigBlind: Int, formatter: NumberFormat): String {
    val blinds = stack.toDouble() / bigBlind
    return if (blinds < ONE_DECIMAL_BELOW) "%.1f".format(Locale.US, blinds) else formatter.format(stack / bigBlind)
}

/** "Color up the 25s", "Color up the 25s and 100s". */
@Composable
internal fun colorUpText(chips: List<Int>, formatter: NumberFormat): String =
    stringResource(R.string.break_color_up, chipList(chips, formatter))

/** "25s", "25s and 100s", "5s, 25s and 100s". */
@Composable
internal fun chipList(chips: List<Int>, formatter: NumberFormat): String {
    val names = chips.map { "${formatter.format(it)}s" }
    return when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(R.string.setup_and, names.dropLast(1).joinToString(", "), names.last())
    }
}

/** "3:20": hours and minutes of play, for the schedule's end and the ticket. */
internal fun hoursMinutes(seconds: Int): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    return "%d:%02d".format(Locale.US, minutes / SECONDS_PER_MINUTE, minutes % SECONDS_PER_MINUTE)
}

private const val ONE_DECIMAL_BELOW = 10.0
