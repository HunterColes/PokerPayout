package com.huntercoles.pokerpayout.tools.presentation

import android.content.res.Resources
import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.history.Standing
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tools.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** History's wording (PP-037), from string resources: shared by the screen and the share text. */
internal class HistoryText(private val res: Resources) {
    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(res.configuration.locales[0])
    private val separator get() = res.getString(R.string.history_separator)

    /** "Oct 5, 2026", in the phone's own date style. */
    fun date(date: LocalDate): String = dates.format(date)

    /** "9 players". */
    fun players(count: Int): String = res.getQuantityString(R.plurals.history_players, count, count)

    /** "9 players · Dana won". */
    fun nightLine(night: SavedNight): String =
        res.getString(R.string.history_night_line, players(night.players.size), night.winner.name)

    /** "18 points". */
    fun points(points: Int): String = res.getQuantityString(R.plurals.history_points, points, points)

    /** "3 nights · 2 wins", or "3 nights" without a win. */
    fun record(standing: Standing): String = listOfNotNull(
        res.getQuantityString(R.plurals.history_nights, standing.nights, standing.nights),
        res.getQuantityString(R.plurals.history_wins, standing.wins, standing.wins).takeIf { standing.wins > 0 },
    ).joinToString(separator)

    /**
     * "Player of 2026: Dana", or, level on points, "Players of 2026, tied: Dana, Sam"; for all time
     * (no [year]), "Most points all time: Dana". Null when nobody has played.
     */
    fun leaders(year: Int?, leaders: List<Standing>): String? {
        if (leaders.isEmpty()) return null
        val names = leaders.joinToString(res.getString(R.string.history_list_separator)) { it.name }
        return if (year == null) {
            res.getQuantityString(R.plurals.history_top_all_time, leaders.size, names)
        } else {
            res.getQuantityString(R.plurals.history_player_of_year, leaders.size, year, names)
        }
    }

    /** What a player paid and did: "Paid in $50 · 1 rebuy · 2 knockouts · bounties $10". */
    fun details(player: NightPlayer): String = listOfNotNull(
        res.getString(R.string.history_paid_in, formatMoney(player.paidInCents)),
        res.getQuantityString(R.plurals.history_rebuys, player.rebuys, player.rebuys).takeIf { player.rebuys > 0 },
        res.getQuantityString(R.plurals.history_add_ons, player.addOns, player.addOns).takeIf { player.addOns > 0 },
        knockouts(player.knockouts).takeIf { player.knockouts > 0 },
        res.getString(R.string.history_bounties, formatMoney(player.bountyCents)).takeIf { player.bountyCents > 0L },
    ).joinToString(separator)

    /**
     * The night as plain text for ACTION_SEND: the day (and the setup's name), the pool, then every
     * player in finishing order with what they won and their knockouts.
     */
    fun share(night: SavedNight): String = buildString {
        val day = date(night.date)
        append(
            night.structureName?.let { res.getString(R.string.history_share_heading_named, day, it) }
                ?: res.getString(R.string.history_share_heading, day),
        )
        val pool = formatMoney(night.prizePoolCents)
        append('\n').append(res.getString(R.string.history_share_pool, pool, players(night.players.size)))
        append('\n')
        night.players.forEach { player ->
            val line = if (player.wonCents > 0L) {
                val won = formatMoney(player.wonCents)
                res.getString(R.string.history_share_row_won, ordinalOf(player.place), player.name, won)
            } else {
                res.getString(R.string.history_share_row, ordinalOf(player.place), player.name)
            }
            append('\n').append(line)
            if (player.knockouts > 0) append(separator).append(knockouts(player.knockouts))
        }
    }

    private fun knockouts(count: Int): String = res.getQuantityString(R.plurals.history_knockouts, count, count)
}
