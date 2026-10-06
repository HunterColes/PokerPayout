package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.PlayerSettlement
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow

const val MAX_PURCHASE_COUNT = 20

/** What can be bought after the entry: rebuys and add-ons. */
enum class Purchase { REBUY, ADD_ON }

/** One player as the Bank records them. */
data class PlayerData(
    val id: Int,
    val name: String,
    val buyIn: Boolean = false,
    val out: Boolean = false,
    val paidOut: Boolean = false,
    /** What each rebuy cost when it was bought (PP-085), oldest first. */
    val rebuyPrices: List<Long> = emptyList(),
    val addOnPrices: List<Long> = emptyList(),
    /** Player id who eliminated this player; null if nobody was credited. */
    val eliminatedBy: Int? = null,
    /** The clock's level when this player went out, if the clock was running. */
    val outLevel: Int? = null
) {
    val rebuys: Int get() = rebuyPrices.size
    val addons: Int get() = addOnPrices.size

    fun prices(kind: Purchase): List<Long> = if (kind == Purchase.REBUY) rebuyPrices else addOnPrices
}

/**
 * Bank screen state. Every amount is in cents and comes from one settlement of what was recorded
 * (`SettleTournamentUseCase`); [rows] is the list as drawn, built from it.
 */
data class BankUiState(
    val players: List<PlayerData> = emptyList(),
    val eliminationOrder: List<Int> = emptyList(),
    val money: MoneySettings = MoneySettings.DEFAULT,
    val pool: PoolBreakdown = PoolBreakdown.EMPTY,
    /** Entry fees of players marked as bought in, plus every recorded rebuy and add-on. */
    val totalPaidInCents: Long = 0L,
    /** Winnings of the players marked as paid out. */
    val totalPaidOutCents: Long = 0L,
    val payoutTable: PayoutTable = PayoutTable.EMPTY,
    val payoutSettings: PayoutSettings = PayoutSettings(
        weights = emptyList(),
        preset = PayoutPreset.DEFAULT,
        rounding = PayoutRounding.DEFAULT
    ),
    /** Player id to finishing place, for places already decided. */
    val placeByPlayer: Map<Int, Int> = emptyMap(),
    val knockoutCounts: Map<Int, Int> = emptyMap(),
    /** Players who have won something: a paid place, a bounty or both. */
    val payoutEligiblePlayerIds: Set<Int> = emptySet(),
    val championId: Int? = null,
    val rows: List<BankRowModel> = emptyList(),
    val clock: ClockStatus = ClockStatus.NOT_STARTED,
    /** The rebuy cutoff, from "rebuys until level N" and the clock (PP-030). */
    val rebuyWindow: PurchaseWindow = PurchaseWindow.NoCutoff,
    /** Add-ons: open until the end of the first break after the rebuy cutoff. */
    val addOnWindow: PurchaseWindow = PurchaseWindow.NoCutoff,
    val isTimerRunning: Boolean = false,
    val isMuted: Boolean = false,
    /** The sheet on screen, if any (one at a time). */
    val sheet: BankSheet? = null,
    /** What Undo would take back, newest first; empty when there is nothing to undo. */
    val undoLabel: String? = null,
    /** False while the Bank is as it starts (default names, nothing recorded): Reset has nothing to do. */
    val canReset: Boolean = false
) {
    val totalPoolCents: Long get() = pool.totalCents
    val prizePoolCents: Long get() = pool.prizePoolCents

    /** What the bank pays back out in total: the prize pool plus the bounty pool. */
    val payableCents: Long get() = pool.payableCents
    val payoutWeights: List<Int> get() = payoutSettings.weights
    val isRebuyEnabled: Boolean get() = money.rebuyCents > 0L
    val isAddOnEnabled: Boolean get() = money.addOnCents > 0L
    val activePlayers: Int get() = players.count { !it.out }
    val paidOutCount: Int get() = players.count { it.paidOut }
    val totalRebuyCount: Int get() = players.sumOf { it.rebuys }
    val totalAddonCount: Int get() = players.sumOf { it.addons }
    val canUndo: Boolean get() = undoLabel != null

    /** What the top bar says about the night so far. */
    val summary: BankSummary
        get() = BankSummary(
            playerCount = players.size,
            playersLeft = activePlayers,
            collectedCents = totalPaidInCents,
            expectedCents = totalPoolCents,
            championName = players.firstOrNull { it.id == championId }?.name,
            stillToPayCents = (payableCents - totalPaidOutCents).coerceAtLeast(0L)
        )
}

