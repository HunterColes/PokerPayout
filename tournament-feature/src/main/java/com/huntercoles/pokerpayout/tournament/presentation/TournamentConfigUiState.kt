package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences.TournamentConfigData

/** Which purchases a zero amount would clear. */
enum class PurchaseKind { REBUY, ADD_ON }

/** Asks before a zero rebuy or add-on amount clears [count] recorded purchases. */
data class PurchaseClearPrompt(val kind: PurchaseKind, val count: Int, val keptAmountCents: Long)

/**
 * The Tournament setup: the players and the money, the pool and payout table they make (for the
 * setup's summaries and the setup shared as text), and the setup's own questions. The blinds live in
 * the clock's state ([TimerUiState.config]) and the payout structure is set on the Payouts tab.
 */
data class TournamentConfigUiState(
    val config: TournamentConfigData = TournamentConfigData(
        numPlayers = TournamentDefaults.PLAYER_COUNT,
        money = MoneySettings.DEFAULT,
        payoutWeights = PayoutPreset.DEFAULT.weightsFor(PayoutPlaces.recommended(TournamentDefaults.PLAYER_COUNT)),
        payoutRounding = PayoutRounding.DEFAULT
    ),
    val pool: PoolBreakdown = PoolBreakdown.EMPTY,
    val payoutTable: PayoutTable = PayoutTable.EMPTY,
    /** The preset the weights come from; null once edited by hand. */
    val payoutPreset: PayoutPreset? = PayoutPreset.DEFAULT,
    /** Place to player name, for places already decided in the Bank. */
    val placeNames: Map<Int, String> = emptyMap(),
    val showResetDialog: Boolean = false,
    val purchaseClearPrompt: PurchaseClearPrompt? = null,
    val rebuyPurchases: Int = 0,
    val addOnPurchases: Int = 0,
    /** Someone is out in the Bank: the bounty type is fixed now (PP-035). */
    val knockoutsRecorded: Boolean = false,
    /** The Bank has drawn at least one mystery envelope for a knockout (PP-035). */
    val envelopesDrawn: Boolean = false,
    /** PP-113: the setup page's one-line welcome, on a new install until dismissed or the first clock starts. */
    val showWelcome: Boolean = false,
    /** The last level a player can join late or re-enter at (PP-116); 0 = no cutoff. */
    val lateEntryUntilLevel: Int = 0
) {
    val money: MoneySettings get() = config.money
    val playerCount: Int get() = config.numPlayers
    val paidPlaces: Int get() = payoutTable.places.size
    val bountyMode: BountyMode get() = money.bountyMode

    /** The bounty type is set before the first knockout: after it, knockouts have been paid under it. */
    val bountyTypeLocked: Boolean get() = knockoutsRecorded

    /** Mystery bounties: once envelopes are drawn, the bounty that made them can't change either. */
    val bountyAmountLocked: Boolean get() = knockoutsRecorded && bountyMode == BountyMode.MYSTERY

    /** Mystery bounties: the envelopes these players and this bounty make, biggest first. */
    val envelopes: List<Long> get() = MysteryBounty.envelopes(playerCount, money.bountyCents)

    /**
     * Mystery bounties: once an envelope is drawn, the player count can't go lower. Fewer players
     * would deal fewer envelopes, and the ones already drawn could leave the champion nothing.
     * Raising it (a late entry) is still allowed.
     */
    val playerCountCantGoLower: Boolean get() = envelopesDrawn && bountyMode == BountyMode.MYSTERY
}
