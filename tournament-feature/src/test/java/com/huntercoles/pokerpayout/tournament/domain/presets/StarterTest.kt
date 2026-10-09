package com.huntercoles.pokerpayout.tournament.domain.presets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.utils.BlindFittingAlgorithm
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The starter nights (PP-113) keep the ladder rules for every smallest chip the setup offers: the
 * advisor finds nothing wrong, and the schedule the clock builds climbs from the chip to the stack in
 * the documented 1.3x to 2.0x steps, one level per level length, with antes and breaks where the
 * starter puts them. Copied into the host's presets, each saves and reads back exactly. Robolectric,
 * for the names and the platform's org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StarterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Every starter at every smallest chip, as tonight's table might play it. */
    private val everyNight: List<Pair<Starter, PresetSetup>> = Starter.entries.flatMap { starter ->
        SmallestChipChoices.values.map { chip -> starter to starter.setupFor(chip, FOOD, PLAYERS) }
    }

    @Test
    fun `every starter is a valid blind setup with every smallest chip`() {
        everyNight.forEach { (starter, setup) ->
            val blinds = setup.blinds
            val problem = with(blinds) {
                BlindSetupAdvisor.check(durationMinutes, roundLengthMinutes, smallestChip, startingChips)
            }
            assertNull("$starter at ${blinds.smallestChip}: ${problem?.explanation}", problem)
            assertTrue(
                "$starter at ${blinds.smallestChip}",
                BlindFittingAlgorithm.isFeasible(starter.levels, blinds.smallestChip, blinds.startingChips),
            )
        }
    }

    @Test
    fun `every starter's ladder keeps the rules, level by level`() {
        everyNight.forEach { (starter, setup) ->
            val blinds = setup.blinds
            val where = "$starter at ${blinds.smallestChip}"
            val levels = schedule(setup)
            assertEquals(where, starter.levels, levels.size)
            assertEquals("$where: the first small blind is the smallest chip", blinds.smallestChip, levels.first().smallBlind)
            assertEquals("$where: the last regular small blind is the stack", blinds.startingChips, levels.last().smallBlind)
            assertTrue("$where: every blind is made of smallest chips", levels.all { it.smallBlind % blinds.smallestChip == 0 })
            levels.zipWithNext().forEach { (a, b) ->
                val growth = b.smallBlind.toDouble() / a.smallBlind
                assertTrue(
                    "$where: L${a.level} ${a.smallBlind} to L${b.level} ${b.smallBlind} grows $growth",
                    growth in BlindStructureConstants.MIN_BLIND_GROWTH_RATE..BlindStructureConstants.MAX_BLIND_GROWTH_RATE,
                )
            }
            val antes = levels.filter { it.ante > 0 }.map { it.level }
            val ante = starter.night.anteFromLevel
            val expected = if (ante > 0) (ante..starter.levels).toList() else emptyList()
            assertEquals("$where: antes", expected, antes)
        }
    }

    @Test
    fun `each starter is the night its name says`() {
        val at50 = Starter.entries.associateWith { it.setupFor(TournamentDefaults.SMALLEST_CHIP, FOOD, PLAYERS) }
        assertEquals("Turbo: two hours of 10-minute levels", listOf(120, 10, 12), at50.shape(Starter.TURBO))
        assertEquals("Classic: the app's own 3 hours of 20-minute levels", listOf(180, 20, 9), at50.shape(Starter.CLASSIC))
        assertEquals("Deep stack: four hours", listOf(240, 20, 12), at50.shape(Starter.DEEP_STACK))
        assertEquals(listOf(180, 20, 9), at50.shape(Starter.BOUNTY_NIGHT))

        val classic = requireNotNull(at50[Starter.CLASSIC])
        assertEquals("the default stack over the default chip", TournamentDefaults.STARTING_CHIPS, classic.blinds.startingChips)
        val deep = requireNotNull(at50[Starter.DEEP_STACK])
        assertEquals("Deep stack plays twice the stack", 2 * classic.blinds.startingChips, deep.blinds.startingChips)
        val bounties = at50.filterValues { it.money.bountyCents > 0L }.keys.toList()
        assertEquals("only Bounty night has a bounty", listOf(Starter.BOUNTY_NIGHT), bounties)
        assertEquals("two breaks in Classic", 2, breaks(classic))
        at50.values.forEach { setup ->
            assertEquals("the host's food stays as it is", FOOD, setup.money.foodCents)
            assertEquals(0L, setup.money.rebuyCents)
            assertEquals(0L, setup.money.addOnCents)
        }
    }

    @Test
    fun `payouts are the standard ones for tonight's players, following the count`() {
        listOf(3, PLAYERS, 30).forEach { players ->
            val payouts = Starter.CLASSIC.setupFor(TournamentDefaults.SMALLEST_CHIP, FOOD, players).payouts
            assertEquals(PayoutPreset.STANDARD, payouts.preset)
            assertEquals(PayoutPreset.STANDARD.weightsFor(PayoutPlaces.recommended(players)), payouts.weights)
            assertTrue("follows the player count", payouts.followsPlayers)
        }
    }

    @Test
    fun `a copy saves and reads back exactly`() {
        everyNight.forEach { (starter, setup) ->
            val copy = TournamentPreset(id = 1L, name = context.getString(starter.title), lastUsedMillis = 0L, setup = setup)
            assertEquals("$starter at ${setup.blinds.smallestChip}", copy, PresetCodec.decode(PresetCodec.encode(copy)))
        }
    }

    @Test
    fun `each starter has a name of its own`() {
        val names = Starter.entries.map { context.getString(it.title) }
        assertEquals(listOf("Turbo", "Classic", "Deep stack", "Bounty night"), names)
    }

    private fun schedule(setup: PresetSetup) = BlindStructureCalculator.generateSchedule(
        BlindStructureInput(
            players = PLAYERS,
            targetDurationMinutes = setup.blinds.durationMinutes,
            smallestChip = setup.blinds.smallestChip,
            startingStack = setup.blinds.startingChips,
            roundLengthMinutes = setup.blinds.roundLengthMinutes,
            bigBlindAnteFromLevel = setup.blinds.anteFromLevel,
        ),
    )

    private fun breaks(setup: PresetSetup): Int = ClockTimeline.build(
        regularLevels = schedule(setup),
        roundLengthMinutes = setup.blinds.roundLengthMinutes,
        breaks = BreakSettings(setup.blinds.breakEveryLevels, setup.blinds.breakLengthMinutes),
    ).segments.count { it is BreakSegment }

    /** Duration, level length and levels. */
    private fun Map<Starter, PresetSetup>.shape(starter: Starter): List<Int> {
        val blinds = requireNotNull(this[starter]).blinds
        return listOf(blinds.durationMinutes, blinds.roundLengthMinutes, blinds.durationMinutes / blinds.roundLengthMinutes)
    }

    private companion object {
        const val FOOD = 500L
        const val PLAYERS = 9
    }
}
