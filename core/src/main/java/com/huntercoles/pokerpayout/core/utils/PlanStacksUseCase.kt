package com.huntercoles.pokerpayout.core.utils

import javax.inject.Inject

/**
 * What to plan: [players] starting stacks of [startingStack] from [inventory], keeping
 * [reserveStacks] more stacks' worth back for rebuys and add-ons.
 *
 * @property smallBlind the first small blind (the Tournament's smallest chip). The stack's smallest
 *   chip is the largest chip you own that pays it; smaller chips stay in the box.
 * @property maxColours how many chip values a stack should use at most (the old "denominations").
 * @property curve the shape the counts should follow (internal: the screen calls it "stack shape").
 * @property schedule the clock's schedule, for the color-up plan; null for no plan.
 */
data class StackPlanRequest(
    val inventory: ChipInventory,
    val startingStack: Int,
    val players: Int,
    val smallBlind: Int,
    val reserveStacks: Int = 0,
    val maxColours: Int = DEFAULT_MAX_COLOURS,
    val curve: ChipDistributionCurve = ChipDistributionCurve.LinearSteep,
    val schedule: BlindSchedule? = null
) {
    companion object {
        const val DEFAULT_MAX_COLOURS = 5
    }
}

/** A chip by its colour and value. */
data class ChipRef(val colour: ChipColour, val value: Int)

/** [count] chips of one colour in a stack. */
data class StackChip(val colour: ChipColour, val value: Int, val count: Int) {
    val worth: Long get() = value.toLong() * count
}

/**
 * One player's starting stack, smallest chip first. It always adds up to the starting stack.
 *
 * @property shapeRelaxed your chips couldn't follow the chosen shape's never-rising counts.
 * @property moreColoursThanAsked no stack of at most the chosen number of colours fitted.
 */
data class PlayerStack(
    val chips: List<StackChip>,
    val shapeRelaxed: Boolean = false,
    val moreColoursThanAsked: Boolean = false,
    /** How closely the counts follow the shape, 0 to 1. Internal: never shown (PP-033). */
    val fitScore: Double = 0.0
) {
    val totalChips: Int get() = chips.sumOf { it.count }
    val totalValue: Long get() = chips.sumOf { it.worth }

    fun countOf(value: Int): Int = chips.firstOrNull { it.value == value }?.count ?: 0
}

/** You need [more] more chips of [colour] (worth [value]) to make [stacks] stacks. */
data class ChipShortfall(val colour: ChipColour, val value: Int, val more: Int, val stacks: Int)

/**
 * What is left for rebuys and add-ons once every player has a stack.
 *
 * @property extraStacks how many more stacks just like the players' the leftover chips make.
 * @property runsOutFirst the colours that limit [extraStacks].
 * @property shortfall when [extraStacks] is under [requested], the fewest chips of one colour
 *   that would make the players' stacks and the reserve together.
 */
data class ReserveCheck(
    val requested: Int,
    val extraStacks: Int,
    val runsOutFirst: List<ChipColour>,
    val shortfall: ChipShortfall? = null
) {
    val enough: Boolean get() = extraStacks >= requested
}

/**
 * One color-up: [chips] stop being needed from level [fromLevel] (1-based) and are colored up
 * into [into] at break [breakNumber], which follows level [afterLevel]. With no break before
 * [fromLevel], both are null and it happens at the start of that level.
 *
 * @property needed about how many [into] chips replace the colored-up chips in play (odd chips are
 *   raced off, so "about").
 * @property spare [into] chips still in the box afterwards; negative when you are short.
 */
data class ColorUpStep(
    val chips: List<ChipRef>,
    val into: ChipRef,
    val fromLevel: Int,
    val breakNumber: Int?,
    val afterLevel: Int?,
    val needed: Int,
    val spare: Int
)

/** The color-ups for [stacksInPlay] stacks, in the order they happen. */
data class ColorUpPlan(val steps: List<ColorUpStep>, val stacksInPlay: Int)

/** The answer to a [StackPlanRequest]. */
sealed interface StackPlan {

    /** Every player gets [stack]; [colorUp] is null without a schedule. */
    data class Ready(val stack: PlayerStack, val reserve: ReserveCheck, val colorUp: ColorUpPlan?) : StackPlan

    /**
     * Your chips can't make a stack for every player. [shortfall] is the fewest chips of one
     * colour that would (null when no single colour does), [stacksYouCanMake] how many full stacks
     * they make now, and [stackIfAdded] the players' stack once the [shortfall] is added, with its
     * [colorUp] plan.
     */
    data class Short(
        val shortfall: ChipShortfall?,
        val stacksYouCanMake: Int,
        val stackIfAdded: PlayerStack?,
        val colorUp: ColorUpPlan?
    ) : StackPlan

    /** Problems no number of chips fixes. */
    sealed interface Unplannable : StackPlan