/** The Bank's top-bar subtitle, as numbers: "7 of 9 left · $540 collected". */
data class BankSummary(
    val playerCount: Int,
    val playersLeft: Int,
    val collectedCents: Long,
    val expectedCents: Long,
    val championName: String?,
    val stillToPayCents: Long
)

/** Which part of the list a row sits in. */
enum class BankSection { CHAMPION, PLAYING, OUT }

/** The five columns, left to right. */
enum class BankColumn { BUY_IN, REBUY, ADD_ON, OUT, PAID }

/**
 * How one cell looks and what it says (design spec, section 3): state by shape and fill, never by
 * colour alone.
 */
enum class CellStatus {
    /** Dashed ring: not yet, tap to do it. */
    Open,

    /** Solid gold: done ([BankCell.count] of them). */
    Done,

    /** A small dot: the column is closed and this player took none. */
    ClosedNotTaken,

    /** A faint dashed ring: nothing to do yet (Paid before anything is owed). */
    Muted,

    /** A red disc with the finishing place ("8th"). */
    OutPlace,

    /** A green disc with a tick: paid out. */
    Paid,

    /** A gold ring with the amount still owed. */
    Owed,

    /** The champion's crown in the Out column; can't be knocked out. */
    Champion
}

/** One cell of a row. [enabled]: a tap does something; [holdable]: a long press opens the count sheet. */
data class BankCell(
    val status: CellStatus,
    val count: Int = 0,
    val place: Int = 0,
    val amountCents: Long = 0L,
    val enabled: Boolean = true,
    val holdable: Boolean = false
)

/** One line of the Bank: the name, its micro line, and the five cells. */
data class BankRowModel(
    val playerId: Int,
    val name: String,
    val section: BankSection,
    val buyIn: BankCell,
    val rebuy: BankCell,
    val addOn: BankCell,
    val out: BankCell,
    val paid: BankCell,
    val knockouts: Int,
    val rebuys: Int,
    val addOns: Int,
    /** Finishing place, once decided. */
    val place: Int?,
    /** Who knocked this player out (null if still in, or nobody was credited). */
    val knockedOutByName: String?,
    /** The level this player went out at, if the clock was running. */
    val outAtLevel: Int?,
    /** What this player has paid in so far (the tablet's In column). */
    val paidInCents: Long,
    /** Winnings not paid yet (the tablet's Owed column); 0 if none. */
    val owedCents: Long
) {
    fun cell(column: BankColumn): BankCell = when (column) {
        BankColumn.BUY_IN -> buyIn
        BankColumn.REBUY -> rebuy
        BankColumn.ADD_ON -> addOn
        BankColumn.OUT -> out
        BankColumn.PAID -> paid
    }
}

/** A player the knockout sheet offers as the one who did it. */
data class KnockoutCandidate(val playerId: Int, val name: String, val knockouts: Int, val isOut: Boolean)

/** The Bank's sheets. Only one is open at a time. */
sealed interface BankSheet {
    /** S5b: who knocked [name] out. They finish in [place]; their [bountyCents] goes to that player. */
    data class Knockout(
        val playerId: Int,
        val name: String,
        val place: Int,
        val bountyCents: Long,
        val candidates: List<KnockoutCandidate>,
        /** Picked already: whoever was credited last time this player went out. */
        val preselectedId: Int?
    ) : BankSheet

    /** S5c: what [name] is owed, line by line, and what they paid in. */
    data class PayOut(
        val playerId: Int,
        val name: String,
        val owed: PlayerSettlement,
        val isChampion: Boolean,
        val money: MoneySettings,
        val rebuys: Int,
        val addOns: Int,
        /** Knockouts nobody was credited with; their bounties go to the champion. */
        val unclaimedKnockouts: Int
    ) : BankSheet

    /** Hold Rebuy or Add-on: set an exact count. */
    data class Count(
        val playerId: Int,
        val name: String,
        val kind: Purchase,
        /** What each one already recorded cost, oldest first. */
        val prices: List<Long>,
        /** What one more costs today. */
        val priceCents: Long,
        val window: PurchaseWindow
    ) : BankSheet {
        val taken: Int get() = prices.size

        /** After the cutoff the count can only go down (to remove one recorded by mistake). */
        val maxCount: Int get() = if (window.isOpen) MAX_PURCHASE_COUNT else taken
    }

    /** The pool, where it came from, and the payout table. */
    data object PoolBreakdown : BankSheet

    /** Presets, rounding, places and weights; read-only while the clock runs. */
    data object PayoutStructure : BankSheet

    /** "Reset the bank?" for [playerCount] players. */
    data class ResetConfirm(val playerCount: Int) : BankSheet
}
