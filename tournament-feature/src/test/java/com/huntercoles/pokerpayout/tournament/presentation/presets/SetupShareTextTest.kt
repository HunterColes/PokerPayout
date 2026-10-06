package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.composable.TournamentFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.util.Locale

/**
 * The setup shared as text (PP-032), for the mockups' night ([TournamentFixture]): the money, every
 * planned level and break in order with the antes, color-ups and the break note, then the payouts in
 * the Payouts tab's own share lines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupShareTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() = store.clear()

    private val numbers = NumberFormat.getIntegerInstance(Locale.US)

    @Test
    fun `the night's money, blinds and payouts, as the group chat reads them`() {
        val setup = fixture.setupState()
        val timer = fixture.ready
        val lines = SetupShareText.build(context, setup, timer).lines()

        assertEquals("Poker night setup", lines[0])
        val money = "$40 buy-in · $5 bounty · $40 rebuy to the end of Level 4 · $10 add-on · $5 food"
        assertEquals("Money per player: $money", lines[1])
        assertEquals("", lines[2])
        assertEquals("Blinds: 3 h · 20-min levels · 5,000 chips · smallest chip 25 · breaks every 4 · ante from L5", lines[3])

        // Every planned level and break, in the clock's order
        val planned = timer.timeline.segments.filter { it !is LevelSegment || !it.isOvertime }
        val schedule = lines.drop(4).take(planned.size)
        planned.zip(schedule).forEach { (segment, line) ->
            when (segment) {
                is LevelSegment -> {
                    val level = segment.level
                    val blinds = "${numbers.format(level.smallBlind)} / ${numbers.format(level.bigBlind)}"
                    assertTrue(line, line.startsWith("L${level.level}: $blinds"))
                    assertEquals(line, level.ante > 0, line.contains("ante ${numbers.format(level.ante)}"))
                    assertEquals(line, segment.colorUp.isNotEmpty(), line.contains("Color up the "))
                }
                is BreakSegment -> {
                    assertTrue(line, line.startsWith("Break · 10 min"))
                    assertTrue(line, line.endsWith("Last orders at the bar"))
                    assertEquals(line, segment.colorUp.isNotEmpty(), line.contains("Color up the "))
                }
            }
        }
        assertEquals("L1: 25 / 50", schedule.first())
        assertEquals(9, schedule.count { it.startsWith("L") })
        assertEquals(2, schedule.count { it.startsWith("Break") })
        assertTrue("the ante starts at level 5", schedule.single { it.startsWith("L5:") }.contains("ante "))
        assertFalse(schedule.single { it.startsWith("L4:") }.contains("ante "))
        assertEquals("3:20 in all, then the blinds double each level", lines[4 + planned.size])

        // The payouts: the Payouts tab's lines, one per place paid, adding up to the pool
        val payouts = lines.drop(4 + planned.size + 2)
        assertEquals("Payouts for 9 players: Standard, rounded to $1", payouts[0])
        assertEquals("Prize pool $430 (9 buy-ins $360 · 1 rebuy $40 · 3 add-ons $30)", payouts[1])
        val places = setup.payoutTable.places
        assertEquals(places.size, payouts.size - 2)
        val ordinals = listOf("1st", "2nd", "3rd")
        places.forEachIndexed { index, place ->
            assertEquals("${ordinals[index]}: ${FormatUtils.formatMoney(place.amountCents)}", payouts[2 + index])
        }
        assertEquals(43_000L, places.sumOf { it.amountCents })
    }

    @Test
    fun `money and blinds leave out what the night doesn't have`() {
        val base = fixture.setupState()
        val setup = base.copy(config = base.config.copy(money = MoneySettings(2_000L, 0L, 0L, 0L, 0L)))
        val ready = fixture.ready
        val timer = ready.copy(
            config = ready.config.copy(breaks = ready.config.breaks.copy(everyLevels = 0), bigBlindAnteFromLevel = 0),
            rebuyUntilLevel = 0,
        )
        val lines = SetupShareText.build(context, setup, timer).lines()

        assertEquals("Money per player: $20 buy-in", lines[1])
        assertEquals("Blinds: 3 h · 20-min levels · 5,000 chips · smallest chip 25", lines[3])
    }

    @Test
    fun `blinds that can't be built share no schedule`() {
        val ready = fixture.ready
        val broken = ready.copy(timeline = ClockTimeline.EMPTY, baseBlindLevels = emptyList())
        val text = SetupShareText.build(context, fixture.setupState(), broken)

        assertFalse(text, text.lines().any { it.startsWith("L1:") || it.startsWith("Break") || it.contains(" in all") })
        assertTrue(text, text.contains("Payouts for 9 players"))
    }
}