    /** No chips in the set. */
    data object NoChips : Unplannable

    /** None of your chips pays the first small blind (none divides it). */
    data class NoChipForSmallBlind(val smallBlind: Int) : Unplannable

    /** The starting stack is smaller than the smallest chip that pays the small blind. */
    data class StackSmallerThanSmallestChip(val stack: Int, val smallestChip: Int) : Unplannable

    /** Every usable chip is a multiple of [unit] and the stack isn't. */
    data class StackNotReachable(val stack: Int, val unit: Int) : Unplannable {
        val nearestBelow: Int get() = stack - stack % unit
        val nearestAbove: Int get() = nearestBelow + unit
    }

    /** The stack is worth more than [ChipDistributionOptimizer.MAX_CAPPED_UNITS] of its smallest unit. */
    data class StackTooBig(val stack: Int, val unit: Int) : Unplannable

    /** A number below its minimum: stack and players at least 1, reserve at least 0. */
    data class InvalidInput(val field: Field, val value: Int) : Unplannable

    enum class Field { STARTING_STACK, PLAYERS, RESERVE, SMALL_BLIND }
}

/**
 * Plans starting stacks from the chips you own (PP-033, S11).
 *
 * 1. The stack's smallest chip is the largest chip you own that pays the first small blind.
 *    Candidates are it plus every chip you own above it worth at most a third of the stack.
 * 2. Each chip is capped at what you own divided by the stacks needed. The stack itself comes from
 *    [ChipDistributionOptimizer.optimizeWithinCaps]: exact, within the caps, closest to the shape.
 * 3. It plans for the players plus the reserve first. If that can't be done, for the players alone,
 *    and the reserve check says what is missing. If even that can't be done, the answer is
 *    [StackPlan.Short], with the fewest chips of one colour that would fix it.
 * 4. The color-up plan follows the clock's schedule ([ColorUpPlanner], placed on breaks the way the
 *    clock places them) and counts the chips each color-up needs from the box.
 *
 * Pure and deterministic; nothing here throws for any input.
 */
class PlanStacksUseCase @Inject constructor() {

    operator fun invoke(request: StackPlanRequest): StackPlan = when (val candidates = candidates(request)) {
        is Candidates.None -> candidates.why
        is Candidates.Chips -> plan(request, Planner(candidates.chips, request.startingStack, request.maxColours, request.curve))
    }

    /** The chips a stack may use, or why no stack can be planned. */
    private sealed interface Candidates {
        class Chips(val chips: List<InventoryChip>) : Candidates
        class None(val why: StackPlan.Unplannable) : Candidates
    }

    @Suppress("ReturnCount") // one early return per unplannable input, in the order they are checked
    private fun candidates(request: StackPlanRequest): Candidates {
        invalid(request)?.let { return Candidates.None(it) }
        val owned = request.inventory.owned
        if (owned.isEmpty()) return Candidates.None(StackPlan.NoChips)
        val smallBlind = request.smallBlind
        val smallest = owned.filter { smallBlind % it.value == 0 }.maxByOrNull { it.value }
            ?: return Candidates.None(StackPlan.NoChipForSmallBlind(smallBlind))
        val stack = request.startingStack
        if (stack < smallest.value) return Candidates.None(StackPlan.StackSmallerThanSmallestChip(stack, smallest.value))
        val chips = owned.filter { it.value == smallest.value || it.value in (smallest.value + 1)..(stack / THIRD) }
        val unit = chips.fold(0) { acc, chip -> gcd(acc, chip.value) }
        return when {
            stack % unit != 0 -> Candidates.None(StackPlan.StackNotReachable(stack, unit))
            stack / unit > ChipDistributionOptimizer.MAX_CAPPED_UNITS -> Candidates.None(StackPlan.StackTooBig(stack, unit))
            else -> Candidates.Chips(chips)
        }
    }

    /** Players and reserve; else players alone, with the reserve's shortfall; else what is short. */
    @Suppress("ReturnCount") // one return per answer, best first
    private fun plan(request: StackPlanRequest, planner: Planner): StackPlan {
        val players = request.players
        val wanted = players + request.reserveStacks
        val full = planner.plan(wanted)
        if (full != null) {
            val reserve = reserveCheck(full, request, shortfall = null)
            return StackPlan.Ready(full, reserve, colorUpPlan(full, request.inventory, wanted, request.schedule))
        }
        val forPlayers = if (wanted != players) planner.plan(players) else null
        if (forPlayers != null) {
            val reserve = reserveCheck(forPlayers, request, planner.cheapestFix(wanted))
            val inPlay = players + reserve.extraStacks.coerceAtMost(request.reserveStacks)
            return StackPlan.Ready(forPlayers, reserve, colorUpPlan(forPlayers, request.inventory, inPlay, request.schedule))
        }
        val fix = planner.cheapestFix(players)
        val stackIfAdded = fix?.let { planner.withMore(it).plan(players) }
        val colorUp = stackIfAdded?.let { stack ->
            val withFix = request.inventory.withCount(fix.colour, (request.inventory[fix.colour]?.count ?: 0) + fix.more)
            colorUpPlan(stack, withFix, players, request.schedule)
        }
        return StackPlan.Short(fix, planner.maxStacks(players - 1), stackIfAdded, colorUp)
    }

