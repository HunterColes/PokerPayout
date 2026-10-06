@file:Suppress("MatchingDeclarationName") // BankRowInput is the one class; buildBankRows the point of the file

package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import com.huntercoles.pokerpayout.core.domain.model.Settlement

/** What the rows are built from: the players, their settlement and the purchase windows. */
internal class BankRowInput(
    val players: List<PlayerData>,
    val eliminationOrder: List<Int>,
    val settlement: Settlement,
    val money: MoneySettings,
    val rebuyWindow: PurchaseWindow,
    val addOnWindow: PurchaseWindow
)

/**
 * The Bank's rows as drawn (S5 v2): the champion first once there is one, then the players still in
 * (in seat order), then the players out, latest out first. Each cell says how it looks and whether
 * a tap or a hold does something.
 */
internal fun buildBankRows(input: BankRowInput): List<BankRowModel> {
    val championId = input.settlement.championId
    val names = input.players.associate { it.id to it.name }
    val ordered = buildPlayerDisplayModels(input.players, input.eliminationOrder).map { it.player }
    val champion = ordered.filter { it.id == championId }
    return (champion + ordered.filterNot { it.id == championId }).map { player ->
        val owed = input.settlement.forPlayer(player.id)
        val isChampion = player.id == championId
        val place = input.settlement.standings.placeOf(player.id)
        val winnings = owed?.winningsCents ?: 0L
        BankRowModel(
            playerId = player.id,
            name = player.name,
            section = when {
                isChampion -> BankSection.CHAMPION
                player.out -> BankSection.OUT
                else -> BankSection.PLAYING
            },
            buyIn = BankCell(if (player.buyIn) CellStatus.Done else CellStatus.Open),
            rebuy = purchaseCell(player.rebuys, input.money.rebuyCents > 0L, input.rebuyWindow),
            addOn = purchaseCell(player.addons, input.money.addOnCents > 0L, input.addOnWindow),
            out = outCell(player, isChampion, place),
            paid = paidCell(player, finished = player.out || isChampion, winnings),
            knockouts = owed?.knockouts ?: 0,
            rebuys = player.rebuys,
            addOns = player.addons,
            place = place,
            knockedOutByName = player.eliminatedBy?.takeIf { player.out }?.let { names[it] },
            outAtLevel = player.outLevel?.takeIf { player.out },
            paidInCents = (if (player.buyIn) input.money.entryCents else 0L) +
                player.rebuyPrices.sum() + player.addOnPrices.sum(),
            owedCents = if (player.paidOut) 0L else winnings
        )
    }
}

/**
 * Rebuy or add-on: done (with the count) when taken, open while the window is, a dot once it has
 * closed with none taken. Taken ones stay filled after the cutoff and can still be corrected by a
 * long press.
 */
private fun purchaseCell(count: Int, enabled: Boolean, window: PurchaseWindow): BankCell = when {
    !enabled && count == 0 -> BankCell(CellStatus.ClosedNotTaken, enabled = false)
    count > 0 -> BankCell(CellStatus.Done, count = count, enabled = enabled && window.isOpen, holdable = true)
    window.isOpen -> BankCell(CellStatus.Open, enabled = true, holdable = true)
    else -> BankCell(CellStatus.ClosedNotTaken, enabled = false)
}

/** Out: the place disc (tap to bring back), the champion's crown (can't be knocked out), or open. */
private fun outCell(player: PlayerData, isChampion: Boolean, place: Int?): BankCell = when {
    isChampion -> BankCell(CellStatus.Champion, place = 1, enabled = false)
    player.out -> BankCell(CellStatus.OutPlace, place = place ?: 0)
    else -> BankCell(CellStatus.Open)
}

/**
 * Paid: a tick once paid; the amount in a gold ring once the player is finished and owed something;
 * otherwise nothing to do yet. Every state opens the pay-out sheet.
 */
private fun paidCell(player: PlayerData, finished: Boolean, winningsCents: Long): BankCell = when {
    player.paidOut -> BankCell(CellStatus.Paid, amountCents = winningsCents)
    finished && winningsCents > 0L -> BankCell(CellStatus.Owed, amountCents = winningsCents)
    else -> BankCell(CellStatus.Muted, amountCents = winningsCents)
}
