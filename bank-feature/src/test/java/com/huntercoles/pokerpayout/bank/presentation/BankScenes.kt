package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import java.time.LocalDate

/**
 * The mockups' game night (design spec section 5) as Bank states, built through the real ViewModel:
 * 9 players (Dana, Marcus, Priya, Theo, Jo, Sam, Alex, Rita, Ben), $40 buy-in, $5 food, $5 bounty,
 * $40 rebuy until the end of level 4, $10 add-on, breaks after levels 4 and 8.
 */
object BankScenes {
    val NAMES = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")
    const val DANA = 1
    const val MARCUS = 2
    const val PRIYA = 3
    const val THEO = 4
    const val JO = 5
    const val SAM = 6
    const val ALEX = 7
    const val RITA = 8
    const val BEN = 9
    private val BREAKS = listOf(4, 8)

    private fun BankTestKit.game(
        players: List<String> = NAMES,
        rebuy: Double = 40.0,
        addOn: Double = 10.0,
        bounties: BountyMode = BountyMode.STANDARD,
    ): BankViewModel {
        configure(players = players.size, buyIn = 40.0, food = 5.0, bounty = 5.0, rebuy = rebuy, addOn = addOn)
        tournamentPreferences.setBountyMode(bounties)
        tournamentPreferences.setRebuyUntilLevel(4)
        tournamentPreferences.setPayoutRounding(PayoutRounding.FIVE_DOLLARS)
        val viewModel = newViewModel()
        players.forEachIndexed { index, name -> viewModel.send(BankIntent.PlayerNameChanged(index + 1, name)) }
        return viewModel
    }

    private fun BankTestKit.buyIns(viewModel: BankViewModel, count: Int) =
        (1..count).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

    /** Before anyone has paid: every ring dashed. */
    fun beforeBuyIns(kit: BankTestKit): BankViewModel = kit.game()

    /**
     * S5: Level 6 of the mockup. Everyone bought in, Marcus rebought, five add-ons; Ben out at level
     * 3 (by Dana), Rita at level 5 (by Marcus). Rebuys closed after level 4, add-ons after break 1.
     * $540 collected, $0 of $495 paid out.
     */
    fun midGame(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game()
        buyIns(viewModel, 9)
        viewModel.send(BankIntent.AddPurchase(MARCUS, Purchase.REBUY))
        (DANA..JO).forEach { viewModel.send(BankIntent.AddPurchase(it, Purchase.ADD_ON)) }
        clock.at(level = 3, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, DANA)
        clock.at(level = 5, breaks = BREAKS)
        settle()
        viewModel.knockOut(RITA, MARCUS)
        clock.at(level = 6, breaks = BREAKS)
        settle()
        viewModel
    }

    /**
     * S5 with progressive bounties (PP-035), level 6: everyone bought in, Marcus rebought, five
     * add-ons. Ben out at level 3 and Alex at level 6, both by Dana, whose bounty grew to $10; Rita
     * out at level 5 by Marcus, whose bounty is $7.50. Everyone else still carries $5.
     */
    fun progressive(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game(bounties = BountyMode.PROGRESSIVE)
        buyIns(viewModel, 9)
        viewModel.send(BankIntent.AddPurchase(MARCUS, Purchase.REBUY))
        (DANA..JO).forEach { viewModel.send(BankIntent.AddPurchase(it, Purchase.ADD_ON)) }
        clock.at(level = 3, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, DANA)
        clock.at(level = 5, breaks = BREAKS)
        settle()
        viewModel.knockOut(RITA, MARCUS)
        clock.at(level = 6, breaks = BREAKS)
        settle()
        viewModel.knockOut(ALEX, DANA)
        viewModel
    }

    /**
     * The same night with mystery bounties (PP-035): nine envelopes (1 × $15, 2 × $6, 6 × $3). Ben out
     * by Dana and Rita by Marcus, each with the envelope the kit's seeded draw gave them.
     */
    fun mystery(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game(bounties = BountyMode.MYSTERY)
        buyIns(viewModel, 9)
        clock.at(level = 5, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, DANA)
        viewModel.send(BankIntent.DismissSheet)
        viewModel.knockOut(RITA, MARCUS)
        viewModel.send(BankIntent.DismissSheet)
        viewModel
    }

    /** Level 2: rebuys still open. Marcus has two, Priya one; Ben is out. */
    fun rebuysOpen(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game()
        buyIns(viewModel, 9)
        clock.at(level = 2, breaks = BREAKS)
        settle()
        viewModel.setRebuys(MARCUS, 2)
        viewModel.send(BankIntent.AddPurchase(PRIYA, Purchase.REBUY))
        viewModel.knockOut(BEN, DANA)
        viewModel
    }

    /** Rebuys and add-ons set to $0: their columns go. */
    fun noRebuys(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game(rebuy = 0.0, addOn = 0.0)
        buyIns(viewModel, 9)
        clock.at(level = 6, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, DANA)
        viewModel.knockOut(RITA, MARCUS)
        viewModel
    }

