package com.huntercoles.pokerpayout.tools.seats

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.tools.poker.Cards
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seat draw's saved state (PP-036): the last draw, the seats-a-table setting and, once changed
 * for the draw, the players. It survives process death and restarts. Nothing else reads it, and a
 * Tournament or Bank reset leaves it alone.
 */
@Singleton
class SeatDrawStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seatsPerTable(): Int = prefs.getInt(KEY_SEATS_PER_TABLE, SeatDrawer.DEFAULT_SEATS_PER_TABLE)
        .coerceIn(SeatDrawer.MIN_SEATS_PER_TABLE, SeatDrawer.MAX_SEATS_PER_TABLE)

    fun setSeatsPerTable(seats: Int) {
        prefs.edit().putInt(KEY_SEATS_PER_TABLE, seats).apply()
    }

    /** The players typed for the draw, or null while the draw follows the Bank's. */
    fun players(): List<String>? = prefs.getString(KEY_PLAYERS, null)?.let(SeatDrawCodec::decodeNames)

    fun setPlayers(players: List<String>?) {
        val edit = prefs.edit()
        if (players == null) edit.remove(KEY_PLAYERS) else edit.putString(KEY_PLAYERS, SeatDrawCodec.encodeNames(players))
        edit.apply()
    }

    /** The last draw, or null if there is none (or it can't be read). */
    fun draw(): SeatDraw? = prefs.getString(KEY_DRAW, null)?.let(SeatDrawCodec::decode)

    fun setDraw(draw: SeatDraw?) {
        val edit = prefs.edit()
        if (draw == null) edit.remove(KEY_DRAW) else edit.putString(KEY_DRAW, SeatDrawCodec.encode(draw))
        edit.apply()
    }

    private companion object {
        const val PREFS_NAME = "seat_draw_prefs"
        const val KEY_SEATS_PER_TABLE = "seats_per_table"
        const val KEY_PLAYERS = "players"
        const val KEY_DRAW = "draw"
    }
}

/**
 * How a draw is saved: a version line, then one line per table, its names (URL-encoded, so any
 * character is safe) and, after a `|`, its button cards ("As,Kd"; empty before the deal). The
 * result is saved rather than the seed, so a saved draw reads back the same in any later version.
 */
internal object SeatDrawCodec {
    private const val VERSION = "seats:1"
    private const val NAME_SEPARATOR = ","
    private const val CARDS_SEPARATOR = "|"
    private const val UTF_8 = "UTF-8"

    fun encode(draw: SeatDraw): String = buildString {
        append(VERSION)
        draw.tables.forEach { table ->
            append('\n')
            append(encodeNames(table.seats))
            append(CARDS_SEPARATOR)
            table.buttonCards?.let { cards -> append(cards.joinToString(NAME_SEPARATOR) { Cards.format(it) }) }
        }
    }

    /** The saved draw, or null for anything this version can't read. */
    fun decode(text: String): SeatDraw? = runCatching {
        val lines = text.split('\n')
        require(lines.first() == VERSION && lines.size > 1)
        SeatDraw(
            lines.drop(1).mapIndexed { index, line ->
                val names = decodeNames(line.substringBefore(CARDS_SEPARATOR))
                val cards = line.substringAfter(CARDS_SEPARATOR, missingDelimiterValue = "")
                    .takeIf { it.isNotEmpty() }
                    ?.split(NAME_SEPARATOR)
                    ?.map(Cards::parse)
                require(cards == null || cards.toSet().size == cards.size)
                DrawnTable(number = index + 1, seats = names, buttonCards = cards)
            },
        )
    }.getOrNull()

    fun encodeNames(names: List<String>): String = names.joinToString(NAME_SEPARATOR) { URLEncoder.encode(it, UTF_8) }

    fun decodeNames(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.split(NAME_SEPARATOR).map { URLDecoder.decode(it, UTF_8) }
}
