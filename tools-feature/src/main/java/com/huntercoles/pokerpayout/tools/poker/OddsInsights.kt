package com.huntercoles.pokerpayout.tools.poker

import kotlin.random.Random

/**
 * One card that can come next, and what it does: every seat's exact equity once it is out, and
 * who leads then.
 *
 * @property equityPct one entry per request seat (0 for a folded seat).
 * @property leader the seat with the highest equity after this card, or `null` when two or more
 *   share the top. On the river that is simply the winner.
 */
data class NextCard(
    val card: Int,
    val equityPct: List<Double>,
    val leader: Int?,
)

/**
 * Every live next card on a flop (the turn) or a turn (the river), from
 * [OddsEngine.nextCardBreakdown].
 *
 * @property cards one entry per card that can still come, in deck order.
 * @property currentLeader the seat whose made hand is best right now ("ahead right now"), or
 *   `null` when made hands tie or a hand isn't fully known.
 */
data class NextCardBreakdown(
    val cards: List<NextCard>,
    val currentLeader: Int?,
) {
    /** How many next cards put [seat] in the lead. */
    fun leadCount(seat: Int): Int = cards.count { it.leader == seat }

    /** The next cards that put [seat] in the lead (its outs), in deck order. */
    fun outs(seat: Int): List<Int> = cards.filter { it.leader == seat }.map { it.card }

    /** Where [card] leads, or `null` if it can't come (it's on the table). */
    fun of(card: Int): NextCard? = cards.firstOrNull { it.card == card }
}

/** A hand's made strength in table terms ("Overpair", "Top pair"), from [OddsInsights.describe]. */
enum class MadeKind {
    /** Before the flop: a pocket pair; after it, a pocket pair below the board's top card. ranks = [pair]. */
    POCKET_PAIR,

    /** Before the flop: two different ranks. ranks = [high, low]; see [MadeHand.suited]. */
    UNPAIRED,

    /** ranks = [high card]. */
    HIGH_CARD,

    /** The only pair is on the board. ranks = [pair]. */
    BOARD_PAIR,

    /** A pocket pair above every board card. ranks = [pair]. */
    OVERPAIR,

    /** A hole card pairs the board's top card. ranks = [pair]. */
    TOP_PAIR,

    /** A hole card pairs the board's second card. ranks = [pair]. */
    SECOND_PAIR,

    /** A hole card pairs a board card below the second and above the lowest. ranks = [pair]. */
    MIDDLE_PAIR,

    /** A hole card pairs the board's lowest card. ranks = [pair]. */
    BOTTOM_PAIR,

    /** ranks = [high pair, low pair]. */
    TWO_PAIR,

    /** A pocket pair plus one on the board. ranks = [rank]. */
    SET,

    /** One hole card plus a pair on the board. ranks = [rank]. */
    TRIPS,

    /** Three of a kind on the board alone. ranks = [rank]. */
    BOARD_TRIPS,

    /** ranks = [top card]. */
    STRAIGHT,

    /** ranks = [top card]. */
    FLUSH,

    /** ranks = [trips, pair]. */
    FULL_HOUSE,

    /** ranks = [rank]. */
    QUADS,

    /** ranks = [top card]. */
    STRAIGHT_FLUSH,
    ROYAL_FLUSH,
}

/** A made hand: what it is and the ranks that name it (0 = deuce .. 12 = ace). */
data class MadeHand(val kind: MadeKind, val ranks: List<Int> = emptyList(), val suited: Boolean = false)

/** What a hand can still become with one more card. Flush draws come before straight draws. */
enum class Draw {
    /** Four to the best flush still possible. */
    NUT_FLUSH_DRAW,
    FLUSH_DRAW,

    /** Four in a row, open at both ends: 8 outs. */
    OPEN_ENDED,

    /** Two different inside cards complete a straight: 8 outs. */
    DOUBLE_GUTSHOT,

    /** One rank completes a straight: 4 outs. */
    GUTSHOT,
}

/**
 * A hand right now: its [made] hand and its [draws] ("Overpair, queens"; "Ace high" with a nut
 * flush draw and a gutshot). Draws are only named on the flop and the turn.
 *
 * @property flushDrawSuit the suit of the flush draw, if there is one (0..3 = c, d, h, s).
 */
data class HandDescription(val made: MadeHand, val draws: List<Draw>, val flushDrawSuit: Int? = null)

/**
 * A group of outs as people say them: "9 spades", "3 aces", "the Q♣".
 */
sealed interface OutGroup {
    /** [count] cards of one [suit] (the flush draw's suit). */
    data class Suit(val suit: Int, val count: Int) : OutGroup

