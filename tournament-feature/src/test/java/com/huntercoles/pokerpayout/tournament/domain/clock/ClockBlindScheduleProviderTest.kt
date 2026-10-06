package com.huntercoles.pokerpayout.tournament.domain.clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.core.utils.PlanStacksUseCase
import com.huntercoles.pokerpayout.core.utils.ScheduledBreak
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.core.utils.StackPlanRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The clock's schedule as the chip set reads it (PP-033): the same levels and breaks the clock
 * lays out, from the same saved setup, and color-ups on the same breaks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockBlindScheduleProviderTest {

    private lateinit var timer: TimerPreferences
    private lateinit var tournament: TournamentPreferences
    private lateinit var provider: ClockBlindScheduleProvider

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("timer_prefs", "tournament_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        timer = TimerPreferences(context)
        tournament = TournamentPreferences(context)
        provider = ClockBlindScheduleProvider(timer, tournament)
    }

    @Test
    fun `the default setup with breaks every 4 levels`() {
        timer.setBreakEveryLevels(4)
        val schedule = assertNotNull(provider.currentSchedule()).let { provider.currentSchedule()!! }

        // 3 h of 20-minute levels from 50s to 5,000, then the clock's 3 doubling overtime levels
        assertEquals(
            listOf(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000, 10_000, 20_000, 40_000),
            schedule.levels.map { it.smallBlind }
        )
        assertEquals(9, schedule.regularLevelCount)
        assertEquals(listOf(ScheduledBreak(1, 4), ScheduledBreak(2, 8)), schedule.breaks)
        assertEquals(50, schedule.smallestChip)
        assertEquals(5_000, schedule.startingChips)
    }

    @Test
    fun `it matches the clock's own timeline`() {
        timer.setBreakEveryLevels(3)
        timer.setBigBlindAnteFromLevel(5)
        tournament.setSmallestChip(25)
        tournament.setStartingChips(4_000)
        val levels = BlindStructureCalculator.generateSchedule(
            BlindStructureInput(
                players = 5,
                targetDurationMinutes = 180,
                smallestChip = 25,
                startingStack = 4_000,
                roundLengthMinutes = 20,
                bigBlindAnteFromLevel = 5
            )
        )
        val timeline = ClockTimeline.build(levels, 20, BreakSettings(everyLevels = 3), 25, bigBlindAnteFromLevel = 5)

        val schedule = provider.currentSchedule()!!
        assertEquals(timeline.levels.map { it.level }, schedule.levels)
        assertEquals(
            timeline.segments.filterIsInstance<BreakSegment>().map { ScheduledBreak(it.number, it.afterLevel) },
            schedule.breaks
        )
        assertTrue("antes from level 5", schedule.levels.drop(4).all { it.ante == it.bigBlind })
    }

    @Test
    fun `a started clock keeps the setup it started with`() {
        tournament.setStartingChips(10_000)
        timer.setHasTimerStarted(true)
        timer.setSmallestChipAtStart(50)
        timer.setStartingChipsAtStart(5_000)
        timer.setRoundLengthAtStart(20)

        val schedule = provider.currentSchedule()!!
        assertEquals(5_000, schedule.startingChips)
        assertEquals(5_000, schedule.levels[schedule.regularLevelCount - 1].smallBlind)
    }

    @Test
    fun `a setup the clock can't run gives no schedule`() {
        tournament.setRoundLengthMinutes(25) // 180 isn't a whole number of 25-minute levels
        assertNull(provider.currentSchedule())
    }

    @Test
    fun `the chip set colors up on the same breaks as the clock`() {
        timer.setBreakEveryLevels(4)
        val schedule = provider.currentSchedule()!!
        val timeline = ClockTimeline.build(
            BlindStructureCalculator.generateSchedule(BlindStructureInput(5, 180, 50, 5_000, 20)),
            20,
            BreakSettings(everyLevels = 4),
            smallestChip = 50
        )
        // A set of the chips the clock colors up through: 50, 100, 500, 1,000, 5,000
        val inventory = ChipInventory.of(
            listOf(
                InventoryChip(ChipColour.Orange, 50, 300),
                InventoryChip(ChipColour.Black, 100, 300),
                InventoryChip(ChipColour.Purple, 500, 200),
                InventoryChip(ChipColour.Yellow, 1_000, 100),
                InventoryChip(ChipColour.Brown, 5_000, 50),
            )
        )
        val plan = PlanStacksUseCase()(
            StackPlanRequest(inventory, startingStack = 5_000, players = 5, smallBlind = 50, maxColours = 4, schedule = schedule)
        )
        assertTrue("$plan", plan is StackPlan.Ready)
        val steps = (plan as StackPlan.Ready).colorUp!!.steps

        val clockBreaks = timeline.segments.filterIsInstance<BreakSegment>().filter { it.colorUp.isNotEmpty() }
            .associate { it.number to it.colorUp.sorted() }
        val chipSetBreaks = steps.groupBy { it.breakNumber!! }
            .mapValues { (_, s) -> s.flatMap { step -> step.chips.map { it.value } }.sorted() }
        assertEquals(mapOf(1 to listOf(50), 2 to listOf(100, 500, 1_000)), clockBreaks)
        assertEquals(clockBreaks, chipSetBreaks)
    }
}