    /**
     * S5c: the night is over. Dana won (knocking out four, one knockout uncredited); Marcus 2nd with
     * no knockouts, paid; Priya 3rd, still owed.
     */
    fun champion(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game()
        buyIns(viewModel, 9)
        viewModel.send(BankIntent.AddPurchase(MARCUS, Purchase.REBUY))
        listOf(DANA, MARCUS, PRIYA, THEO, JO).forEach { viewModel.send(BankIntent.AddPurchase(it, Purchase.ADD_ON)) }
        clock.at(level = 9, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, PRIYA)
        viewModel.knockOut(RITA, DANA)
        viewModel.knockOut(ALEX, THEO)
        viewModel.knockOut(SAM, null)
        viewModel.knockOut(JO, DANA)
        viewModel.knockOut(THEO, DANA)
        viewModel.knockOut(PRIYA, DANA)
        viewModel.knockOut(MARCUS, null)
        viewModel.send(BankIntent.SetPaid(MARCUS, true))
        viewModel
    }

    /**
     * The champion night, settled between friends: Dana, Marcus, Priya, Theo and Jo paid in at the
     * start, Sam, Alex, Rita and Ben settle up at the end; nobody is paid out yet. The Bank holds
     * $340 and keeps the $45 food money, so it pays out $295; the four owe $50 each.
     */
    fun settleUp(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game()
        buyIns(viewModel, JO)
        viewModel.send(BankIntent.AddPurchase(MARCUS, Purchase.REBUY))
        listOf(DANA, MARCUS, PRIYA, THEO, JO).forEach { viewModel.send(BankIntent.AddPurchase(it, Purchase.ADD_ON)) }
        clock.at(level = 9, breaks = BREAKS)
        settle()
        viewModel.knockOut(BEN, PRIYA)
        viewModel.knockOut(RITA, DANA)
        viewModel.knockOut(ALEX, THEO)
        viewModel.knockOut(SAM, null)
        viewModel.knockOut(JO, DANA)
        viewModel.knockOut(THEO, DANA)
        viewModel.knockOut(PRIYA, DANA)
        viewModel.knockOut(MARCUS, null)
        viewModel
    }

    /**
     * S26 (PP-110): setting up the next night from the regulars. Four saved nights this autumn and one
     * in January (with Old Al and Mike R.), and Zoë typed in the Bank once; tonight nine seats, six
     * of them named so far (Dana, Marcus, Priya, Theo, Jo, Sam), with the sheet open.
     */
    fun regulars(kit: BankTestKit): BankViewModel = with(kit) {
        listOf(
            history("2026-01-17", "Old Al", "Dana", "Mike R."),
            history("2026-09-12", "Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex"),
            history("2026-09-19", "Priya", "Dana", "Marcus", "Rita", "Theo", "Sam"),
            history("2026-09-26", "Marcus", "Dana", "Jo", "Priya", "Ben", "Alex"),
            history("2026-10-02", "Dana", "Priya", "Theo", "Marcus", "Sam", "Rita", "Jo"),
        ).forEach { nights.add(it) }
        regularsStore.remember("Zoë", LocalDate.parse("2026-10-08"))
        val viewModel = game(players = NAMES.take(SAM) + List(NAMES.size - SAM) { "" })
        viewModel.send(BankIntent.ShowRegulars)
        viewModel
    }

    /** Names going in: Dana, Marcus and Priya typed, six seats nobody named yet, so the Bank offers the regulars. */
    fun naming(kit: BankTestKit): BankViewModel = kit.game(players = NAMES.take(PRIYA) + List(NAMES.size - PRIYA) { "" })

    /** The first time: nobody saved or typed yet, nine seats nobody named, the sheet open. */
    fun noRegulars(kit: BankTestKit): BankViewModel = with(kit) {
        val viewModel = game(players = List(NAMES.size) { "" })
        viewModel.send(BankIntent.ShowRegulars)
        viewModel
    }

    /** A saved night where [names] finish in that order, $40 in each. */
    private fun history(date: String, vararg names: String) = SavedNight(
        id = 0L,
        date = LocalDate.parse(date),
        structureName = null,
        prizePoolCents = names.size * 4_000L,
        players = names.mapIndexed { index, name -> NightPlayer(name, index + 1, 4_000L, 0, 0L, 0, 0L, 0L, 0, 0L) },
    )

    /** 30 players, half bought in, a few out: the list scrolls under the sticky header. */
    fun thirtyPlayers(kit: BankTestKit): BankViewModel = with(kit) {
        val names = NAMES + listOf(
            "Kim", "Lou", "Max", "Nia", "Oz", "Pat", "Quinn", "Raj", "Sky", "Tess", "Uma", "Vic", "Wes",
            "Xan", "Yara", "Zoe", "Abe", "Bea", "Cy", "Dot", "Eli",
        )
        val viewModel = game(players = names)
        buyIns(viewModel, 15)
        clock.at(level = 3, breaks = BREAKS)
        settle()
        viewModel.setRebuys(MARCUS, 2)
        listOf(30, 29, 28).forEach { viewModel.knockOut(it, DANA) }
        viewModel
    }
}