    /** [count] cards of one [rank]. */
    data class Rank(val rank: Int, val count: Int) : OutGroup

    /** One card, named. */
    data class Single(val card: Int) : OutGroup
}

/**
 * One row of "By the river": how often a hand ends as [weakest] or, when [strongest] is higher,
 * anything from [weakest] up to [strongest] ("Flush+").
 */
data class OutlookRow(val weakest: HandCategory, val strongest: HandCategory, val pct: Double) {
    val isRange: Boolean get() = strongest != weakest
}

/**
 * Plain-language insight on a hand: made-hand and draw labels, who is ahead right now, outs as
 * people count them, and a short "by the river" summary. Pure functions, no Android types.
 */
object OddsInsights {

    private const val BOARD_FLOP = 3
    private const val BOARD_TURN = 4
    private const val BOARD_MAX = 5

    /** Rows in "By the river". */
    const val OUTLOOK_ROWS = 4

    /** The strongest categories merge into one "X+" row until it holds at least this many percent. */
    const val OUTLOOK_BUCKET_PCT = 5.0

    /** Categories rarer than this many percent are left out of "By the river". */
    private const val OUTLOOK_MIN_PCT = 0.05

    /**
     * Names [hole] (two known cards) on [board] (0..5 cards). Before the flop (fewer than three
     * board cards) it names the starting hand: a pocket pair, or two ranks suited or offsuit.
     *
     * @throws IllegalArgumentException unless [hole] has two cards and every card is distinct.
     */
    fun describe(hole: List<Int>, board: List<Int>): HandDescription {
        require(hole.size == 2) { "describe needs two hole cards, got ${hole.size}" }
        require(board.size <= BOARD_MAX) { "The board has at most five cards" }
        require((hole + board).toSet().size == hole.size + board.size) { "Duplicate card" }
        return if (board.size < BOARD_FLOP) startingHand(hole) else postflop(hole, board)
    }

    /**
     * The seat whose made hand is best right now, or `null` if the board has fewer than three
     * cards, a contestant's hand isn't fully known, or the best hands tie. Folded seats don't count.
     */
    fun madeLeader(request: OddsRequest): Int? {
        val contestants = request.seats.indices.filter { !request.seats[it].folded }
        val comparable = request.board.size >= BOARD_FLOP && contestants.all { request.seats[it].cards.size == 2 }
        if (!comparable || contestants.isEmpty()) return null
        val boardMask = Cards.mask(request.board)
        val strengths = contestants.associateWith { HandEvaluator.evaluate(Cards.mask(request.seats[it].cards) or boardMask) }
        val best = strengths.values.max()
        return strengths.filterValues { it == best }.keys.singleOrNull()
    }

    /**
     * The seat the next-card grid is drawn for: of the contestants who are not ahead right now,
     * the one with the most cards that put it in the lead (ties go to the higher [equityPct]).
     * `null` with fewer than two contestants.
     */
    fun focusSeat(breakdown: NextCardBreakdown, request: OddsRequest, equityPct: List<Double>): Int? {
        val contestants = request.seats.indices.filter { !request.seats[it].folded }
        if (contestants.size < 2) return null
        val candidates = contestants.filter { it != breakdown.currentLeader }
        return candidates.maxWithOrNull(
            compareBy<Int>({ breakdown.leadCount(it) }, { equityPct.getOrElse(it) { 0.0 } }, { -it }),
        )
    }

    /**
     * Groups [outs] the way people count them: the flush draw's suit first ("9 spades"), then
     * the remaining cards by rank, highest first ("3 aces", "3 kings"), then lone cards named
     * ("the Q♣"). A suit or rank with only one card left is named as a card.
     */
    fun groupOuts(outs: List<Int>, flushSuit: Int?): List<OutGroup> {
        val suited = outs.filter { suitOf(it) == flushSuit }
        val groups = mutableListOf<OutGroup>()
        val rest = if (flushSuit != null && suited.size >= 2) {
            groups += OutGroup.Suit(flushSuit, suited.size)
            outs - suited.toSet()
        } else {
            outs
        }
        val byRank = rest.groupBy(::rankOf).toSortedMap(compareByDescending { it })
        byRank.filterValues { it.size >= 2 }.forEach { (rank, cards) -> groups += OutGroup.Rank(rank, cards.size) }
        byRank.filterValues { it.size == 1 }.forEach { (_, cards) -> groups += OutGroup.Single(cards.single()) }
        return groups
    }

