package com.huntercoles.pokerpayout.core.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * [PlanStacksUseCase]: starting stacks from the chips you own (PP-033, S11).
 *
 * The contract, checked on thousands of seeded random sets against an independent brute-force
 * oracle ([Oracle]): a planned stack adds up to the starting stack exactly and never uses more of a
 * chip than the set holds for the stacks it was planned for; a "short" answer means no stack
 * exists, and its fix is real, minimal and the cheapest single colour; the reserve check counts the
 * leftover stacks right, and only falls short when no stack leaves that reserve.
 */
class PlanStacksUseCaseTest {

    private val plan = PlanStacksUseCase()

    private fun chip(colour: ChipColour, value: Int, count: Int) = InventoryChip(colour, value, count)

    private fun set(vararg chips: InventoryChip) = ChipInventory.of(chips.toList())

    private fun ready(request: StackPlanRequest): StackPlan.Ready {
        val outcome = plan(request)
        assertTrue(outcome is StackPlan.Ready, "expected a plan for $request, got $outcome")
        return outcome as StackPlan.Ready
    }

    private fun PlayerStack.pairs() = chips.map { it.value to it.count }

    // ------------------------------------------------------------------ examples

    @Nested
    inner class Examples {

        @Test
        fun `the mockup's 500-chip set gives 9 players 5,000 each and counts what is left`() {
            val request = StackPlanRequest(ChipInventory.HOME_SET, startingStack = 5_000, players = 9, smallBlind = 25)
            val result = ready(request)

            assertEquals(5_000L, result.stack.totalValue)
            assertEquals(25, result.stack.chips.first().value, "the 25s pay the 25 small blind")
            result.stack.chips.forEach { c ->
                assertTrue(9 * c.count <= ChipInventory.HOME_SET.chipWorth(c.value)!!.count, "$c for 9 players")
            }
            val left = result.stack.chips.map { c -> (ChipInventory.HOME_SET.chipWorth(c.value)!!.count - 9 * c.count) / c.count }
            assertEquals(left.min(), result.reserve.extraStacks)
            assertEquals(0, result.reserve.requested)
            assertTrue(result.reserve.enough)
            assertNull(result.colorUp, "no schedule, no color-up plan")
        }

        @Test
        fun `a set made from the old calculator's answer gives the same stack it showed`() {
            // v1.3.0 defaults: 5,000 from 50s, 5 denominations, Linear Steep, 5 players
            val old = ChipDistributionOptimizer.optimize(5_000, 50, 5, ChipDistributionCurve.LinearSteep)
            val oldPairs = (old as ChipDistributionOutcome.Success).distribution.let { it.denominations.zip(it.quantities) }
            assertEquals(listOf(50 to 9, 100 to 8, 250 to 5, 500 to 3, 1_000 to 1), oldPairs)

            val inventory = ChipInventory.fromLastStack(oldPairs, players = 5)!!
            val result = ready(StackPlanRequest(inventory, startingStack = 5_000, players = 5, smallBlind = 50))
            assertEquals(oldPairs, result.stack.pairs())
        }

        @Test
        fun `with plenty of every standard chip the planner gives the old calculator's answers`() {
            val plenty = ChipInventory.of(ChipColour.entries.map { InventoryChip(it, it.standardValue, ChipInventory.MAX_COUNT) })
            val configs = listOf(500, 1_000, 1_500, 2_000, 3_000, 5_000, 7_500, 10_000, 20_000, 50_000).flatMap { stack ->
                listOf(5, 10, 25, 50, 100).flatMap { smallest ->
                    (3..6).flatMap { k ->
                        ChipDistributionCurve.getAllCurves().map { StackPlanRequest(plenty, stack, 1, smallest, 0, k, it) }
                    }
                }
            }
            var compared = 0
            configs.forEach { request ->
                val label = "${request.startingStack}/${request.smallBlind}/${request.maxColours}/${request.curve.displayName}"
                val old = request.run { ChipDistributionOptimizer.optimize(startingStack, smallBlind, maxColours, curve) }
                val expected = (old as? ChipDistributionOutcome.Success)?.distribution
                    ?.takeIf { d -> d.quantities.all { it <= ChipInventory.MAX_COUNT } }
                    ?: return@forEach
                assertEquals(expected.denominations.zip(expected.quantities), ready(request).stack.pairs(), label)
                compared++
            }
            assertTrue(compared > 900, "compared $compared configs")
        }

        @Test
        fun `too few blacks for 9 stacks of 1,000 is short by exactly the blacks it needs`() {
            // 25s (10 a stack at most) and 100s (5 a stack at most) can't make 1,000: 25a + 100b = 1,000
            // with a ≤ 10 needs b ≥ 8. Eight 100s a stack needs 72 blacks: 27 more. (Greens would take 90 more.)
            val inventory = set(chip(ChipColour.Green, 25, 90), chip(ChipColour.Black, 100, 45))
            val outcome = plan(StackPlanRequest(inventory, startingStack = 1_000, players = 9, smallBlind = 25))

            assertTrue(outcome is StackPlan.Short, "$outcome")
            outcome as StackPlan.Short
            assertEquals(ChipShortfall(ChipColour.Black, 100, more = 27, stacks = 9), outcome.shortfall)
            // 12 × 25 + 7 × 100 makes 6 stacks (90 / 12 = 7, 45 / 7 = 6); nothing makes 7
            assertEquals(6, outcome.stacksYouCanMake)
            val fixed = outcome.stackIfAdded!!
            assertEquals(1_000L, fixed.totalValue)
            assertTrue(fixed.countOf(100) <= 72 / 9 && fixed.countOf(25) <= 10, "${fixed.pairs()}")
        }

        @Test
        fun `a reserve is planned for when the set can hold it`() {
            val inventory = set(
                chip(ChipColour.Green, 25, 150),
                chip(ChipColour.Black, 100, 150),
                chip(ChipColour.Purple, 500, 100),
            )
            val result = ready(StackPlanRequest(inventory, 2_000, players = 8, smallBlind = 25, reserveStacks = 4))

            assertTrue(result.reserve.enough)
            assertTrue(result.reserve.extraStacks >= 4)
            result.stack.chips.forEach { c ->
                assertTrue(12 * c.count <= inventory.chipWorth(c.value)!!.count, "$c for 12 stacks")
            }
        }

        @Test
        fun `a reserve the set can't hold is reported with the chips that would make it`() {
            // 1,000 from 25s and 100s: 9 stacks fit (say 8 × 25 + 8 × 100), 12 don't
            val inventory = set(chip(ChipColour.Green, 25, 100), chip(ChipColour.Black, 100, 80))
            val result = ready(StackPlanRequest(inventory, 1_000, players = 9, smallBlind = 25, reserveStacks = 3))

            assertFalse(result.reserve.enough)
            assertTrue(result.reserve.extraStacks < 3)
            val fix = assertNotNull(result.reserve.shortfall).let { result.reserve.shortfall!! }
            assertEquals(12, fix.stacks)
            val fixed = inventory.withCount(fix.colour, inventory[fix.colour]!!.count + fix.more)
            assertTrue(ready(StackPlanRequest(fixed, 1_000, players = 9, smallBlind = 25, reserveStacks = 3)).reserve.enough)
        }

        @Test
        fun `chips smaller than the one that pays the small blind stay in the box`() {
            val inventory = set(
                chip(ChipColour.White, 1, 500),
                chip(ChipColour.Red, 5, 500),
                chip(ChipColour.Green, 25, 200),
                chip(ChipColour.Black, 100, 200),
            )
            val result = ready(StackPlanRequest(inventory, startingStack = 3_000, players = 6, smallBlind = 50))
            assertEquals(25, result.stack.chips.first().value, "25 is the largest chip that pays 50")
            assertTrue(result.stack.chips.none { it.value < 25 })
        }

        @Test
        fun `custom colours and values plan like any other`() {
            // A set whose whites are 25s and reds 100s
            val inventory = set(chip(ChipColour.White, 25, 200), chip(ChipColour.Red, 100, 200), chip(ChipColour.Blue, 500, 100))
            val result = ready(StackPlanRequest(inventory, startingStack = 4_000, players = 8, smallBlind = 25))
            assertEquals(ChipColour.White, result.stack.chips.first().colour)
            assertEquals(4_000L, result.stack.totalValue)
        }
    }

