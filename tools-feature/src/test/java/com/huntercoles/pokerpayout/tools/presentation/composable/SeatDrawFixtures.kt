package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.tools.presentation.SeatDrawUiState
import com.huntercoles.pokerpayout.tools.seats.SeatDraw
import com.huntercoles.pokerpayout.tools.seats.SeatDrawer
import kotlin.random.Random

/**
 * Seat draw states for the screen tests, with the mockups' players. Every draw comes from the real
 * drawer on a fixed seed.
 */
internal object SeatDrawFixtures {
    /** The mockups' nine players: one table at nine a table. */
    val nine = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")

    /** Fourteen: two tables of 7 at nine a table. */
    val fourteen = nine + listOf("Kim", "Lou", "Max", "Nia", "Omar")

    private fun state(players: List<String>, draw: SeatDraw?) =
        SeatDrawUiState(players = players, seatNames = players, fromBank = true, draw = draw)

    /**
     * The first seed whose draw of [players] looks drawn at a glance: none of the first three
     * listed sits in the first three seats.
     */
    private fun shuffled(players: List<String>): SeatDraw = (1L..10_000L).asSequence()
        .map { seed -> SeatDrawer.drawSeats(players, 9, Random(seed)) }
        .first { draw -> draw.players.take(3).none { it in players.take(3) } }

    /** Nothing drawn yet. */
    val empty = state(nine, draw = null)

    val oneTable = state(nine, shuffled(nine))

    val twoTables = state(fourteen, shuffled(fourteen))

    /**
     * Both tables dealt for the button, on the first seed where table 1's top rank is tied (so the
     * screen shows the suit deciding) and its button is near the top (so a phone shows it too).
     */
    val buttonDealt: SeatDrawUiState = run {
        val seated = checkNotNull(twoTables.draw)
        val seed = (0L..10_000L).first { seed ->
            val button = SeatDrawer.dealButtons(seated, Random(seed)).tables.first().button
            button != null && button.tiedOnRank && button.buttonSeat in 2..3
        }
        twoTables.copy(draw = SeatDrawer.dealButtons(seated, Random(seed)))
    }

    /** Names changed for the draw and the name fields open: two new players not named yet. */
    val editing = SeatDrawUiState(
        players = nine.take(7) + listOf("", ""),
        seatNames = nine.take(7) + listOf("Player 8", "Player 9"),
        fromBank = false,
        editingNames = true,
    )

    /** A draw, then two more players: the draw is out of date. */
    val stale = twoTables.copy(
        players = fourteen + listOf("Pat", "Quinn"),
        seatNames = fourteen + listOf("Pat", "Quinn"),
        fromBank = false,
    )
}
