package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.Money
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import kotlin.math.abs

/**
 * Every saved night as CSV for a spreadsheet (PP-037, RFC 4180): a header, then one row per player
 * per night, the oldest night first and its players in finishing order. Dates read 2026-10-05;
 * amounts are plain numbers with two decimals and a '.' ("12.50", "-0.50"), in every currency, so a
 * spreadsheet reads them as numbers whatever the phone's language; buy_in is what the player paid to
 * sit down (buy-in, food and bounty). The last column, currency, holds the money symbol the app shows
 * ("$", "€", "kr"; empty for plain numbers, PP-114). Text holding a comma, a quote or a line break is
 * quoted, its quotes doubled. Lines end in CRLF.
 */
object NightCsv {
    val HEADER = listOf(
        "date", "structure", "prize_pool", "players", "place", "player", "points",
        "buy_in", "rebuys", "rebuy_total", "add_ons", "add_on_total", "paid_in",
        "prize", "knockouts", "bounties", "won", "net", "currency",
    )

    private const val LINE_END = "\r\n"

    fun of(nights: List<SavedNight>, currency: AppCurrency = MoneyFormat.current): String = buildString {
        append(HEADER.joinToString(",")).append(LINE_END)
        nights.sortedWith(compareBy<SavedNight> { it.date }.thenBy { it.id }).forEach { night ->
            night.players.forEach { player -> append(row(night, player, currency).joinToString(",")).append(LINE_END) }
        }
    }

    private fun row(night: SavedNight, player: NightPlayer, currency: AppCurrency): List<String> = listOf(
        night.date.toString(),
        field(night.structureName.orEmpty()),
        amount(night.prizePoolCents),
        night.players.size.toString(),
        player.place.toString(),
        field(player.name),
        Season.points(player.place, night.players.size).toString(),
        amount(player.entryCents),
        player.rebuys.toString(),
        amount(player.rebuyCents),
        player.addOns.toString(),
        amount(player.addOnCents),
        amount(player.paidInCents),
        amount(player.prizeCents),
        player.knockouts.toString(),
        amount(player.bountyCents),
        amount(player.wonCents),
        amount(player.netCents),
        field(currency.symbol),
    )

    /**
     * [text] as one CSV field: quoted when it holds a comma, a quote or a line break, its quotes doubled.
     * Text a spreadsheet would run as a formula (starting with =, +, -, @, a tab or a carriage return)
     * gets a leading apostrophe first, so a player named "=1+1" stays a name.
     */
    fun field(text: String): String {
        val safe = if (text.firstOrNull() in FORMULA_STARTS) "'$text" else text
        return if (safe.any { it in NEEDS_QUOTES }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
    private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')

    /** Whole cents as a plain number with two decimals: 1250 is "12.50", -50 is "-0.50". */
    fun amount(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val magnitude = abs(cents)
        val fraction = (magnitude % Money.CENTS_PER_DOLLAR).toString().padStart(2, '0')
        return "$sign${magnitude / Money.CENTS_PER_DOLLAR}.$fraction"
    }
}
