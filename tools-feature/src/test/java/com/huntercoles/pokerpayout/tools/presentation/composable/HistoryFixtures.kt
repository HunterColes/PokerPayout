package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.tools.presentation.HistoryUiState
import java.time.LocalDate

/**
 * History states for the screen tests (S16, PP-037): three nights with the mockups' players over two
 * years, a $50 entry each ($40 buy-in, $5 food, $5 bounty). All time, Dana and Priya are level on 13
 * points; 2026's player of the year is Marcus.
 */
internal object HistoryFixtures {

    private fun player(name: String, place: Int, prize: Long = 0L) = NightPlayer(name, place, 5_000L, 0, 0L, 0, 0L, prize, 0, 0L)

    /** Six players, $300: Dana rebought and took an add-on, knocked out four and won; Marcus took an add-on. */
    val friday = SavedNight(
        id = 3L,
        date = LocalDate.of(2026, 10, 2),
        structureName = "Friday",
        prizePoolCents = 30_000L,
        players = listOf(
            NightPlayer("Dana", 1, 5_000L, 1, 4_000L, 1, 1_000L, 18_000L, 4, 2_500L),
            NightPlayer("Marcus", 2, 5_000L, 0, 0L, 1, 1_000L, 12_000L, 1, 500L),
            player("Priya", 3),
            player("Theo", 4),
            player("Jo", 5),
            player("Sam", 6),
        ),
    )

    val september = SavedNight(
        id = 2L,
        date = LocalDate.of(2026, 9, 12),
        structureName = null,
        prizePoolCents = 20_000L,
        players = listOf(
            player("Marcus", 1, 14_000L),
            player("Priya", 2, 6_000L),
            player("Dana", 3),
            player("Theo", 4),
            player("Alex", 5),
        ),
    )

    val holiday = SavedNight(
        id = 1L,
        date = LocalDate.of(2025, 12, 19),
        structureName = "Holiday deep stack",
        prizePoolCents = 20_000L,
        players = listOf(
            player("Priya", 1, 14_000L),
            player("Dana", 2, 6_000L),
            player("Sam", 3),
            player("Jo", 4),
            player("Marcus", 5),
        ),
    )

    /** The latest first, as the store keeps them. */
    val nights = listOf(friday, september, holiday)

    val list = HistoryUiState.of(nights)

    val year = HistoryUiState.of(nights, year = 2026)

    val night = HistoryUiState.of(nights, openId = friday.id)

    val empty = HistoryUiState.of(emptyList())
}
