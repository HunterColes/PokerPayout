package com.huntercoles.pokerpayout.core.domain.cash

import java.math.BigInteger
import javax.inject.Inject

/** One payment of the settle-up: [fromId] pays [toId] [amountCents]. */
data class CashTransfer(val fromId: Int, val toId: Int, val amountCents: Long)

/** What the settle-up says about a ledger. */
sealed interface CashSettlement {
    /** Nobody has bought in. */
    data object Empty : CashSettlement

    /** [uncounted] players still have chips to count. */
    data class Counting(val uncounted: Int) : CashSettlement

    /**
     * The chip check is off by [differenceCents] (counted out minus cash in) and nobody has chosen
     * to split it. [canSplit] is false when there are no chips to split it over (every stack $0).
     */
    data class Unbalanced(val differenceCents: Long, val canSplit: Boolean) : CashSettlement

    /**
     * Who pays whom.
     * - [netsCents]: each player's result by id, in ledger order, after any split. They add up to 0.
     * - [transfers]: the payments, largest debts first.
     * - [splitCents]: the chip-check difference that was split (counted out minus cash in); 0 when
     *   the count balanced.
     * - [adjustmentsCents]: what the split did to each player's cash-out; empty when it balanced.
     */
    data class Settled(
        val netsCents: Map<Int, Long>,
        val transfers: List<CashTransfer>,
        val splitCents: Long = 0L,
        val adjustmentsCents: Map<Int, Long> = emptyMap(),
    ) : CashSettlement {
        fun adjustmentFor(playerId: Int): Long = adjustmentsCents[playerId] ?: 0L
    }
}

/**
 * The cash game's settle-up (PP-029): who pays whom, in whole cents.
 *
 * **Chip check first.** It settles only when everyone's chips are counted and the counts add up to
 * the cash that went in. When they don't, the players either recount or explicitly choose to split
 * the difference ([invoke]'s `splitDifference`). Splitting values every chip at cash in / chips
 * counted ([scaleCashOuts]): a short bank costs every stack the same share, and a player who
 * cashed out nothing pays nothing extra.
 *
 * **Greedy settle-up** ([transfers]): with debts and credits each sorted largest first, the largest
 * debtor pays the largest creditor as much as one of them owes or is owed, and that one is done; the
 * pass moves on. Each payment finishes at least one player and the last finishes two, so k players
 * who aren't even need at most k − 1 payments. Nobody is paid more than they are owed and nobody pays
 * more than they owe. It is not always the fewest payments possible (that problem is NP-hard), but
 * it never needs more than n − 1.
 */
class SettleCashUseCase @Inject constructor() {

    /** Settles [ledger]; an unbalanced chip check settles only if [splitDifference] is true. */
    operator fun invoke(ledger: CashLedger, splitDifference: Boolean = false): CashSettlement =
        when (val check = ledger.chipCheck) {
            ChipCheck.Empty -> CashSettlement.Empty
            is ChipCheck.Counting -> CashSettlement.Counting(check.uncounted)
            ChipCheck.Balanced -> {
                val nets = ledger.players.associate { it.id to (it.cashOutCents ?: 0L) - it.inCents }
                CashSettlement.Settled(nets, transfers(nets))
            }
            is ChipCheck.Off -> split(ledger, check.differenceCents, splitDifference)
        }

    private fun split(ledger: CashLedger, differenceCents: Long, chosen: Boolean): CashSettlement {
        val scaled = scaleCashOuts(ledger.players, ledger.cashInCents)
        if (!chosen || scaled == null) return CashSettlement.Unbalanced(differenceCents, canSplit = scaled != null)
        val nets = ledger.players.associate { it.id to scaled.getValue(it.id) - it.inCents }
        val adjustments = ledger.players.associate { it.id to scaled.getValue(it.id) - (it.cashOutCents ?: 0L) }
        return CashSettlement.Settled(nets, transfers(nets), differenceCents, adjustments)
    }

    /**
     * The payments that settle [netsCents] (player id to result, in ledger order; they must add up
     * to 0), in one greedy pass: debts largest first, credits largest first (ties in ledger order).
     * The current debtor pays the current creditor the smaller of what the one still owes and the
     * other is still owed, and whichever is done gives way to the next. The list reads payer by
     * payer, largest debt first, and each payer's payments largest first; the same ledger always
     * gives the same list.
     */
    fun transfers(netsCents: Map<Int, Long>): List<CashTransfer> {
        require(netsCents.values.sum() == 0L) { "The results must add up to 0, not ${netsCents.values.sum()}" }
        val seated = netsCents.entries.toList()
        val debtors = seated.mapIndexedNotNull { seat, (id, net) -> Balance(id, -net, seat).takeIf { net < 0L } }
            .sortedWith(LargestFirst)
        val creditors = seated.mapIndexedNotNull { seat, (id, net) -> Balance(id, net, seat).takeIf { net > 0L } }
            .sortedWith(LargestFirst)
        val payments = mutableListOf<Payment>()
        var d = 0
        var c = 0
        // The credits left always equal the debts left, so both lists run out together.
        while (d < debtors.size && c < creditors.size) {
            val debtor = debtors[d]
            val creditor = creditors[c]
            val amount = minOf(debtor.left, creditor.left)
            payments += Payment(d, CashTransfer(debtor.id, creditor.id, amount))
            debtor.left -= amount
            creditor.left -= amount
            if (debtor.left == 0L) d++
            if (creditor.left == 0L) c++
        }
        return payments.sortedWith(compareBy<Payment> { it.debtorRank }.thenByDescending { it.transfer.amountCents })
            .map { it.transfer }
    }

    /**
     * Each player's cash-out rescaled so they add up to [targetCents] exactly: every chip is worth
     * target / counted. Each share is rounded down to the cent, then the cents left over go one each
     * to the largest remainders (ties to ledger order), so a stack of $0 stays $0 and a bigger stack
     * never ends up with less than a smaller one. Null when nothing was counted out (no chips to
     * scale).
     */
    fun scaleCashOuts(players: List<CashPlayer>, targetCents: Long): Map<Int, Long>? {
        require(targetCents >= 0L) { "Can't share out a negative amount" }
        val countedCents = players.sumOf { it.cashOutCents ?: 0L }
        if (countedCents <= 0L) return null
        val counted = BigInteger.valueOf(countedCents)
        val target = BigInteger.valueOf(targetCents)
        val shares = players.mapIndexed { seat, player ->
            val (floor, remainder) = BigInteger.valueOf(player.cashOutCents ?: 0L).multiply(target).divideAndRemainder(counted)
            Share(player.id, seat, floor.toLong(), remainder)
        }
        // Fewer cents are left over than there are players, so this fits an Int.
        val leftover = (targetCents - shares.sumOf { it.floorCents }).toInt()
        val extraCent = shares.sortedWith(compareByDescending<Share> { it.remainder }.thenBy { it.seat })
            .take(leftover)
            .map { it.id }
            .toSet()
        return shares.associate { it.id to it.floorCents + if (it.id in extraCent) 1L else 0L }
    }

    private class Balance(val id: Int, val owed: Long, val seat: Int) {
        var left: Long = owed
    }

    private class Payment(val debtorRank: Int, val transfer: CashTransfer)

    private class Share(val id: Int, val seat: Int, val floorCents: Long, val remainder: BigInteger)

    private companion object {
        /** The largest first; ties in ledger order. */
        val LargestFirst = compareByDescending<Balance> { it.owed }.thenBy { it.seat }
    }
}
