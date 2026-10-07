package com.huntercoles.pokerpayout.core.domain.history

import java.time.LocalDate

/** Saved nights for the History tests (PP-037). */
internal object Nights {

    /** A player who paid a $50 entry, bought nothing more, and won [prize] and [bounties]. */
    fun player(name: String, place: Int, prize: Long = 0L, knockouts: Int = 0, bounties: Long = 0L) =
        NightPlayer(
            name = name,
            place = place,
            entryCents = 5_000L,
            rebuys = 0,
            rebuyCents = 0L,
            addOns = 0,
            addOnCents = 0L,
            prizeCents = prize,
            knockouts = knockouts,
            bountyCents = bounties,
        )

    /** A night on [date] ("2026-10-05") where [names] finish in that order, 1st first; [prizes] pay the top places. */
    fun night(date: String, names: List<String>, id: Long = 0L, prizes: List<Long> = emptyList(), structure: String? = null) =
        SavedNight(
            id = id,
            date = LocalDate.parse(date),
            structureName = structure,
            prizePoolCents = prizes.sum(),
            players = names.mapIndexed { index, name -> player(name, index + 1, prizes.getOrElse(index) { 0L }) },
        )
}
