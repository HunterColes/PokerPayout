package com.huntercoles.pokerpayout.tools.presentation

// Seat edits on an OddsTable: each returns a new table, or the same table if the edit isn't allowed.

/** Marks [seat]'s empty slots random (a random hand if both were empty). */
fun OddsTable.randomHand(seat: Int): OddsTable = copy(
    seats = seats.mapIndexed { i, s ->
        if (i == seat) s.copy(cards = s.cards.map { if (it == SlotValue.Empty) SlotValue.Random else it }) else s
    },
)

/** Clears both of [seat]'s cards (its fold state stays). */
fun OddsTable.clearHand(seat: Int): OddsTable =
    copy(seats = seats.mapIndexed { i, s -> if (i == seat) SeatState(folded = s.folded) else s })

/** One more seat with an unknown hand, up to [OddsTable.MAX_SEATS]. */
fun OddsTable.addSeat(): OddsTable = if (seats.size < OddsTable.MAX_SEATS) copy(seats = seats + SeatState()) else this

/** Removes [seat], down to [OddsTable.MIN_SEATS], as long as two players stay in the hand. */
fun OddsTable.removeSeat(seat: Int): OddsTable {
    val after = seats.filterIndexed { i, _ -> i != seat }
    val allowed = after.size >= OddsTable.MIN_SEATS && after.count { !it.folded } >= OddsTable.MIN_SEATS
    return if (allowed) copy(seats = after) else this
}

/** Folds [seat] (or brings it back), as long as two players stay in the hand. */
fun OddsTable.fold(seat: Int, folded: Boolean): OddsTable {
    val after = seats.mapIndexed { i, s -> if (i == seat) s.copy(folded = folded) else s }
    return if (after.count { !it.folded } >= OddsTable.MIN_SEATS) copy(seats = after) else this
}

/** Swaps the hands (and fold state) of seats [a] and [b]. */
fun OddsTable.swap(a: Int, b: Int): OddsTable {
    if (a == b || a !in seats.indices || b !in seats.indices) return this
    val swapped = seats.toMutableList()
    swapped[a] = seats[b]
    swapped[b] = seats[a]
    return copy(seats = swapped)
}

/** A new deal at the same table: every card cleared and everyone back in, same number of seats. */
fun OddsTable.newHand(): OddsTable = OddsTable(seats = List(seats.size) { SeatState() })
