package com.huntercoles.pokerpayout.core.domain.settle

/** One payment of a settle-up: [fromId] pays [toId] [amountCents]. */
data class Transfer(val fromId: Int, val toId: Int, val amountCents: Long)

/**
 * The payments that square a set of balances, in whole cents, and as few of them as possible.
 *
 * A balance is what a party is owed (positive) or owes (negative); they add up to 0. Every payment
 * goes from a party who owes to a party who is owed, nobody pays more than they owe and nobody is
 * paid more than they are owed, so afterwards every balance is 0 to the cent.
 *
 * **The fewest payments.** k parties with money to move need k − g payments, where g is the most
 * groups they can be split into that each add up to 0 on their own (a group of m needs m − 1, and
 * can't do with fewer unless it splits further). [exact] finds the best split by a search over every
 * subset, which is instant while at most [EXACT_MAX_PARTIES] parties have money to move: every night
 * of up to 10 players, with the Bank. Above that, [greedy] (largest debt to largest credit) needs at
 * most k − 1, the same bound, though not always the fewest (finding the fewest is NP-hard).
 *
 * **Order.** The list reads payer by payer, the largest debt first, and each payer's payments largest
 * first; ties keep the balances' order. The same balances always give the same list.
 */
object MinimumPayments {
    /** The exact search runs while at most this many balances are not 0 (2^11 subsets). */
    const val EXACT_MAX_PARTIES = 11

    /** The fewest payments that square [balancesCents] (exact up to [EXACT_MAX_PARTIES], greedy above). */
    fun of(balancesCents: Map<Int, Long>): List<Transfer> {
        val parties = parties(balancesCents)
        val payments = if (parties.size <= EXACT_MAX_PARTIES) exactPayments(parties) else greedyPayments(parties)
        return ordered(parties, payments)
    }

    /** The exact minimum, whatever the size (2^k subsets: for tests and small k). */
    fun exact(balancesCents: Map<Int, Long>): List<Transfer> {
        val parties = parties(balancesCents)
        return ordered(parties, exactPayments(parties))
    }

    /** One greedy pass over all the balances: at most k − 1 payments for k parties with money to move. */
    fun greedy(balancesCents: Map<Int, Long>): List<Transfer> {
        val parties = parties(balancesCents)
        return ordered(parties, greedyPayments(parties))
    }

    /** The parties with money to move, in the balances' order. */
    private fun parties(balancesCents: Map<Int, Long>): List<Party> {
        val total = balancesCents.values.sum()
        require(total == 0L) { "The balances must add up to 0, not $total" }
        return balancesCents.entries.mapIndexedNotNull { seat, (id, cents) -> Party(id, cents, seat).takeIf { cents != 0L } }
    }

    /**
     * The most zero-sum groups, then [greedyPayments] inside each. best[mask] is the most zero-sum
     * groups along any order of adding the parties in mask one by one (a group closes each time the
     * running sum is 0), which is the most groups mask splits into. Walking back from all of them
     * along a best order recovers the groups.
     */
    private fun exactPayments(parties: List<Party>): List<Transfer> {
        val count = parties.size
        if (count == 0) return emptyList()
        require(count < Int.SIZE_BITS - 1) { "Too many parties for the exact search: $count" }
        val all = (1 shl count) - 1
        val sums = LongArray(all + 1)
        val best = IntArray(all + 1)
        for (mask in 1..all) {
            val lowest = Integer.numberOfTrailingZeros(mask)
            sums[mask] = sums[mask and (mask - 1)] + parties[lowest].cents
            best[mask] = mostWithout(mask, best) + if (sums[mask] == 0L) 1 else 0
        }
        val groups = mutableListOf<List<Party>>()
        var mask = all
        var groupEnd = all
        while (mask != 0) {
            val previous = mask xor bitToDrop(mask, best, sums)
            if (sums[previous] == 0L) {
                groups += members(groupEnd xor previous, parties)
                groupEnd = previous
            }
            mask = previous
        }
        return groups.asReversed().flatMap(::greedyPayments)
    }

    /** The most groups in [mask] with one party left out. */
    private fun mostWithout(mask: Int, best: IntArray): Int {
        var most = 0
        var bits = mask
        while (bits != 0) {
            val bit = bits and -bits
            most = maxOf(most, best[mask xor bit])
            bits = bits xor bit
        }
        return most
    }

    /** The lowest party whose leaving keeps [mask] on a best order. */
    private fun bitToDrop(mask: Int, best: IntArray, sums: LongArray): Int {
        val wanted = best[mask] - if (sums[mask] == 0L) 1 else 0
        var bits = mask
        while (bits != 0) {
            val bit = bits and -bits
            if (best[mask xor bit] == wanted) return bit
            bits = bits xor bit
        }
        error("No best order through $mask")
    }

    private fun members(mask: Int, parties: List<Party>): List<Party> =
        parties.filterIndexed { index, _ -> mask and (1 shl index) != 0 }

    /**
     * Debts and credits each sorted largest first (ties in the balances' order); the current debtor
     * pays the current creditor the smaller of what the one still owes and the other is still owed,
     * and whichever is done gives way to the next. Each payment finishes at least one party and the
     * last finishes two, so m parties need at most m − 1.
     */
    private fun greedyPayments(parties: List<Party>): List<Transfer> {
        val debtors = parties.filter { it.cents < 0L }.map { Left(it, -it.cents) }.sortedWith(LargestFirst)
        val creditors = parties.filter { it.cents > 0L }.map { Left(it, it.cents) }.sortedWith(LargestFirst)
        val payments = mutableListOf<Transfer>()
        var d = 0
        var c = 0
        // The credits left always equal the debts left, so both lists run out together.
        while (d < debtors.size && c < creditors.size) {
            val debtor = debtors[d]
            val creditor = creditors[c]
            val amount = minOf(debtor.cents, creditor.cents)
            payments += Transfer(debtor.party.id, creditor.party.id, amount)
            debtor.cents -= amount
            creditor.cents -= amount
            if (debtor.cents == 0L) d++
            if (creditor.cents == 0L) c++
        }
        return payments
    }

    /** Payer by payer, the largest debt first (ties by seat); each payer's largest payment first (ties by seat). */
    private fun ordered(parties: List<Party>, payments: List<Transfer>): List<Transfer> {
        val byId = parties.associateBy { it.id }
        return payments.sortedWith(
            compareBy<Transfer> { byId.getValue(it.fromId).cents }
                .thenBy { byId.getValue(it.fromId).seat }
                .thenByDescending { it.amountCents }
                .thenBy { byId.getValue(it.toId).seat }
        )
    }

    private class Party(val id: Int, val cents: Long, val seat: Int)

    private class Left(val party: Party, var cents: Long)

    /** The largest first; ties in the balances' order. */
    private val LargestFirst = compareByDescending<Left> { it.cents }.thenBy { it.party.seat }
}