    private fun invalid(request: StackPlanRequest): StackPlan.InvalidInput? = when {
        request.startingStack < 1 -> StackPlan.InvalidInput(StackPlan.Field.STARTING_STACK, request.startingStack)
        request.players < 1 -> StackPlan.InvalidInput(StackPlan.Field.PLAYERS, request.players)
        request.reserveStacks < 0 -> StackPlan.InvalidInput(StackPlan.Field.RESERVE, request.reserveStacks)
        request.smallBlind < 1 -> StackPlan.InvalidInput(StackPlan.Field.SMALL_BLIND, request.smallBlind)
        else -> null
    }

    /** How many more stacks like [stack] are left once each player has one. */
    private fun reserveCheck(stack: PlayerStack, request: StackPlanRequest, shortfall: ChipShortfall?): ReserveCheck {
        val left = stack.chips.map { chip ->
            val owned = request.inventory.chipWorth(chip.value)?.count ?: 0
            chip.colour to (owned - request.players * chip.count) / chip.count
        }
        val extra = left.minOf { it.second }.coerceAtLeast(0)
        return ReserveCheck(
            requested = request.reserveStacks,
            extraStacks = extra,
            runsOutFirst = left.filter { it.second <= extra }.map { it.first },
            shortfall = shortfall.takeIf { extra < request.reserveStacks }
        )
    }

    /** Chips that drop out together: at break [breakIndex] (0-based), or on level [levelIndex] when it is -1. */
    private class DropGroup(val levelIndex: Int, val breakIndex: Int, val chips: MutableList<Int>)

    /**
     * The color-ups the schedule calls for with these chips ([ColorUpPlanner.planFor] over every
     * chip you own from the stack's smallest up), placed on breaks the way the clock places them.
     * Each dropped chip goes into the next larger chip that stays in play and is a multiple of it.
     *
     * Chips in play are [stacks] × the stack, and chips only change hands, so each value's total in
     * play only moves at a color-up. The box holds the rest; each color-up takes what it needs.
     */
    private fun colorUpPlan(stack: PlayerStack, inventory: ChipInventory, stacks: Int, schedule: BlindSchedule?): ColorUpPlan? {
        if (schedule == null) return null
        val chain = inventory.owned.map { it.value }.filter { it >= stack.chips.first().value }
        val breaks = schedule.breaks.sortedBy { it.afterLevel }
        val ledger = ChipLedger(stack, inventory, stacks)
        val dropped = mutableSetOf<Int>()
        val steps = mutableListOf<ColorUpStep>()
        dropGroups(ColorUpPlanner.planFor(schedule.levels, chain), breaks).forEach { group ->
            dropped += group.chips
            // Each chip goes into the next larger chip that stays in play and is a multiple of it
            group.chips.groupBy { chip -> chain.firstOrNull { it > chip && it % chip == 0 && it !in dropped } }
                .entries.sortedBy { it.key ?: Int.MAX_VALUE }
                .forEach { (into, chips) ->
                    val move = into?.let { ledger.colorUp(chips, it) } ?: return@forEach
                    val brk = breaks.getOrNull(group.breakIndex)
                    steps += ColorUpStep(
                        chips = move.chips.map { ref(inventory, it) },
                        into = ref(inventory, into),
                        fromLevel = group.levelIndex + 1,
                        breakNumber = brk?.number,
                        afterLevel = brk?.afterLevel,
                        needed = move.needed,
                        spare = move.spare
                    )
                }
        }
        return ColorUpPlan(steps, stacks)
    }

    /** [drops] (level index -> chips) grouped by when they go: chips placed on the same break go together. */
    private fun dropGroups(drops: Map<Int, List<Int>>, breaks: List<ScheduledBreak>): List<DropGroup> {
        val breakIndexes = breaks.map { it.afterLevel - 1 }
        val groups = mutableListOf<DropGroup>()
        drops.forEach { (levelIndex, chips) ->
            val breakIndex = ColorUpPlanner.breakFor(levelIndex, breakIndexes)
            val last = groups.lastOrNull()
            if (last != null && breakIndex >= 0 && last.breakIndex == breakIndex) {
                last.chips += chips
            } else {
                groups += DropGroup(levelIndex, breakIndex, chips.toMutableList())
            }
        }
        return groups
    }