    @Nested
    inner class Unplannable {

        @Test
        fun `each problem no number of chips can fix is named`() {
            val home = ChipInventory.HOME_SET
            assertEquals(StackPlan.NoChips, plan(StackPlanRequest(ChipInventory.EMPTY, 5_000, 9, 25)))
            assertEquals(StackPlan.NoChips, plan(StackPlanRequest(home.withCount(ChipColour.Green, 0).let { s ->
                ChipColour.entries.fold(s) { acc, c -> acc.withCount(c, 0) }
            }, 5_000, 9, 25)))
            assertEquals(StackPlan.NoChipForSmallBlind(25), plan(StackPlanRequest(home.without(ChipColour.Green), 5_000, 9, 25)))
            assertEquals(StackPlan.NoChipForSmallBlind(30), plan(StackPlanRequest(home, 5_000, 9, 30)))
            val unreachable = plan(StackPlanRequest(home, 5_010, 9, 25))
            assertEquals(StackPlan.StackNotReachable(5_010, 25), unreachable)
            unreachable as StackPlan.StackNotReachable
            assertEquals(5_000 to 5_025, unreachable.nearestBelow to unreachable.nearestAbove)
            assertEquals(StackPlan.StackSmallerThanSmallestChip(20, 25), plan(StackPlanRequest(home, 20, 9, 25)))
            assertEquals(StackPlan.InvalidInput(StackPlan.Field.PLAYERS, 0), plan(StackPlanRequest(home, 5_000, 0, 25)))
            assertEquals(StackPlan.InvalidInput(StackPlan.Field.STARTING_STACK, 0), plan(StackPlanRequest(home, 0, 9, 25)))
            assertEquals(
                StackPlan.InvalidInput(StackPlan.Field.RESERVE, -1),
                plan(StackPlanRequest(home, 5_000, 9, 25, reserveStacks = -1))
            )
            assertEquals(StackPlan.InvalidInput(StackPlan.Field.SMALL_BLIND, 0), plan(StackPlanRequest(home, 5_000, 9, 0)))
            val ones = set(chip(ChipColour.White, 1, 9_999))
            assertEquals(StackPlan.StackTooBig(1_000_000, 1), plan(StackPlanRequest(ones, 1_000_000, 1, 1)))
        }
    }

