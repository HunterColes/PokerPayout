package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding

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

    private fun BankTestKit.game(players: List<String> = NAMES, rebuy: Double = 40.0, addOn: Double = 10.0): BankViewModel {
        configure(players = players.size, buyIn = 40.0, food = 5.0, bounty = 5.0, rebuy = rebuy, addOn = addOn)
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