    /**
     * "By the river" for one seat from its [categoryPct] (indexed by [HandCategory.ordinal], in
     * percent): the strongest categories merged into one "X+" row until it holds at least
     * [OUTLOOK_BUCKET_PCT], plus the most likely of the rest, [OUTLOOK_ROWS] rows in all, most
     * likely first. Categories under 0.05% are left out.
     */
    fun riverOutlook(categoryPct: List<Double>): List<OutlookRow> {
        fun pct(c: HandCategory) = categoryPct.getOrElse(c.ordinal) { 0.0 }
        val present = HandCategory.entries.filter { pct(it) >= OUTLOOK_MIN_PCT }.sortedDescending()
        val rows = if (present.size <= OUTLOOK_ROWS) {
            present.map { OutlookRow(it, it, pct(it)) }
        } else {
            // Merge from the strongest down; leave OUTLOOK_ROWS - 1 categories for their own rows.
            var merged = 0
            var bucket = 0.0
            while (merged < present.size - (OUTLOOK_ROWS - 1) && (merged == 0 || bucket < OUTLOOK_BUCKET_PCT)) {
                bucket += pct(present[merged])
                merged++
            }
            val others = present.drop(merged).sortedByDescending(::pct).take(OUTLOOK_ROWS - 1)
            listOf(OutlookRow(present[merged - 1], present[0], bucket)) + others.map { OutlookRow(it, it, pct(it)) }
        }
        return rows.sortedByDescending { it.pct }
    }

    // ------------------------------------------------------------------ made hands

    private fun startingHand(hole: List<Int>): HandDescription {
        val (a, b) = hole.map(::rankOf).sortedDescending()
        val made = if (a == b) {
            MadeHand(MadeKind.POCKET_PAIR, listOf(a))
        } else {
            MadeHand(MadeKind.UNPAIRED, listOf(a, b), suited = suitOf(hole[0]) == suitOf(hole[1]))
        }
        return HandDescription(made, emptyList())
    }

    private fun postflop(hole: List<Int>, board: List<Int>): HandDescription {
        val strength = HandEvaluator.evaluate(hole + board)
        val category = HandEvaluator.category(strength)
        val made = madeHand(category, HandEvaluator.ranks(strength), hole, board)
        if (board.size > BOARD_TURN || category >= HandCategory.FLUSH) return HandDescription(made, emptyList())
        val flush = DrawFinder.flushDraw(hole, board)
        val draws = buildList {
            if (flush != null) add(if (flush.second) Draw.NUT_FLUSH_DRAW else Draw.FLUSH_DRAW)
            if (category < HandCategory.STRAIGHT) DrawFinder.straightDraw(hole, board)?.let(::add)
        }
        return HandDescription(made, draws, flush?.first)
    }

    private fun madeHand(category: HandCategory, ranks: List<Int>, hole: List<Int>, board: List<Int>): MadeHand {
        val holeRanks = hole.map(::rankOf)
        return when (category) {
            HandCategory.PAIR -> pairHand(ranks[0], holeRanks, board.map(::rankOf).distinct().sortedDescending())
            HandCategory.TRIPS -> tripsHand(ranks[0], holeRanks)
            HandCategory.TWO_PAIR -> MadeHand(MadeKind.TWO_PAIR, ranks.take(2))
            HandCategory.FULL_HOUSE -> MadeHand(MadeKind.FULL_HOUSE, ranks.take(2))
            HandCategory.STRAIGHT_FLUSH ->
                if (ranks[0] == Cards.ACE) MadeHand(MadeKind.ROYAL_FLUSH) else MadeHand(MadeKind.STRAIGHT_FLUSH, listOf(ranks[0]))
            else -> MadeHand(SIMPLE_KINDS.getValue(category), listOf(ranks[0]))
        }
    }

    /** Categories named by their first rank alone. */
    private val SIMPLE_KINDS = mapOf(
        HandCategory.HIGH_CARD to MadeKind.HIGH_CARD,
        HandCategory.STRAIGHT to MadeKind.STRAIGHT,
        HandCategory.FLUSH to MadeKind.FLUSH,
        HandCategory.QUADS to MadeKind.QUADS,
    )

    private fun pairHand(pair: Int, holeRanks: List<Int>, boardRanks: List<Int>): MadeHand {
        val pocket = holeRanks[0] == holeRanks[1]
        val kind = when {
            pocket && holeRanks[0] == pair -> if (pair > boardRanks.first()) MadeKind.OVERPAIR else MadeKind.POCKET_PAIR
            pair !in holeRanks -> MadeKind.BOARD_PAIR
            pair == boardRanks.first() -> MadeKind.TOP_PAIR
            pair == boardRanks.getOrNull(1) -> MadeKind.SECOND_PAIR
            pair == boardRanks.last() -> MadeKind.BOTTOM_PAIR
            else -> MadeKind.MIDDLE_PAIR
        }
        return MadeHand(kind, listOf(pair))
    }

