package com.huntercoles.pokerpayout.tools.poker

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Hold'em equity engine. Pure Kotlin: no Android types, seedable, cancellable.
 *
 * [calculate] picks the method: **exact enumeration** of every deal when that needs at most
 * [OddsSettings.exactBudget] hand evaluations (every spot where all hands are known, including
 * heads-up preflop, and small spots with random hands), otherwise **Monte Carlo**, emitting
 * progressive snapshots with a standard error until [OddsSettings.maxSamples].
 *
 * Work runs on [dispatcher], split into independent chunks whose integer tallies are summed,
 * so results don't depend on thread count or scheduling. Cancelling the collector stops the
 * work within one chunk (about a millisecond).
 */
class OddsEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Odds for [request]. Exact: one complete snapshot. Monte Carlo: snapshots after roughly
     * 2k, 6k, 14k, 30k, 62k... deals, the last with `complete = true`.
     *
     * @throws OddsInputException (when collected) if the request is invalid.
     */
    fun calculate(request: OddsRequest, settings: OddsSettings = OddsSettings()): Flow<OddsResult> = flow {
        val plan = Plan.of(request)
        if (plan.exactEvaluations() <= settings.exactBudget) {
            emit(enumerate(plan))
        } else {
            monteCarlo(plan, settings) { emit(it) }
        }
    }.flowOn(dispatcher)

    /** Runs [calculate] to completion and returns the final result. */
    suspend fun finalResult(request: OddsRequest, settings: OddsSettings = OddsSettings()): OddsResult =
        calculate(request, settings).last()

    /**
     * For every card that can come next (the turn on a flop, the river on a turn): each seat's
     * exact equity once that card is out, and who leads then. Also says who is ahead right now
     * ([NextCardBreakdown.currentLeader]). A heads-up flop takes 45 x 44 showdowns.
     *
     * @param budget the most hand evaluations to spend in all; random hands multiply the work.
     * @throws OddsInputException if the board isn't a flop or a turn, the request is invalid, or
     *   the work would exceed [budget].
     */
    suspend fun nextCardBreakdown(
        request: OddsRequest,
        budget: Long = OddsSettings.DEFAULT_EXACT_BUDGET,
    ): NextCardBreakdown = withContext(dispatcher) {
        val plan = Plan.of(request)
        requireInput(request.board.size == FLOP_CARDS || request.board.size == FLOP_CARDS + 1) {
            "Next-card odds need a flop or a turn."
        }
        val perCard = Plan.of(request.copy(board = request.board + plan.deck[0])).exactEvaluations()
        requireInput(saturatingMultiply(perCard, plan.deck.size.toLong()) <= budget) {
            "Too many unknown cards to work out every next card."
        }
        val job = coroutineContext.job
        val cards = plan.deck.map { card ->
            async { nextCard(Plan.of(request.copy(board = request.board + card)), card, job) }
        }.awaitAll()
        NextCardBreakdown(cards, OddsInsights.madeLeader(request))
    }

    /** The cards that would put [seat] in the lead next (see [nextCardBreakdown]). */
    suspend fun outs(request: OddsRequest, seat: Int): List<Int> = nextCardBreakdown(request).outs(seat)

    private fun nextCard(plan: Plan, card: Int, job: Job): NextCard {
        val groups = plan.groups()
        val tally = Tally(plan.contestants.size, plan.seatCount, squares = false)
        if (groups.isEmpty()) {
            Enumerator(plan, groups, job, tally).leaf()
        } else {
            for (first in 0..plan.deck.size - groups[0].size) Enumerator(plan, groups, job, tally).runFrom(first)
        }
        val best = tally.shares.max()
        val leaders = tally.shares.indices.filter { tally.shares[it] == best }
        val equity = DoubleArray(plan.seatCount)
        plan.contestants.forEachIndexed { p, seat ->
            equity[seat] = PERCENT * tally.shares[p] / (tally.deals.toDouble() * OddsResult.SHARE_UNIT)
        }
        return NextCard(card, equity.toList(), leaders.singleOrNull()?.let { plan.contestants[it] })
    }

    // ---------------------------------------------------------------- exact enumeration

    private suspend fun enumerate(plan: Plan): OddsResult = coroutineScope {
        val job = coroutineContext.job
        val groups = plan.groups()
        val tally = Tally(plan.contestants.size, plan.seatCount, squares = false)
        if (groups.isEmpty()) {
            Enumerator(plan, groups, job, tally).leaf()
        } else {
            // One task per choice of the first dealt card. Heavy tasks come first.
            val firstGroupSize = groups[0].size
            val tasks = (0..plan.deck.size - firstGroupSize).map { first ->
                async {
                    Tally(plan.contestants.size, plan.seatCount, squares = false).also {
                        Enumerator(plan, groups, job, it).runFrom(first)
                    }
                }
            }
            tasks.awaitAll().forEach(tally::add)
        }
        tally.toResult(plan, exact = true, complete = true, seed = null)
    }

    /** A group of cards dealt together: a seat's missing hole cards, or the rest of the board. */
    private class Group(val target: Int, val size: Int) // target: contestant index, or BOARD

    private class Enumerator(
        private val plan: Plan,
        private val groups: List<Group>,
        private val job: Job,
        private val tally: Tally,
    ) {
        private val hands = plan.knownMasks.copyOf()
        private var board = plan.boardMask
        private var leaves = 0

        fun runFrom(firstIndex: Int) {
            val g = groups[0]
            val bit = Cards.bit(plan.deck[firstIndex])
            assign(g.target, bit)
            deal(0, firstIndex + 1, g.size - 1, bit)
            assign(g.target, bit)
        }

        private fun assign(target: Int, bit: Long) {
            if (target == BOARD) board = board xor bit else hands[target] = hands[target] xor bit
        }

        /** Deal [left] more cards of group [g], from deck index [start] on, avoiding [used]. */
        private fun deal(g: Int, start: Int, left: Int, used: Long) {
            if (left == 0) {
                if (g + 1 == groups.size) leaf() else deal(g + 1, 0, groups[g + 1].size, used)
                return
            }
            val deck = plan.deck
            val target = groups[g].target
            for (i in start..deck.size - left) {
                val bit = Cards.bit(deck[i])
                if (used and bit != 0L) continue
                assign(target, bit)
                deal(g, i + 1, left - 1, used or bit)
                assign(target, bit)
            }
        }

        fun leaf() {
            if (++leaves and CANCEL_CHECK_MASK == 0) job.ensureActive()
            tally.showdown(hands, board)
        }
    }

    // ---------------------------------------------------------------- Monte Carlo

    private suspend fun monteCarlo(plan: Plan, settings: OddsSettings, emit: suspend (OddsResult) -> Unit) {
        val seed = settings.seed ?: Random.nextLong()
        val total = settings.maxSamples.toLong()
        val tally = Tally(plan.contestants.size, plan.seatCount, squares = true)
        var nextBatch = 0
        var roundBatches = FIRST_ROUND_BATCHES
        var done = 0L
        while (true) {
            val sizes = ArrayList<Int>(roundBatches)
            var planned = done
            while (sizes.size < roundBatches && planned < total) {
                val size = min(BATCH_SIZE.toLong(), total - planned).toInt()
                sizes += size
                planned += size
            }
            val firstIndex = nextBatch
            val parts = coroutineScope {
                val job = coroutineContext.job
                sizes.mapIndexed { i, size ->
                    async { sample(plan, seed, firstIndex + i, size, job) }
                }.awaitAll()
            }
            parts.forEach(tally::add)
            nextBatch += sizes.size
            done = planned
            val reachedTarget = settings.targetStdErr > 0.0 &&
                tally.maxStdErrPct() <= settings.targetStdErr
            val complete = done >= total || reachedTarget
            emit(tally.toResult(plan, exact = false, complete = complete, seed = seed))
            if (complete) return
            roundBatches = min(roundBatches * 2, MAX_ROUND_BATCHES)
            yield()
        }
    }

    /** Deals [samples] random runouts. Deterministic for a given (seed, batch). */
    private fun sample(plan: Plan, seed: Long, batch: Int, samples: Int, job: Job): Tally {
        job.ensureActive()
        val tally = Tally(plan.contestants.size, plan.seatCount, squares = true)
        val rng = Random(mix(seed, batch))
        val deck = plan.deck.copyOf()
        val n = deck.size
        val need = plan.cardsToDeal
        val missing = plan.missing
        val known = plan.knownMasks
        val hands = LongArray(known.size)
        repeat(samples) {
            // Partial Fisher-Yates: deck[0 until need] becomes a uniform random draw.
            for (j in 0 until need) {
                val r = j + rng.nextInt(n - j)
                val t = deck[j]; deck[j] = deck[r]; deck[r] = t
            }
            var pos = 0
            for (p in hands.indices) {
                var m = known[p]
                for (k in 0 until missing[p]) m = m or Cards.bit(deck[pos++])
                hands[p] = m
            }
            var board = plan.boardMask
            while (pos < need) board = board or Cards.bit(deck[pos++])
            tally.showdown(hands, board)
        }
        return tally
    }

    // ---------------------------------------------------------------- shared pieces

    /** Validated, card-mask form of a request. Contestants are the non-folded seats. */
    private class Plan(
        val seatCount: Int,
        val contestants: IntArray,
        val knownMasks: LongArray,
        val missing: IntArray,
        val boardMask: Long,
        val deck: IntArray,
    ) {
        val boardMissing: Int = BOARD_CARDS - Cards.count(boardMask)
        val cardsToDeal: Int = missing.sum() + boardMissing

        fun groups(): List<Group> = buildList {
            missing.forEachIndexed { p, m -> if (m > 0) add(Group(p, m)) }
            if (boardMissing > 0) add(Group(BOARD, boardMissing))
        }

        /** Deals x contestants for a full enumeration, saturating at Long.MAX_VALUE. */
        fun exactEvaluations(): Long {
            var left = deck.size
            var deals = 1L
            for (g in groups()) {
                deals = saturatingMultiply(deals, choose(left, g.size))
                left -= g.size
            }
            return saturatingMultiply(deals, contestants.size.toLong())
        }

        companion object {
            fun of(request: OddsRequest): Plan {
                val seats = request.seats
                requireInput(seats.size >= 2) { "Add at least two players." }
                requireInput(seats.size <= MAX_SEATS) { "At most $MAX_SEATS players." }
                requireInput(request.board.size <= BOARD_CARDS) { "The board has at most five cards." }
                seats.forEachIndexed { i, s ->
                    requireInput(s.cards.size <= 2) { "Player ${i + 1} has more than two hole cards." }
                }
                var used = 0L
                fun take(card: Int) {
                    requireInput(card in 0 until Cards.COUNT) { "Unknown card index $card." }
                    val bit = Cards.bit(card)
                    requireInput(used and bit == 0L) { "${Cards.format(card)} is used twice." }
                    used = used or bit
                }
                seats.forEach { it.cards.forEach(::take) }
                request.board.forEach(::take)
                request.dead.forEach(::take)

                val contestants = seats.indices.filter { !seats[it].folded }.toIntArray()
                requireInput(contestants.size >= 2) { "At least two players must stay in the hand." }
                val known = LongArray(contestants.size) { Cards.mask(seats[contestants[it]].cards) }
                val missing = IntArray(contestants.size) { 2 - seats[contestants[it]].cards.size }
                val deck = (0 until Cards.COUNT).filter { used and Cards.bit(it) == 0L }.toIntArray()
                val boardMissing = BOARD_CARDS - request.board.size
                requireInput(missing.sum() + boardMissing <= deck.size) { "Not enough cards left to deal." }
                return Plan(
                    seatCount = seats.size,
                    contestants = contestants,
                    knownMasks = known,
                    missing = missing,
                    boardMask = Cards.mask(request.board),
                    deck = deck,
                )
            }
        }
    }

    /**
     * Integer tallies for one chunk of deals. Index p is a contestant, not a seat. Pot shares
     * are in 1/[OddsResult.SHARE_UNIT] units, so every number here is exact.
     */
    private class Tally(private val n: Int, private val seats: Int, private val squares: Boolean) {
        var deals = 0L
        val wins = LongArray(n)
        val ties = LongArray(n)
        val shares = LongArray(n)
        val sharesSq = LongArray(if (squares) n else 0)
        val categories = LongArray(n * CATEGORIES)
        private val strengths = IntArray(n)

        fun showdown(hands: LongArray, board: Long) {
            var best = -1
            var winners = 0
            for (p in 0 until n) {
                val s = HandEvaluator.evaluate(hands[p] or board)
                strengths[p] = s
                categories[p * CATEGORIES + (s ushr HandEvaluator.CATEGORY_SHIFT)]++
                if (s > best) {
                    best = s
                    winners = 1
                } else if (s == best) {
                    winners++
                }
            }
            deals++
            val share = OddsResult.SHARE_UNIT / winners
            for (p in 0 until n) {
                if (strengths[p] != best) continue
                if (winners == 1) wins[p]++ else ties[p]++
                shares[p] += share
                if (squares) sharesSq[p] += share * share
            }
        }

        fun add(other: Tally) {
            deals += other.deals
            for (p in 0 until n) {
                wins[p] += other.wins[p]
                ties[p] += other.ties[p]
                shares[p] += other.shares[p]
                if (squares) sharesSq[p] += other.sharesSq[p]
            }
            for (i in categories.indices) categories[i] += other.categories[i]
        }

        /** Standard error of contestant p's equity, in percentage points. */
        fun stdErrPct(p: Int): Double {
            if (!squares || deals < 2) return 0.0
            val unit = OddsResult.SHARE_UNIT.toDouble()
            val mean = shares[p] / unit / deals
            val meanSq = sharesSq[p] / (unit * unit) / deals
            val variance = (meanSq - mean * mean).coerceAtLeast(0.0) * deals / (deals - 1)
            return PERCENT * sqrt(variance / deals)
        }

        fun maxStdErrPct(): Double = (0 until n).maxOf { stdErrPct(it) }

        fun toResult(plan: Plan, exact: Boolean, complete: Boolean, seed: Long?): OddsResult {
            val bySeat = arrayOfNulls<PlayerOdds>(seats)
            plan.contestants.forEachIndexed { p, seat ->
                val d = deals.toDouble()
                bySeat[seat] = PlayerOdds(
                    folded = false,
                    wins = wins[p],
                    ties = ties[p],
                    potShares = shares[p],
                    winPct = PERCENT * wins[p] / d,
                    tiePct = PERCENT * ties[p] / d,
                    equityPct = PERCENT * shares[p] / (d * OddsResult.SHARE_UNIT),
                    equityStdErr = if (exact) 0.0 else stdErrPct(p),
                    handCategoryPct = List(CATEGORIES) { PERCENT * categories[p * CATEGORIES + it] / d },
                )
            }
            val players = List(seats) { seat -> bySeat[seat] ?: FOLDED }
            return OddsResult(players, deals, exact, complete, if (exact) null else seed)
        }
    }

    companion object {
        const val MAX_SEATS = 10

        /** Deals per Monte Carlo chunk: the unit of parallelism and of cancellation checks. */
        const val BATCH_SIZE = 1024
        private const val FIRST_ROUND_BATCHES = 2
        private const val MAX_ROUND_BATCHES = 32

        private const val BOARD = -1
        private const val BOARD_CARDS = 5
        private const val FLOP_CARDS = 3
        private const val PERCENT = 100.0
        private val CATEGORIES = HandCategory.entries.size

        /** Exact enumeration checks for cancellation every 16,384 showdowns. */
        private const val CANCEL_CHECK_MASK = 0x3FFF

        private val FOLDED = PlayerOdds(
            folded = true, wins = 0, ties = 0, potShares = 0,
            winPct = 0.0, tiePct = 0.0, equityPct = 0.0, equityStdErr = 0.0,
            handCategoryPct = emptyList(),
        )

        /** Number of ways to choose k of n, k <= 5 here. */
        internal fun choose(n: Int, k: Int): Long {
            if (k < 0 || k > n) return 0
            var r = 1L
            for (i in 0 until k) r = r * (n - i) / (i + 1)
            return r
        }

        private fun saturatingMultiply(a: Long, b: Long): Long =
            if (a != 0L && b > Long.MAX_VALUE / a) Long.MAX_VALUE else a * b

        /** Like [require], but throws the [OddsInputException] the UI shows to the user. */
        private inline fun requireInput(valid: Boolean, message: () -> String) {
            if (!valid) throw OddsInputException(message())
        }

        /** SplitMix64 finalizer: well-spread, independent per-batch seeds. */
        @Suppress("MagicNumber") // SplitMix64's published shifts and multipliers
        internal fun mix(seed: Long, batch: Int): Long {
            var z = seed + (batch + 1).toLong() * -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
