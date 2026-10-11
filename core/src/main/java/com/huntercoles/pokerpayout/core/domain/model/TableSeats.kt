package com.huntercoles.pokerpayout.core.domain.model

/**
 * How many seats a table has: the seat draw's setting (Tools > Seat draw, 9 until the host changes
 * it), which tools-feature provides. The clock reads it for its final table (PP-111): everyone
 * left fits one table, in a night that started on two or more.
 */
fun interface TableSeats {
    fun seatsPerTable(): Int
}
