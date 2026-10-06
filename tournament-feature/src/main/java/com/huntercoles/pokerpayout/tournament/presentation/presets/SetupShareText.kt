package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.BlindConfiguration
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.composable.SECONDS_PER_MINUTE
import com.huntercoles.pokerpayout.tournament.presentation.composable.blindsText
import com.huntercoles.pokerpayout.tournament.presentation.composable.colorUpText
import com.huntercoles.pokerpayout.tournament.presentation.composable.hoursMinutes
import com.huntercoles.pokerpayout.tournament.presentation.composable.money
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsShareText
import java.text.NumberFormat
import java.util.Locale

/**
 * The setup as plain text for the group chat (PP-032): the money per player, the blind schedule level
 * by level with its breaks and color-ups, and the payouts, as the Tournament tab has them now (the
 * clock's own schedule once it has started). Built from the screen's words: the strip's phrases, the
 * schedule's, the color-ups', and the Payouts tab's share lines.
 *
 * ```
 * Poker night setup
 * Money per player: $40 buy-in · $5 bounty · $40 rebuy to the end of Level 4 · $10 add-on · $5 food
 *
 * Blinds: 3 h · 20-min levels · 5,000 chips · smallest chip 25 · breaks every 4 · ante from L5
 * L1: 25 / 50
 * …
 * Break · 10 min · Color up the 25s · Last rebuy
 * …
 * 3:20 in all, then the blinds double each level
 *
 * Payouts for 9 players: Standard, rounded to $1
 * Prize pool $360 (9 buy-ins $360)
 * 1st: $180
 * …
 * ```
 */
object SetupShareText {

    fun build(context: Context, setup: TournamentConfigUiState, timer: TimerUiState): String {
        val res = context.resources
        val formatter = NumberFormat.getIntegerInstance(Locale.getDefault())
        val lines = buildList {
            add(res.getString(R.string.share_setup_title))
            add(res.getString(R.string.share_setup_money, moneyLine(res, setup.money, timer.rebuyUntilLevel)))
            add("")
            add(res.getString(R.string.share_setup_blinds, blindsLine(res, timer.config, formatter)))
            addAll(scheduleLines(res, timer.timeline, formatter))
            add("")
            addAll(payoutLines(context, setup))
        }
        return lines.joinToString("\n").trim()
    }

    /** "$40 buy-in · $5 bounty · $40 rebuy to the end of Level 4 · $10 add-on · $5 food". */
    private fun moneyLine(res: Resources, amounts: MoneySettings, rebuyUntilLevel: Int): String = listOfNotNull(
        res.getString(R.string.strip_buy_in, money(amounts.buyInCents)),
        amounts.bountyCents.takeIf { it > 0L }?.let { res.getString(R.string.strip_bounty, money(it)) },
        amounts.rebuyCents.takeIf { it > 0L }?.let { rebuy ->
            if (rebuyUntilLevel > 0) {
                res.getString(R.string.share_setup_rebuy_until, money(rebuy), rebuyUntilLevel)
            } else {
                res.getString(R.string.strip_rebuy, money(rebuy))
            }
        },
        amounts.addOnCents.takeIf { it > 0L }?.let { res.getString(R.string.strip_add_on, money(it)) },
        amounts.foodCents.takeIf { it > 0L }?.let { res.getString(R.string.share_setup_food, money(it)) },
    ).joinToString(res.getString(R.string.strip_separator))

    /** "3 h · 20-min levels · 5,000 chips · smallest chip 25 · breaks every 4 · ante from L5". */
    private fun blindsLine(res: Resources, config: BlindConfiguration, formatter: NumberFormat): String = listOfNotNull(
        res.getString(R.string.strip_hours, config.gameDurationHours),
        res.getString(R.string.strip_level_length, config.roundLengthMinutes),
        res.getString(R.string.strip_chips, formatter.format(config.startingChips)),
        res.getString(R.string.share_setup_smallest, formatter.format(config.smallestChip)),
        config.breaks.everyLevels.takeIf { config.breaks.enabled }?.let { res.getString(R.string.strip_breaks_every, it) },
        config.bigBlindAnteFromLevel.takeIf { it > 0 }?.let { res.getString(R.string.strip_ante_from, it) },
    ).joinToString(res.getString(R.string.strip_separator))

    /** One line per regular level and break, then the planned length; none when the blinds can't be built. */
    private fun scheduleLines(res: Resources, timeline: ClockTimeline, formatter: NumberFormat): List<String> {
        val planned = timeline.segments.filter { it !is LevelSegment || !it.isOvertime }
        val end = timeline.regularEndSeconds.takeIf { it > 0 }?.let { res.getString(R.string.share_setup_ends, hoursMinutes(it)) }
        return planned.map { segmentLine(res, it, formatter) } + listOfNotNull(end)
    }

    /** "L5: 150 / 300 · ante 300", "Break · 10 min · Color up the 25s · Last rebuy". */
    private fun segmentLine(res: Resources, segment: ClockSegment, formatter: NumberFormat): String {
        val colorUp = segment.colorUp.takeIf { it.isNotEmpty() }?.let { colorUpText(res, it, formatter) }
        val parts = when (segment) {
            is LevelSegment -> listOfNotNull(
                res.getString(R.string.share_setup_level, segment.level.level, blindsText(segment.level, formatter)),
                segment.level.ante.takeIf { it > 0 }?.let { res.getString(R.string.clock_ante, formatter.format(it)) },
                colorUp,
            )
            is BreakSegment -> listOfNotNull(
                res.getString(R.string.schedule_break, segment.durationSeconds / SECONDS_PER_MINUTE),
                colorUp,
                segment.message.takeIf { it.isNotBlank() },
            )
        }
        return parts.joinToString(res.getString(R.string.strip_separator))
    }

    /** "Payouts for 9 players: Standard, rounded to $5", the pool, then each place paid. */
    private fun payoutLines(context: Context, setup: TournamentConfigUiState): List<String> {
        val players = setup.playerCount
        val structure = PayoutsShareText.structureLine(context, setup.payoutPreset, setup.config.payoutRounding)
        val heading = context.resources.getQuantityString(R.plurals.share_setup_payouts, players, players, structure)
        val pool = PayoutsShareText.poolLine(context, setup.pool, players, setup.rebuyPurchases, setup.addOnPurchases)
        val places = setup.payoutTable.places.map { row ->
            PayoutsShareText.placeLine(context, row.place, row.amountCents, setup.placeNames[row.place])
        }
        return listOf(heading, pool) + places
    }
}

/** Hands [text] to any app that takes plain text (the group chat), through the system's share sheet. */
internal fun shareSetup(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.presets_share)))
}
