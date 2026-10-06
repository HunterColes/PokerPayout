package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.poker.Cards

/** The table and the keypad together: what one keypad or seat action changes. */
data class TableEdit(val table: OddsTable, val keypad: KeypadState)

/**
 * The rank-then-suit keypad (S8) as pure functions. After each card the keypad moves to the next
 * empty slot ([OddsTable.nextOpen]): the seats in order, then the board; it closes when every slot
 * is filled. A card already on the table can't be picked twice.
 */
object OddsKeypad {

    /** Aims the keypad at [ref] (an empty board slot past the first empty one aims at that one). */
    fun select(edit: TableEdit, ref: SlotRef): TableEdit = edit.copy(keypad = KeypadState(target = edit.table.aim(ref)))

    /** Picks a rank; ignored with the keypad closed or when all four cards of that rank are out. */
    fun pickRank(edit: TableEdit, rank: Int): TableEdit {
        val target = edit.keypad.target ?: return edit
        val anyFree = (0 until Cards.SUITS).any { edit.table.canPlace(target, Cards.of(rank, it)) }
        return if (anyFree) edit.copy(keypad = edit.keypad.copy(rank = rank)) else edit
    }

    /** Completes the picked rank with [suit]: the card goes in, the keypad moves on. */
    fun pickSuit(edit: TableEdit, suit: Int): TableEdit {
        val rank = edit.keypad.rank ?: return edit
        return place(edit, Cards.of(rank, suit))
    }

    /** Puts [card] in the keypad's slot and moves on; ignored if the card is already elsewhere. */
    fun place(edit: TableEdit, card: Int): TableEdit {
        val target = edit.keypad.target
        if (target == null || !edit.table.canPlace(target, card)) return edit
        val table = edit.table.place(target, card)
        return TableEdit(table, KeypadState(target = table.nextOpen(after = target)))
    }

    /**
     * Clears the keypad's slot if it holds a card (or "random"); if it's empty, steps back to the
     * card before it and clears that, like a text backspace.
     */
    fun backspace(edit: TableEdit): TableEdit {
        val target = edit.keypad.target ?: return edit
        val table = edit.table
        val order = table.order()
        val clear = if (table.slot(target) != SlotValue.Empty) {
            target
        } else {
            order.take(order.indexOf(target).coerceAtLeast(0)).lastOrNull { table.slot(it) != SlotValue.Empty }
        }
        return if (clear == null) {
            edit.copy(keypad = edit.keypad.copy(rank = null))
        } else {
            TableEdit(table.with(clear, SlotValue.Empty), KeypadState(target = clear))
        }
    }

    /** Leaves the keypad seat's missing cards unknown ("a random hand") and moves to the next seat. */
    fun randomHand(edit: TableEdit): TableEdit {
        val target = edit.keypad.target as? SlotRef.Hole ?: return edit
        val table = edit.table.randomHand(target.seat)
        return TableEdit(table, KeypadState(target = table.nextOpen(after = SlotRef.Hole(target.seat, 1))))
    }

    /**
     * Keeps the keypad pointing at a real, live slot after a seat change: a removed or folded seat's
     * slot moves to the next empty one, and seats after a removed one shift down.
     */
    fun retarget(keypad: KeypadState, table: OddsTable, removedSeat: Int? = null): KeypadState {
        val target = keypad.target as? SlotRef.Hole ?: return keypad
        val seat = when {
            removedSeat == null || target.seat < removedSeat -> target.seat
            target.seat > removedSeat -> target.seat - 1
            else -> null // the keypad's seat was removed
        }
        val live = seat?.takeIf { it in table.seats.indices && !table.seats[it].folded }
        return live?.let { keypad.copy(target = SlotRef.Hole(it, target.index)) } ?: KeypadState(target = table.nextOpen())
    }
}
