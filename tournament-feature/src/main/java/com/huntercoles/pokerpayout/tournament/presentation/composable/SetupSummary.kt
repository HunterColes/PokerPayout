package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.design.components.presetLabel
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/**
 * The setup in one line each, for the strip above the clock and the fold that makes it (S1 v2):
 * "9 players · $40 buy-in · 20-min levels · 5,000 chips". From what the Tournament setup holds now.
 */
internal object SetupSummary {

    /** The strip's line, all of it (what TalkBack reads, and the fold's first line). */
    @Composable
    fun strip(setup: TournamentConfigUiState, timer: TimerUiState, full: Boolean = false): String =
        stripParts(setup, timer, full).joinToString(stripSeparator())

    /**
     * The strip's settings, most important first: players, buy-in, level length, chips. [full]
     * spells out every setting (tablets, where there's room). The strip shows as many as fit.
     */
    @Composable
    fun stripParts(setup: TournamentConfigUiState, timer: TimerUiState, full: Boolean = false): List<String> {
        val formatter = rememberChipFormatter()
        val config = timer.config
        return if (full) {
            listOf(players(setup)) + moneyParts(setup, timer) + listOf(
                stringResource(R.string.strip_hours, config.gameDurationHours),
                stringResource(R.string.strip_level_length, config.roundLengthMinutes),
                stringResource(R.string.strip_chips, formatter.format(config.startingChips)),
            ) + breakAndAnteParts(timer)
        } else {
            listOf(
                players(setup),
                stringResource(R.string.strip_buy_in, money(setup.money.buyInCents)),
                stringResource(R.string.strip_level_length, config.roundLengthMinutes),
                stringResource(R.string.strip_chips, formatter.format(config.startingChips)),
            )
        }
    }

    /** " · ", between the settings. */
    @Composable
    fun stripSeparator(): String = stringResource(R.string.strip_separator)

    /** "$40 buy-in · $5 bounty · rebuy to L4 · $10 add-on": the money section folded. */
    @Composable
    fun moneyLine(setup: TournamentConfigUiState, timer: TimerUiState): String =
        moneyParts(setup, timer).joinToString(stringResource(R.string.strip_separator))

    /** "3 h · 20-min levels · 5,000 · 25 chip · breaks every 4": the blinds section folded. */
    @Composable
    fun blindsLine(timer: TimerUiState): String {
        val formatter = rememberChipFormatter()
        val config = timer.config
        val parts = listOf(
            stringResource(R.string.strip_hours, config.gameDurationHours),
            stringResource(R.string.strip_level_length, config.roundLengthMinutes),
            formatter.format(config.startingChips),
            stringResource(R.string.strip_chip, formatter.format(config.smallestChip)),
        ) + breakAndAnteParts(timer)
        return parts.joinToString(stringResource(R.string.strip_separator))
    }

    /** "9 levels · 2 breaks", beside the blinds section's title and on the ticket. */
    @Composable
    fun levelsAndBreaks(timer: TimerUiState): String {
        val levels = timer.regularLevelCount
        val breaks = timer.timeline.segments.count { it is BreakSegment }
        return stringResource(
            R.string.setup_ticket_shape,
            pluralStringResource(R.plurals.setup_levels, levels, levels),
            if (breaks > 0) {
                pluralStringResource(R.plurals.setup_breaks, breaks, breaks)
            } else {
                stringResource(R.string.setup_no_breaks)
            },
        )
    }

    /** "Standard · 3 paid · rounded to $5": the payouts row folded. */
    @Composable
    fun payoutsLine(setup: TournamentConfigUiState): String = listOf(
        setup.payoutPreset?.let { presetLabel(it) } ?: stringResource(R.string.setup_payouts_custom),
        stringResource(R.string.strip_paid, setup.paidPlaces),
        stringResource(R.string.strip_rounded, setup.config.payoutRounding.label),
    ).joinToString(stringResource(R.string.strip_separator))

    @Composable
    private fun players(setup: TournamentConfigUiState): String =
        pluralStringResource(R.plurals.strip_players, setup.playerCount, setup.playerCount)

    @Composable
    private fun moneyParts(setup: TournamentConfigUiState, timer: TimerUiState): List<String> {
        val amounts = setup.money
        return listOfNotNull(
            stringResource(R.string.strip_buy_in, money(amounts.buyInCents)),
            amounts.bountyCents.takeIf { it > 0 }?.let { bountyPart(amounts.bountyMode, it) },
            amounts.rebuyCents.takeIf { it > 0 }?.let {
                if (timer.rebuyUntilLevel > 0) {
                    stringResource(R.string.strip_rebuy_to, timer.rebuyUntilLevel)
                } else {
                    stringResource(R.string.strip_rebuy, money(it))
                }
            },
            amounts.addOnCents.takeIf { it > 0 }?.let { stringResource(R.string.strip_add_on, money(it)) },
        )
    }

    @Composable
    private fun breakAndAnteParts(timer: TimerUiState): List<String> = listOfNotNull(
        timer.config.breaks.everyLevels.takeIf { timer.config.breaks.enabled }
            ?.let { stringResource(R.string.strip_breaks_every, it) },
        timer.config.bigBlindAnteFromLevel.takeIf { it > 0 }?.let { stringResource(R.string.strip_ante_from, it) },
    )
}

/** "$5 bounty", "$5 progressive bounty", "$5 mystery bounty" (PP-035). */
@Composable
private fun bountyPart(mode: BountyMode, cents: Long): String = stringResource(
    when (mode) {
        BountyMode.STANDARD -> R.string.strip_bounty
        BountyMode.PROGRESSIVE -> R.string.strip_bounty_pko
        BountyMode.MYSTERY -> R.string.strip_bounty_mystery
    },
    money(cents),
)

/** "$40", or "$12.50": cents only when there are some (copy rules, design spec §8), in the host's currency. */
internal fun money(cents: Long): String = FormatUtils.formatMoney(cents)