    private fun tripsHand(rank: Int, holeRanks: List<Int>): MadeHand {
        val kind = when {
            holeRanks[0] == rank && holeRanks[1] == rank -> MadeKind.SET
            rank in holeRanks -> MadeKind.TRIPS
            else -> MadeKind.BOARD_TRIPS
        }
        return MadeHand(kind, listOf(rank))
    }
}

/** Flush and straight draws that use at least one hole card. */
private object DrawFinder {
    private const val FLUSH_CARDS = 5
    private const val RUN = 4
    private const val RANK_MASK = 0x1FFF

    /** (suit, nut) of a four-card flush draw that uses a hole card, or null. */
    fun flushDraw(hole: List<Int>, board: List<Int>): Pair<Int, Boolean>? {
        val all = hole + board
        val suit = (0 until Cards.SUITS).firstOrNull { s ->
            all.count { suitOf(it) == s } == FLUSH_CARDS - 1 && hole.any { suitOf(it) == s }
        } ?: return null
        val onBoard = board.filter { suitOf(it) == suit }.map(::rankOf).toSet()
        val bestUnseen = (Cards.ACE downTo Cards.DEUCE).first { it !in onBoard }
        return suit to hole.any { suitOf(it) == suit && rankOf(it) == bestUnseen }
    }

    /** The straight draw that uses a hole card, if any. */
    fun straightDraw(hole: List<Int>, board: List<Int>): Draw? {
        val all = rankMask(hole + board)
        val boardOnly = rankMask(board)
        val completing = (Cards.DEUCE..Cards.ACE).filter { r ->
            val bit = 1 shl r
            val top = HandEvaluator.STRAIGHT_TOP[all or bit]
            all and bit == 0 && top >= 0 && top != HandEvaluator.STRAIGHT_TOP[boardOnly or bit]
        }
        return when {
            completing.isEmpty() -> null
            completing.size == 1 -> Draw.GUTSHOT
            isOpenEnded(all, completing) -> Draw.OPEN_ENDED
            else -> Draw.DOUBLE_GUTSHOT
        }
    }

    /** Four ranks in a row (the ace also plays low) with a completing rank at each end. */
    private fun isOpenEnded(ranks: Int, completing: List<Int>): Boolean {
        // Ace-low view: bit 0 is the ace as a one, bit r + 1 is rank r.
        val ext = (ranks shl 1) or (if (ranks and (1 shl Cards.ACE) != 0) 1 else 0)
        fun rankAt(i: Int): Int = if (i == 0) Cards.ACE else i - 1
        val window = (1 shl RUN) - 1
        return (1..(Cards.RANKS + 1 - RUN)).any { start ->
            ext and (window shl start) == window shl start &&
                rankAt(start - 1) in completing &&
                start + RUN <= Cards.RANKS && rankAt(start + RUN) in completing
        }
    }

    private fun rankMask(cards: List<Int>): Int = cards.fold(0) { m, c -> m or (1 shl rankOf(c)) } and RANK_MASK
}

/**
 * Deals run-it-out boards from a seeded shuffle, so a run can be replayed: the same request,
 * seed and run number always give the same cards.
 */
object RunOuts {

    private const val BOARD_CARDS = 5

    /**
     * The rest of the board for run [run], [count] times over (run it twice), dealt from one
     * shuffle of the live deck without replacement. Every contestant's hand must be known.
     *
     * @return [count] lists of `5 - board.size` cards each.
     * @throws OddsInputException if a hand isn't fully known, a card repeats, or the deck runs out.
     */
    fun deal(request: OddsRequest, seed: Long, run: Int, count: Int = 1): List<List<Int>> {
        require(count >= 1) { "count must be positive" }
        val contestants = request.seats.filter { !it.folded }
        val used = request.seats.flatMap { it.cards } + request.board + request.dead
        val deck = (0 until Cards.COUNT).filter { it !in used }.toIntArray()
        val per = BOARD_CARDS - request.board.size
        val problem = when {
            contestants.size < 2 -> "At least two players must stay in the hand."
            contestants.any { it.cards.size != 2 } -> "Every hand must be known to run it out."
            used.toSet().size != used.size -> "A card is used twice."
            per * count > deck.size -> "Not enough cards left to deal."
            else -> null
        }
        if (problem != null) throw OddsInputException(problem)
        val rng = Random(OddsEngine.mix(seed, run))
        for (j in 0 until per * count) {
            val r = j + rng.nextInt(deck.size - j)
            val t = deck[j]
            deck[j] = deck[r]
            deck[r] = t
        }
        return List(count) { i -> deck.slice(i * per until (i + 1) * per) }
    }
}
