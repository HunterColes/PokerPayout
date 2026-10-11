package com.huntercoles.pokerpayout.tools.table

import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
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
 * (the same pool, structure and rounding, late entries and re-entries included, PP-116), so the
 * prizes match that tab to the cent. An entry is left until the Bank records it out; a player who
 * re-entered is left on their new entry only.
 */
class BankTonight @Inject constructor(
    private val tournament: TournamentPreferences,
    private val bank: BankPreferences,
    private val settle: SettleTournamentUseCase,
) : TonightSource {
    override fun tonight(): Tonight {
        val config = tournament.getCurrentTournamentConfig()
        val ids = (1..config.numPlayers).toList()
        val settlement = settle(
            players = bank.recordedPlayers(config.numPlayers),
            eliminationOrder = bank.getEliminationOrder(),
            money = config.money,
            weights = config.payoutWeights,
            rounding = config.payoutRounding,
        )
        val decided = settlement.standings.placeByPlayer.keys
        return Tonight(
            playersLeft = ids.filter { it !in decided }.map { id -> bank.getPlayerName(id).trim().ifEmpty { "Player $id" } },
            prizes = ids.map { place -> settlement.payoutTable.amountFor(place) },
        )
    }
}