    /** One color-up's chips (those anyone holds), the bigger chips it needs, and how many are left in the box. */
    private class Move(val chips: List<Int>, val needed: Int, val spare: Int)

    /**
     * What is in play and in the box, by chip value. Chips in play start as [stacks] × the stack;
     * chips only change hands, so a value's total in play moves only at a color-up.
     */
    private class ChipLedger(stack: PlayerStack, inventory: ChipInventory, stacks: Int) {
        private val inPlay = mutableMapOf<Int, Long>()
        private val box = mutableMapOf<Int, Long>()

        init {
            inventory.owned.forEach { chip ->
                val dealt = stack.countOf(chip.value).toLong() * stacks
                inPlay[chip.value] = dealt * chip.value
                box[chip.value] = chip.count - dealt
            }
        }

        /** Colors [chips] up into [into]; null when nobody holds any of them. */
        fun colorUp(chips: List<Int>, into: Int): Move? {
            val held = chips.filter { (inPlay[it] ?: 0L) > 0L }
            val value = held.sumOf { inPlay.getValue(it) }
            chips.forEach { inPlay[it] = 0L }
            if (value == 0L) return null
            val needed = (value + into - 1) / into
            val spare = (box[into] ?: 0L) - needed
            box[into] = spare
            inPlay[into] = (inPlay[into] ?: 0L) + value
            return Move(held, needed.toIntClamped(), spare.toIntClamped())
        }

        private fun Long.toIntClamped(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun ref(inventory: ChipInventory, value: Int): ChipRef =
        ChipRef(inventory.chipWorth(value)?.colour ?: ChipColour.forStandardValue(value) ?: ChipColour.Grey, value)

    /** Plans over one inventory's candidate chips (ascending), for any number of stacks. */
    private class Planner(
        private val candidates: List<InventoryChip>,
        private val stack: Int,
        private val maxColours: Int,
        private val curve: ChipDistributionCurve,
        private val counts: IntArray = IntArray(candidates.size) { candidates[it].count }
    ) {
        private val values = IntArray(candidates.size) { candidates[it].value }

        private fun caps(stacks: Int, owned: IntArray = counts) = IntArray(owned.size) { owned[it] / stacks }

        fun plan(stacks: Int): PlayerStack? =
            ChipDistributionOptimizer.optimizeWithinCaps(stack, values, caps(stacks), maxColours, curve)?.let { found ->
                val result = found.distribution
                PlayerStack(
                    chips = result.denominations.zip(result.quantities).map { (value, count) ->
                        StackChip(candidates.first { it.value == value }.colour, value, count)
                    },
                    shapeRelaxed = found.shapeRelaxed,
                    moreColoursThanAsked = found.moreChipsThanAsked,
                    fitScore = result.fitScore
                )
            }

        fun canMake(stacks: Int, owned: IntArray = counts): Boolean =
            stacks <= 0 || ChipDistributionOptimizer.canMakeWithinCaps(stack, values, caps(stacks, owned))

        /** Most full stacks (0 to [upTo]) these chips make. */
        fun maxStacks(upTo: Int): Int {
            var low = 0
            var high = upTo.coerceAtLeast(0)
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (canMake(mid)) low = mid else high = mid - 1
            }
            return low
        }

        /**
         * The fewest extra chips of one colour that make [stacks] stacks, or null when no single
         * colour does. Ties go to the smaller chip.
         */
        fun cheapestFix(stacks: Int): ChipShortfall? = candidates.indices.mapNotNull { i ->
            val current = counts[i] / stacks
            val most = stack / values[i] // a stack never holds more of a chip than this
            if (current >= most || !canMake(stacks, counts.withCap(i, most, stacks))) return@mapNotNull null
            var low = current + 1
            var high = most
            while (low < high) {
                val mid = (low + high) / 2
                if (canMake(stacks, counts.withCap(i, mid, stacks))) high = mid else low = mid + 1
            }
            ChipShortfall(candidates[i].colour, values[i], (low.toLong() * stacks - counts[i]).toCount(), stacks)
        }.minWithOrNull(compareBy<ChipShortfall> { it.more }.thenBy { it.value })

        fun withMore(fix: ChipShortfall): Planner {
            val i = candidates.indexOfFirst { it.colour == fix.colour }
            val more = counts.copyOf().also { it[i] += fix.more }
            return Planner(candidates, stack, maxColours, curve, more)
        }

        /** These counts with chip [i] raised to exactly [cap] per stack for [stacks] stacks. */
        private fun IntArray.withCap(i: Int, cap: Int, stacks: Int): IntArray =
            copyOf().also { it[i] = maxOf(it[i], (cap.toLong() * stacks).toCount()) }

        private fun Long.toCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        /** The largest chip in a stack is worth at most a third of it (the smallest chip excepted). */
        const val THIRD = 3

        fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }
    }
}