    // ------------------------------------------------------------------ color-up

    @Nested
    inner class ColorUp {

        /** The clock's default ladder (3 h, 20-minute levels, 5,000 from 50s) plus its 3 overtime levels. */
        private val levels = listOf(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000, 10_000, 20_000, 40_000)
            .mapIndexed { i, sb -> BlindLevel(i + 1, sb, 2 * sb, 0, i * 20) }

        private fun schedule(vararg breaksAfter: Int) = BlindSchedule(
            levels = levels,
            regularLevelCount = 9,
            breaks = breaksAfter.mapIndexed { i, after -> ScheduledBreak(i + 1, after) },
            smallestChip = 50,
            startingChips = 5_000
        )

        private fun request(inventory: ChipInventory, players: Int, schedule: BlindSchedule, reserve: Int = 0) =
            StackPlanRequest(inventory, 5_000, players, smallBlind = 50, reserve, maxColours = 4, schedule = schedule)

        private val inventory = set(
            chip(ChipColour.Orange, 50, 200),
            chip(ChipColour.Black, 100, 200),
            chip(ChipColour.Purple, 500, 100),
            chip(ChipColour.Yellow, 1_000, 100),
        )

        @Test
        fun `breaks every 4 levels - 50s at break 1, then 100s and 500s at break 2`() {
            val result = ready(request(inventory, players = 9, schedule = schedule(4, 8)))
            val q = result.stack.chips.associate { it.value to it.count.toLong() }
            val plan = result.colorUp!!
            assertEquals(9, plan.stacksInPlay)

            // From level 4 (300/600) every amount is a multiple of 100; break 1 follows level 4
            // From level 7 (1,500/3,000) of 500, from level 8 (3,000/6,000) of 1,000: both at break 2
            val fifties = 9 * (q[50] ?: 0) * 50
            val step1 = ColorUpStep(
                chips = listOf(ChipRef(ChipColour.Orange, 50)),
                into = ChipRef(ChipColour.Black, 100),
                fromLevel = 4,
                breakNumber = 1,
                afterLevel = 4,
                needed = ((fifties + 99) / 100).toInt(),
                spare = (200 - 9 * (q[100] ?: 0) - (fifties + 99) / 100).toInt()
            )
            val bigValue = fifties + 9 * (q[100] ?: 0) * 100 + 9 * (q[500] ?: 0) * 500
            val step2 = ColorUpStep(
                // The purples only if the stack has some: a chip nobody holds needs no color-up
                chips = listOf(ChipRef(ChipColour.Black, 100), ChipRef(ChipColour.Purple, 500))
                    .filter { it.value == 100 || q[500] != null },
                into = ChipRef(ChipColour.Yellow, 1_000),
                fromLevel = 7,
                breakNumber = 2,
                afterLevel = 8,
                needed = ((bigValue + 999) / 1_000).toInt(),
                spare = (100 - 9 * (q[1_000] ?: 0) - (bigValue + 999) / 1_000).toInt()
            )
            assertEquals(listOf(step1, step2), plan.steps)
        }

        @Test
        fun `the plan places color-ups on the same breaks as the clock would`() {
            // ColorUpPlanner.plan (the clock's) and planFor agree on the chips on the 50 -> 100 -> 500 -> 1,000 path
            val clock = ColorUpPlanner.plan(levels, 50)
            val chipSet = ColorUpPlanner.planFor(levels, listOf(50, 100, 500, 1_000, 5_000))
            assertEquals(clock, chipSet)
            assertEquals(mapOf(3 to listOf(50), 6 to listOf(100), 7 to listOf(500), 8 to listOf(1_000)), chipSet)
        }

        @Test
        fun `with no breaks, chips color up at the start of the level that stops needing them`() {
            val result = ready(request(inventory, players = 9, schedule = schedule()))
            val steps = result.colorUp!!.steps
            assertEquals(listOf(4, 7, 8), steps.map { it.fromLevel })
            assertTrue(steps.all { it.breakNumber == null && it.afterLevel == null })
            // Level by level, each chip goes into the next one still in play: 50s into 100s, 100s
            // into 500s, and a level later the 500s into 1,000s
            assertEquals(listOf(100, 500, 1_000), steps.map { it.into.value })
            assertEquals(listOf(listOf(50), listOf(100), listOf(500)), steps.map { s -> s.chips.map { it.value } })
        }

        @Test
        fun `a chip off the smallest chip's path still colors up`() {
            // 250s divide neither 100 nor... they go when every amount is a multiple of 500
            val withPinks = inventory.withChip(chip(ChipColour.Pink, 250, 100))!!
            val drops = ColorUpPlanner.planFor(levels, withPinks.chips.map { it.value })
            assertEquals(mapOf(3 to listOf(50), 6 to listOf(100, 250), 7 to listOf(500)), drops)
        }

        @Test
        fun `too few big chips in the box shows a negative spare`() {
            val few = inventory.withCount(ChipColour.Black, 120)
            val result = ready(request(few, players = 9, schedule = schedule(4, 8)))
            val first = result.colorUp!!.steps.first()
            assertEquals(100, first.into.value)
            assertEquals(120 - 9 * result.stack.countOf(100) - first.needed, first.spare)
        }

        @Test
        fun `the plan counts the reserve's stacks as in play`() {
            val result = ready(request(inventory, players = 8, schedule = schedule(4, 8), reserve = 2))
            assertEquals(10, result.colorUp!!.stacksInPlay)
        }
    }

