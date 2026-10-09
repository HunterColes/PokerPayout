package com.huntercoles.pokerpayout.tools.table

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.PlayerNamesProvider
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import javax.inject.Inject

/**
 * Tonight's tournament as the deal maker needs it.
 *
 * @property playersLeft the names of the players not knocked out yet, in the Bank's order; empty
 *   once there is a champion.
 * @property prizes the Payouts tab's table, 1st first, one per player (0 past the paid places).
 */
data class Tonight(val playersLeft: List<String>, val prizes: List<Long>) {
    val prizePoolCents: Long get() = prizes.sum()

    companion object {
        val NONE = Tonight(playersLeft = emptyList(), prizes = emptyList())
    }
}

/** Reads [Tonight]: from the Bank and the Payouts in the app, from a fake in tests. */
fun interface TonightSource {
    fun tonight(): Tonight
}

/**
 * Tonight from the Bank and the Tournament settings, settled the way the Payouts tab settles it
 * (the same pool, structure and rounding), so the prizes match that tab to the cent. A player is
 * left until the Bank records them out.
 */
class BankTonight @Inject constructor(
    private val tournament: TournamentPreferences,
    private val bank: BankPreferences,
    private val settle: SettleTournamentUseCase,
    private val names: PlayerNamesProvider,
) : TonightSource {
    override fun tonight(): Tonight {
        val config = tournament.getCurrentTournamentConfig()
        val ids = (1..config.numPlayers).toList()
        val players = ids.map { id ->
            BankPlayer(
                id = id,
                boughtIn = bank.getPlayerBuyInStatus(id),
                paidOut = bank.getPlayerPayedOutStatus(id),
                eliminatedBy = bank.getPlayerEliminatedBy(id),
                rebuyPricesCents = bank.getPlayerRebuyPrices(id),
                addOnPricesCents = bank.getPlayerAddonPrices(id),
                bountyDrawCents = bank.getPlayerBountyDraw(id),
            )
        }
        val settlement = settle(
            players = players,
            eliminationOrder = bank.getEliminationOrder(),
            money = config.money,
            weights = config.payoutWeights,
            rounding = config.payoutRounding,
        )
        val decided = settlement.standings.placeByPlayer.keys
        return Tonight(
            playersLeft = names.currentNames().filterIndexed { index, _ -> (index + 1) !in decided },
            prizes = ids.map { place -> settlement.payoutTable.amountFor(place) },
        )
    }
}
