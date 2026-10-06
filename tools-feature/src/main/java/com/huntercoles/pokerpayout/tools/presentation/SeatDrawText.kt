package com.huntercoles.pokerpayout.tools.presentation

import android.content.res.Resources
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.rankOf
import com.huntercoles.pokerpayout.tools.presentation.composable.cardText
import com.huntercoles.pokerpayout.tools.seats.ButtonResult
import com.huntercoles.pokerpayout.tools.seats.DrawnTable
import com.huntercoles.pokerpayout.tools.seats.SeatDraw

/** What a seat does for the first hand: deal, post a blind, or nothing yet. */
enum class SeatRole { Button, ButtonAndSmallBlind, SmallBlind, BigBlind }

/** [seat]'s job in the first hand at this table, once the button is dealt. */
fun ButtonResult.roleOf(seat: Int): SeatRole? = when (seat) {
    buttonSeat -> if (smallBlindSeat == buttonSeat) SeatRole.ButtonAndSmallBlind else SeatRole.Button
    smallBlindSeat -> SeatRole.SmallBlind
    bigBlindSeat -> SeatRole.BigBlind
    else -> null
}

/** The seat draw's wording, from string resources: shared by the screen and the share text. */
internal class SeatDrawText(private val res: Resources) {

    /** "10 players". */
    fun players(count: Int): String = res.getQuantityString(R.plurals.seat_draw_players_count, count, count)

    /** "2 tables". */
    fun tables(count: Int): String = res.getQuantityString(R.plurals.seat_draw_tables_count, count, count)

    /** "1 table of 9", "2 tables of 5", "3 tables: 7, 6 and 6". */
    fun plan(sizes: List<Int>): String = when {
        sizes.isEmpty() -> ""
        sizes.distinct().size == 1 -> res.getQuantityString(R.plurals.seat_draw_plan_even, sizes.size, sizes.size, sizes.first())
        else -> res.getString(R.string.seat_draw_plan_uneven, sizes.size, list(sizes.map(Int::toString)))
    }

    /** "Button: Bob, seat 5". */
    fun buttonLine(table: DrawnTable, result: ButtonResult): String =
        res.getString(R.string.seat_draw_button_line, table.seats[result.buttonSeat - 1], result.buttonSeat)

    /** "Tie on kings: K♠ wins on suit.", or null when the rank alone decided. */
    fun tieLine(result: ButtonResult): String? = if (result.tiedOnRank) {
        val rank = res.getStringArray(R.array.odds_rank_plurals)[rankOf(result.winningCard)]
        res.getString(R.string.seat_draw_tie, rank, cardText(result.winningCard))
    } else {
        null
    }

    /** "Button", "Small blind": the pills on a seat. */
    fun rolePills(role: SeatRole?): List<String> = when (role) {
        SeatRole.Button -> listOf(res.getString(R.string.seat_draw_role_button))
        SeatRole.ButtonAndSmallBlind -> listOf(
            res.getString(R.string.seat_draw_role_button),
            res.getString(R.string.seat_draw_role_small_blind),
        )
        SeatRole.SmallBlind -> listOf(res.getString(R.string.seat_draw_role_small_blind))
        SeatRole.BigBlind -> listOf(res.getString(R.string.seat_draw_role_big_blind))
        null -> emptyList()
    }

    /**
     * TalkBack's one stop per seat: "Seat 3, Alice, table 1", then the card it drew and its pills
     * once dealt ("Seat 5, Bob, table 1, King of spades, Button").
     */
    fun seatDescription(table: Int, seat: Int, name: String, cardName: String?, role: SeatRole?): String {
        val seatLine = res.getString(R.string.seat_draw_seat_description, seat, name, table)
        return (listOfNotNull(cardName) + rolePills(role)).fold(seatLine) { all, more ->
            res.getString(R.string.seat_draw_description_more, all, more)
        }
    }

    /**
     * The draw as plain text for ACTION_SEND: a heading, then each table's seats in order with the
     * button and the blinds marked, and who has the button with which card; then the rule.
     */
    fun share(draw: SeatDraw): String = buildString {
        append(res.getString(R.string.seat_draw_share_heading, players(draw.players.size), tables(draw.tables.size)))
        draw.tables.forEach { table ->
            append("\n\n").append(res.getString(R.string.seat_draw_table, table.number))
            val result = table.button
            table.seats.forEachIndexed { index, name ->
                val seat = index + 1
                val role = result?.roleOf(seat)?.let(::shareRole)
                append('\n')
                append(
                    if (role == null) {
                        res.getString(R.string.seat_draw_share_seat, seat, name)
                    } else {
                        res.getString(R.string.seat_draw_share_seat_role, seat, name, role)
                    },
                )
            }
            if (result != null) {
                append('\n').append(
                    res.getString(
                        R.string.seat_draw_share_button_line,
                        table.seats[result.buttonSeat - 1],
                        result.buttonSeat,
                        cardText(result.winningCard),
                    ),
                )
            }
        }
        if (draw.buttonDealt) append("\n\n").append(res.getString(R.string.seat_draw_rule))
    }

    private fun shareRole(role: SeatRole): String = res.getString(
        when (role) {
            SeatRole.Button -> R.string.seat_draw_share_button
            SeatRole.ButtonAndSmallBlind -> R.string.seat_draw_share_button_small_blind
            SeatRole.SmallBlind -> R.string.seat_draw_share_small_blind
            SeatRole.BigBlind -> R.string.seat_draw_share_big_blind
        },
    )

    /** "7, 6 and 6". */
    private fun list(items: List<String>): String = if (items.size == 1) {
        items.single()
    } else {
        res.getString(R.string.seat_draw_list_and, items.dropLast(1).joinToString(", "), items.last())
    }
}
