package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblemKind
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * The blind engine as properties over every setup the Tournament tab lets a host type: 1 to 24 hours,
 * any level length, any chip on the picker, any starting stack up to 999,999,999 (the field's limit).
 * BlindStructureCalculatorTest sweeps a grid of realistic setups; this samples the whole space,
 * odd values included, where a grid has holes.
 *
 * - The advisor ([BlindSetupAdvisor]) and the calculator ([BlindStructureCalculator]) agree on which
 *   setups can be played.
 * - Every ladder built keeps every rule in CLAUDE.md: the first small blind is the smallest chip, the
 *   last regular one is the starting stack, every level is a multiple of the smallest chip, every step
 *   grows 1.3x to 2.0x; and overtime keeps doubling.
 * - Every refused setup says why, and every fix it offers works.
 */
class BlindPropertiesTest {

    /** A blind setup as the host types it, and the level the big-blind ante starts from (0: none). */
    private data class Setup(val hours: Int, val roundMinutes: Int, val chip: Int, val stack: Int, val anteFrom: Int) {
        val minutes: Int get() = hours * MINUTES_PER_HOUR
        val input: BlindStructureInput get() = BlindStructureInput(PLAYERS, minutes, chip, stack, roundMinutes, anteFrom)
        fun check() = BlindSetupAdvisor.check(minutes, roundMinutes, chip, stack)
        fun with(fix: BlindSetupFix): Setup = when (fix) {
            is BlindSetupFix.UseRoundLength -> copy(roundMinutes = fix.minutes)
            is BlindSetupFix.UseStartingChips -> copy(stack = fix.chips)
        }
    }

    @Test
    fun `the advisor accepts exactly the setups the calculator can build`() =
        forAll(seed = 2026_1008_21L, iterations = 2_000, gen = setups) { setup ->
            val problem = setup.check()
            val built = runCatching { BlindStructureCalculator.generateSchedule(setup.input) }
            if (problem == null) {
                val levels = built.getOrElse { error -> throw AssertionError("$setup: accepted, but not built: $error") }
                expect(levels.size == setup.minutes / setup.roundMinutes) { "$setup: ${levels.size} levels" }
            } else if (problem.kind !in ROUNDS_ONLY) {
                // Uneven rounds and too few rounds are the advisor's alone: the calculator rounds those down or up.
                expect(built.isFailure) { "$setup: refused (${problem.kind}) but the calculator builds ${built.getOrNull()}" }
            }
        }

    @Test
    fun `every ladder built keeps every rule, and overtime keeps doubling`() =
        forAll(seed = 2026_1008_22L, iterations = 2_000, gen = playable) { setup ->
            val levels = BlindStructureCalculator.generateSchedule(setup.input)
            val smallBlinds = levels.map { it.smallBlind }
            expect(smallBlinds.first() == setup.chip) { "$setup starts at ${smallBlinds.first()}" }
            expect(smallBlinds.last() == setup.stack) { "$setup ends at ${smallBlinds.last()}" }
            expect(smallBlinds.all { it % setup.chip == 0 }) { "$setup: not all multiples of ${setup.chip}: $smallBlinds" }
            smallBlinds.zipWithNext().forEach { (from, to) ->
                // 1.3x <= to / from <= 2.0x, in exact integers
                expect(to.toLong() * GROWTH_TENTHS_DENOMINATOR >= from.toLong() * MIN_GROWTH_TENTHS && to.toLong() <= 2L * from) {
                    "$setup: $from -> $to is outside 1.3x-2.0x in $smallBlinds"
                }
            }
            levels.forEachIndexed { index, level -> checkLevel(setup, level, index + 1) }

            // Overtime: up to three more levels, each doubling the one before, as the clock adds them
            val withOvertime = levels.toMutableList()
            repeat(BlindStructureCalculator.MAX_OVERTIME_LEVELS) {
                val next = BlindStructureCalculator.generateNextOvertimeLevel(withOvertime, setup.roundMinutes, setup.anteFrom)
                    ?: return@repeat
                val before = withOvertime.last().smallBlind
                expect(next.smallBlind.toLong() == 2L * before) { "$setup: overtime $before -> ${next.smallBlind}" }
                checkLevel(setup, next, withOvertime.size + 1)
                withOvertime += next
            }
        }

    @Test
    fun `every refused setup says why, and every fix it offers works`() =
        forAll(seed = 2026_1008_23L, iterations = 2_000, gen = setups) { setup ->
            val problem = setup.check() ?: return@forAll
            expect(problem.explanation.isNotBlank()) { "$setup: refused (${problem.kind}) without a reason" }
            problem.fixes.forEach { fix ->
                val fixed = setup.with(fix)
                expect(fixed != setup) { "$setup: the fix '${fix.label}' changes nothing" }
                val after = fixed.check()
                expect(after == null) { "$setup: the fix '${fix.label}' still fails: ${after?.explanation}" }
            }
        }

    private fun checkLevel(setup: Setup, level: BlindLevel, number: Int) {
        expect(level.level == number) { "$setup: level $number is numbered ${level.level}" }
        expect(level.smallBlind > 0 && level.bigBlind.toLong() == 2L * level.smallBlind) {
            "$setup: level $number has blinds ${level.smallBlind}/${level.bigBlind}"
        }
        val anteExpected = if (setup.anteFrom in 1..number) level.bigBlind else 0
        expect(level.ante == anteExpected) { "$setup: level $number has ante ${level.ante}, expected $anteExpected" }
        val start = (number - 1) * setup.roundMinutes
        expect(level.roundStartMinute == start) { "$setup: level $number at ${level.roundStartMinute}, not $start" }
    }

    private companion object {
        const val PLAYERS = 9
        const val MINUTES_PER_HOUR = 60
        const val MIN_GROWTH_TENTHS = 13L
        const val GROWTH_TENTHS_DENOMINATOR = 10L
        val ROUNDS_ONLY = setOf(BlindSetupProblemKind.UNEVEN_ROUNDS, BlindSetupProblemKind.TOO_FEW_ROUNDS)

        private val chips = Arb.element(SmallestChipChoices.values)

        /** A level length: usually a multiple of 5 minutes, sometimes anything the field takes. */
        private val roundLengths = Arb.bind(Arb.int(1..24), Arb.int(1..999), Arb.int(0..3)) { fives, any, which ->
            if (which == 0) any else fives * 5
        }

        /** A stack: a round number, a multiple of the chip, or anything up to the field's 999,999,999. */
        private val stacks = Arb.bind(
            Arb.element(listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 80)),
            Arb.int(0..7),
            Arb.int(1..999_999_999),
            Arb.int(0..2),
        ) { mantissa, power, any, which ->
            if (which == 0) any else (mantissa * 10.0.pow(power)).toInt().coerceAtMost(999_999_999)
        }

        val setups = Arb.bind(Arb.int(1..24), roundLengths, chips, stacks, Arb.int(0..12), ::Setup)

        /** Setups the advisor accepts: a random one moved onto a valid round length and stack where it offers them. */
        val playable = setups.map { setup ->
            generateSequence(setup) { current -> current.check()?.fixes?.firstOrNull()?.let(current::with) }
                .take(3)
                .firstOrNull { it.check() == null }
                ?: Setup(hours = 3, roundMinutes = 20, chip = setup.chip, stack = setup.chip * 100, anteFrom = setup.anteFrom)
        }
    }
}
