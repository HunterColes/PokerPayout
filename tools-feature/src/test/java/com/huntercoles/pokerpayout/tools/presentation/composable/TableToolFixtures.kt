package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.tools.presentation.DealPlayer
import com.huntercoles.pokerpayout.tools.presentation.DealUiState
import com.huntercoles.pokerpayout.tools.presentation.OutsUiState
import com.huntercoles.pokerpayout.tools.presentation.PotPlayer
import com.huntercoles.pokerpayout.tools.presentation.PrizeSource
import com.huntercoles.pokerpayout.tools.presentation.SidePotsUiState
import com.huntercoles.pokerpayout.tools.table.Street

/** The table tools' states for the screen tests, with the mockups' players. */
internal object TableToolFixtures {
    /**
     * Dana all in for 300, Marcus bets 1,200, Priya calls 800 all in, Theo folded after 150: a main
     * pot of 1,050 for three, a side pot of 1,000 for two, and 400 back to Marcus.
     */
    val sidePots = SidePotsUiState(
        listOf(
            PotPlayer("Dana", 300),
            PotPlayer("Marcus", 1_200),
            PotPlayer("Priya", 800),
            PotPlayer("Theo", 150, folded = true),
        ),
    )

    /** The screen as it opens: three players, nothing in. */
    val sidePotsEmpty = SidePotsUiState()

    /** Everyone with chips in folded. */
    val sidePotsAllFolded = SidePotsUiState(
        listOf(PotPlayer("Dana", 100, folded = true), PotPlayer("Sam", 0), PotPlayer(chips = 50, folded = true)),
    )

    /** Ten players, the most, five of them all in for different amounts: five pots. */
    val sidePotsTen = SidePotsUiState(
        listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben", "Kim").mapIndexed { index, name ->
            PotPlayer(name, chips = 100L * (index / 2 + 1), folded = index == 9)
        },
    )

    /** Tonight's $900 paid 50 / 30 / 20 among nine: three left from the Bank, the chip leader well ahead. */
    private val tonight = listOf(45_000L, 27_000L, 18_000L, 0L, 0L, 0L, 0L, 0L, 0L)

    val deal = DealUiState(
        players = listOf(DealPlayer("Dana", 12_000), DealPlayer("Marcus", 7_500), DealPlayer("Priya", 4_500)),
        fromBank = true,
        source = PrizeSource.Payouts,
        payouts = tonight,
        forWinnerCents = 5_000,
    )

    /** The deal as it opens from the Bank, chips still to type. */
    val dealNoChips = deal.copy(players = deal.players.map { it.copy(chips = null) }, forWinnerCents = null)

    /** Four players typed, prizes typed, one of them going up. */
    val dealTyped = DealUiState(
        players = listOf(
            DealPlayer("Dana", 9_000),
            DealPlayer(chips = 6_000),
            DealPlayer("Priya", 3_000),
            DealPlayer("Theo", 2_000),
        ),
        source = PrizeSource.Typed,
        payouts = tonight,
        typed = listOf(40_000L, 25_000L, 15_000L, 20_000L),
    )

    /** A flush draw on the flop facing 100 into 200: 25% needed, 19.1% next card, 35.0% by the river. */
    val outsFlop = OutsUiState(street = Street.Flop, outs = 9, pot = 300, call = 100)

    /** A gutshot on the turn, nothing typed for the pot. */
    val outsTurn = OutsUiState(street = Street.Turn, outs = 4)

    /** Fifteen outs on the flop: the rule of 4 runs high. */
    val outsCombo = OutsUiState(street = Street.Flop, outs = 15, pot = 1_000, call = 500)
}