    // ------------------------------------------------------------------ properties

    @Nested
    inner class Properties {

        private val values = listOf(5, 10, 20, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000, 30, 75, 300)
        private val stacks = listOf(500, 1_000, 1_500, 2_000, 2_500, 3_000, 4_000, 5_000, 7_500, 10_000, 15_000, 20_000) +
            listOf(1_234, 5_010) // unreachable from most sets
        private val blinds = listOf(5, 10, 25, 50, 100)

        private fun randomRequest(rnd: Random): StackPlanRequest {
            val n = rnd.nextInt(1, 7)
            val colours = ChipColour.entries.shuffled(rnd).take(n)
            val chosen = values.shuffled(rnd).take(n)
            val inventory = ChipInventory.of(colours.zip(chosen) { c, v -> InventoryChip(c, v, rnd.nextInt(0, 401)) })
            return StackPlanRequest(
                inventory = inventory,
                startingStack = stacks.random(rnd),
                players = rnd.nextInt(1, 13),
                smallBlind = blinds.random(rnd),
                reserveStacks = rnd.nextInt(0, 6),
                maxColours = rnd.nextInt(2, 7),
                curve = ChipDistributionCurve.getAllCurves().random(rnd)
            )
        }

        @Test
        fun `random sets - stacks are exact and within the caps, shortfalls are real, minimal and cheapest`() {
            val rnd = Random(2026_10_05)
            val seen = mutableMapOf<String, Int>()
            repeat(1_500) { i ->
                val request = randomRequest(rnd)
                val outcome = plan(request)
                seen.merge(outcome::class.simpleName!!, 1, Int::plus)
                val where = "case $i: $request"
                when (outcome) {
                    is StackPlan.Ready -> checkReady(request, outcome, where)
                    is StackPlan.Short -> checkShort(request, outcome, where)
                    is StackPlan.Unplannable -> checkUnplannable(request, outcome, where)
                }
            }
            // The generator reaches every kind of answer
            listOf("Ready", "Short", "NoChipForSmallBlind", "StackNotReachable").forEach {
                assertTrue((seen[it] ?: 0) >= 20, "only ${seen[it]} $it answers: $seen")
            }
        }

        @Test
        fun `the bitset feasibility and the capped optimizer agree with brute force`() {
            val rnd = Random(77)
            repeat(3_000) { i ->
                val n = rnd.nextInt(1, 6)
                val chips = values.filter { it <= 1_000 }.shuffled(rnd).take(n).sorted().toIntArray()
                val caps = IntArray(n) { rnd.nextInt(0, 25) }
                val target = rnd.nextInt(1, 200) * 25
                val expected = Oracle.canMake(target, chips, caps)
                val where = "case $i: $target from ${chips.toList()} capped ${caps.toList()}"
                assertEquals(expected, ChipDistributionOptimizer.canMakeWithinCaps(target, chips, caps), where)
                val found = ChipDistributionOptimizer.optimizeWithinCaps(
                    target, chips, caps, rnd.nextInt(1, 6), ChipDistributionCurve.getAllCurves().random(rnd)
                )
                assertEquals(expected, found != null, where)
                if (found != null) {
                    val d = found.distribution
                    assertEquals(target.toLong(), d.denominations.zip(d.quantities).sumOf { (v, q) -> v.toLong() * q }, where)
                    assertEquals(chips[0], d.denominations.first(), where)
                    d.denominations.zip(d.quantities).forEach { (v, q) ->
                        assertTrue(q in 1..caps[chips.indexOf(v)], "$where: $q × $v")
                    }
                }
            }
        }

        private fun candidates(request: StackPlanRequest): Pair<List<InventoryChip>, Int>? {
            val owned = request.inventory.owned
            val smallest = owned.filter { request.smallBlind % it.value == 0 }.maxByOrNull { it.value } ?: return null
            val list = owned.filter { it.value == smallest.value || it.value in smallest.value + 1..request.startingStack / 3 }
            return list to smallest.value
        }

        @Suppress("ReturnCount") // no stacks are always possible; no candidates never
        private fun oracle(request: StackPlanRequest, stacks: Int, extra: Pair<ChipColour, Int>? = null): Boolean {
            if (stacks <= 0) return true
            val (list, _) = candidates(request) ?: return false
            val chips = list.map { it.value }.toIntArray()
            val caps = IntArray(list.size) { i ->
                val more = if (extra?.first == list[i].colour) extra.second else 0
                ((list[i].count.toLong() + more) / stacks).toInt()
            }
            return Oracle.canMake(request.startingStack, chips, caps)
        }

        private fun checkStack(
            request: StackPlanRequest,
            stack: PlayerStack,
            stacks: Int,
            inventory: ChipInventory,
            where: String,
        ) {
            val (list, smallest) = candidates(request)!!
            assertEquals(request.startingStack.toLong(), stack.totalValue, "$where: adds up")
            assertEquals(smallest, stack.chips.first().value, "$where: smallest chip pays the small blind")
            stack.chips.forEach { c ->
                assertTrue(c.count >= 1, where)
                assertTrue(list.any { it.value == c.value && it.colour == c.colour }, "$where: $c is a candidate")
                val owned = inventory.chipWorth(c.value)!!.count.toLong()
                assertTrue(stacks.toLong() * c.count <= owned, "$where: $stacks × $c within $owned")
            }
            if (!stack.moreColoursThanAsked) assertTrue(stack.chips.size <= request.maxColours, "$where: colours")
        }

        private fun checkReady(request: StackPlanRequest, outcome: StackPlan.Ready, where: String) {
            val reserve = outcome.reserve
            checkStack(request, outcome.stack, request.players, request.inventory, where)
            val left = outcome.stack.chips.map { c ->
                (request.inventory.chipWorth(c.value)!!.count - request.players * c.count) / c.count
            }
            assertEquals(left.min(), reserve.extraStacks, "$where: extra stacks")
            assertEquals(request.reserveStacks, reserve.requested, where)
            if (reserve.enough) {
                checkStack(request, outcome.stack, request.players + request.reserveStacks, request.inventory, where)
            } else {
                // Short of the reserve only when no stack at all leaves it
                val wanted = request.players + request.reserveStacks
                assertFalse(oracle(request, wanted), "$where: a stack leaving the reserve exists")
                reserve.shortfall?.let { fix ->
                    assertTrue(oracle(request, fix.stacks, fix.colour to fix.more), "$where: the reserve fix works")
                }
            }
        }

        private fun checkShort(request: StackPlanRequest, outcome: StackPlan.Short, where: String) {
            val players = request.players
            assertFalse(oracle(request, players), "$where: short, yet a stack exists")
            val n = outcome.stacksYouCanMake
            assertTrue(n in 0 until players && oracle(request, n) && !oracle(request, n + 1), "$where: $n stacks")

            val (list, _) = candidates(request)!!
            // The cheapest single-colour fix, found independently: fewest chips, then the smaller chip
            val cheapest = list.mapNotNull { c ->
                val most = request.startingStack / c.value
                val current = c.count / players
                if (current >= most || !oracle(request, players, c.colour to (most * players - c.count))) return@mapNotNull null
                var low = current + 1
                var high = most
                while (low < high) {
                    val mid = (low + high) / 2
                    if (oracle(request, players, c.colour to (mid * players - c.count))) high = mid else low = mid + 1
                }
                ChipShortfall(c.colour, c.value, low * players - c.count, players)
            }.minWithOrNull(compareBy<ChipShortfall> { it.more }.thenBy { it.value })
            assertEquals(cheapest, outcome.shortfall, "$where: the cheapest fix")

            val fix = outcome.shortfall ?: return
            val fixed = request.inventory.withCount(fix.colour, request.inventory[fix.colour]!!.count + fix.more)
            if (fixed[fix.colour]!!.count == request.inventory[fix.colour]!!.count + fix.more) {
                checkStack(request, outcome.stackIfAdded!!, players, fixed, where)
            }
        }

        private fun checkUnplannable(request: StackPlanRequest, outcome: StackPlan.Unplannable, where: String) {
            val owned = request.inventory.owned
            when (outcome) {
                StackPlan.NoChips -> assertTrue(owned.isEmpty(), where)
                is StackPlan.NoChipForSmallBlind -> assertTrue(owned.none { request.smallBlind % it.value == 0 }, where)
                is StackPlan.StackSmallerThanSmallestChip -> assertTrue(request.startingStack < outcome.smallestChip, where)
                is StackPlan.StackNotReachable -> {
                    val (list, _) = candidates(request)!!
                    val unit = list.fold(0) { acc, c -> gcd(acc, c.value) }
                    assertEquals(unit, outcome.unit, where)
                    assertTrue(request.startingStack % unit != 0, where)
                    assertFalse(oracle(request, 1), where)
                }
                else -> fail<Unit>("$where: unexpected $outcome")
            }
        }

        private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }

    /**
     * Brute force, independent of the optimizer's bitsets: a plain bounded-coin DP over every
     * amount (at least one of the first chip, up to its cap of each).
     */
    private object Oracle {
        fun canMake(target: Int, chips: IntArray, caps: IntArray): Boolean {
            if (chips.isEmpty() || caps[0] < 1 || chips[0] > target) return false
            // The one chip of the first kind every stack has, then up to the rest of each cap
            var reach = BooleanArray(target + 1).also { it[chips[0]] = true }
            chips.forEachIndexed { i, value -> reach = addCoins(reach, value, if (i == 0) caps[0] - 1 else caps[i]) }
            return reach[target]
        }

        /** Every amount [reach] makes, plus 0 to [count] coins of [value]: the classic bounded-coin pass. */
        private fun addCoins(reach: BooleanArray, value: Int, count: Int): BooleanArray {
            val next = BooleanArray(reach.size)
            val used = IntArray(reach.size) // fewest coins of [value] that reach each amount
            for (amount in reach.indices) {
                if (reach[amount]) {
                    next[amount] = true
                } else if (amount >= value && next[amount - value] && used[amount - value] < count) {
                    next[amount] = true
                    used[amount] = used[amount - value] + 1
                }
            }
            return next
        }
    }
}
