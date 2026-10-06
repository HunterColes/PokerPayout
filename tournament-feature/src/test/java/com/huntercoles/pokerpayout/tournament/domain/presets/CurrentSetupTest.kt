package com.huntercoles.pokerpayout.tournament.domain.presets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A preset against the real saved settings (PP-032): saving and loading back restores every field
 * exactly; loading never touches the players, the Bank or the clock, is refused once the clock has
 * started, follows tonight's player count, and keeps a rebuy amount the Bank has rebuys at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var tournament: TournamentPreferences
    private lateinit var timer: TimerPreferences
    private lateinit var bank: BankPreferences
    private lateinit var chips: ChipCalculatorPreferences
    private lateinit var current: CurrentSetup

    private val ownSet = ChipSetSettings(
        inventory = ChipInventory.of(
            listOf(
                InventoryChip(ChipColour.White, 25, 200),
                InventoryChip(ChipColour.Red, 100, 200),
                InventoryChip(ChipColour.Green, 500, 100),
            )
        ),
        inventoryReviewed = true,
        stackOverride = 8_000,
        shape = ChipDistributionCurve.BellCurve,
        maxColours = 4,
        reserveOverride = 3,
    )

    @Before
    fun setUp() {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "chip_calculator_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournament = TournamentPreferences(context)
        timer = TimerPreferences(context)
        bank = BankPreferences(context)
        chips = ChipCalculatorPreferences(context, tournament)
        current = CurrentSetup(tournament, timer, chips, bank)
        tournament.setPlayerCount(9)
    }

    /** A night with every field a preset holds off its default. */
    private fun setUpFridayNight() {
        tournament.setBuyInCents(4_050L)
        tournament.setFoodCents(550L)
        tournament.setBountyCents(500L)
        tournament.setRebuyCents(3_500L)
        tournament.setAddOnCents(1_000L)
        tournament.setRebuyUntilLevel(4)
        tournament.setPayoutSettings(topHeavyFour(PayoutRounding.FIVE_DOLLARS))
        tournament.setGameDurationHours(4)
        timer.setGameDurationMinutes(240)
        tournament.setRoundLengthMinutes(15)
        tournament.setSmallestChip(25)
        tournament.setStartingChips(10_000)
        timer.setBreakEveryLevels(3)
        timer.setBreakLengthMinutes(12)
        timer.setBreakMessage("Last rebuy")
        timer.setBigBlindAnteFromLevel(6)
        chips.restore(ownSet)
    }

    /** Back to a new tournament (a reset), with the same 9 players. */
    private fun resetEverything() {
        tournament.resetAllTournamentData()
        timer.resetAllTimerData()
        chips.resetAllData()
        tournament.setPlayerCount(9)
    }

    private fun topHeavyFour(rounding: PayoutRounding) =
        PayoutSettings(PayoutPreset.TOP_HEAVY.weightsFor(4), PayoutPreset.TOP_HEAVY, rounding)

    private fun savedAndRead(setup: PresetSetup): PresetSetup =
        requireNotNull(PresetCodec.decode(PresetCodec.encode(TournamentPreset(1L, "Friday", 0L, setup)))).setup

    @Test
    fun `a setup saved and loaded back restores every field exactly`() {
        setUpFridayNight()
        val saved = current.capture(includeChipSet = true)
        val stored = savedAndRead(saved)
        resetEverything()
        assertNotEquals(saved, current.capture(includeChipSet = true))

        assertTrue(current.apply(stored))

        assertEquals(saved, current.capture(includeChipSet = true))
        assertEquals(MoneySettings(4_050L, 550L, 500L, 3_500L, 1_000L), tournament.getMoneySettings())
        assertEquals(4, tournament.getRebuyUntilLevel())
        assertEquals(topHeavyFour(PayoutRounding.FIVE_DOLLARS), tournament.getPayoutSettings())
        assertEquals(listOf(4, 15, 25, 10_000), with(tournament) {
            listOf(getGameDurationHours(), getRoundLengthMinutes(), getSmallestChip(), getStartingChips())
        })
        assertEquals(listOf(240, 3, 12, 6), with(timer) {
            listOf(getGameDurationMinutes(), getBreakEveryLevels(), getBreakLengthMinutes(), getBigBlindAnteFromLevel())
        })
        assertEquals("Last rebuy", timer.getBreakMessage())
        assertEquals(ownSet, chips.current())
    }

    @Test
    fun `loading leaves the players, the Bank and the clock as they are`() {
        bank.savePlayerName(1, "Alice")
        bank.savePlayerBuyInStatus(1, true)
        bank.saveEliminationOrder(listOf(9))
        setUpFridayNight()
        val saved = savedAndRead(current.capture(includeChipSet = false))
        resetEverything()

        assertTrue(current.apply(saved))

        assertEquals(9, tournament.getPlayerCount())
        assertEquals("Alice", bank.getPlayerName(1))
        assertTrue(bank.getPlayerBuyInStatus(1))
        assertEquals(listOf(9), bank.getEliminationOrder())
        assertFalse(timer.getHasTimerStarted())
        assertEquals(0L, timer.getClock()?.elapsedMillis ?: 0L)
    }

    @Test
    fun `once the clock has started a preset isn't loaded, and nothing changes`() {
        setUpFridayNight()
        val friday = savedAndRead(current.capture(includeChipSet = true))
        resetEverything()
        timer.setHasTimerStarted(true)
        val before = current.capture(includeChipSet = true)
        val revision = tournament.setupRevision.value

        assertFalse(current.canLoad())
        assertFalse(current.apply(friday))

        assertEquals(before, current.capture(includeChipSet = true))
        assertEquals(revision, tournament.setupRevision.value)
    }

    @Test
    fun `loading tells the screens with their own copy to read the setup again`() {
        val revision = tournament.setupRevision.value
        assertTrue(current.apply(current.capture(includeChipSet = false)))
        assertEquals(revision + 1, tournament.setupRevision.value)
    }

    @Test
    fun `payouts at the recommended places follow tonight's player count, others keep theirs`() {
        // 9 players: Standard at the recommended 3 places follows the count
        val standard = savedAndRead(current.capture(includeChipSet = false))
        assertTrue(standard.payouts.followsPlayers)
        tournament.setPayoutSettings(topHeavyFour(PayoutRounding.DEFAULT))
        val topHeavy = savedAndRead(current.capture(includeChipSet = false))
        assertFalse(topHeavy.payouts.followsPlayers)

        tournament.setPlayerCount(13)
        current.apply(standard)
        assertEquals(PayoutPreset.STANDARD.weightsFor(4), tournament.getPayoutWeights()) // a third of 13
        current.apply(topHeavy)
        assertEquals(PayoutPreset.TOP_HEAVY.weightsFor(4), tournament.getPayoutWeights())

        // Never more places than players
        tournament.setPlayerCount(3)
        current.apply(topHeavy)
        assertEquals(PayoutPreset.TOP_HEAVY.weightsFor(3), tournament.getPayoutWeights())
    }

    @Test
    fun `a rebuy the preset turns off keeps its amount while the Bank has rebuys at it`() {
        val noRebuys = savedAndRead(current.capture(includeChipSet = false))
        tournament.setRebuyCents(4_000L)
        bank.savePlayerRebuys(1, 1)

        current.apply(noRebuys)
        assertEquals("kept: a rebuy is recorded", 4_000L, tournament.getMoneySettings().rebuyCents)

        bank.clearAllRebuys()
        current.apply(noRebuys)
        assertEquals("off: none recorded", 0L, tournament.getMoneySettings().rebuyCents)
    }

    @Test
    fun `a preset without the chip set leaves the chip set alone`() {
        val withoutChips = savedAndRead(current.capture(includeChipSet = false))
        chips.restore(ownSet)
        current.apply(withoutChips)
        assertEquals(ownSet, chips.current())
    }

    @Test
    fun `loading asks first only when it would replace what the host chose`() {
        setUpFridayNight()
        val friday = savedAndRead(current.capture(includeChipSet = true))
        resetEverything()
        assertFalse("a new tournament's setup has nothing to lose", current.wouldOverwrite(friday))

        tournament.setBuyInCents(3_000L)
        assertTrue("a buy-in the host typed", current.wouldOverwrite(friday))

        current.apply(friday)
        assertFalse("already what the preset makes it", current.wouldOverwrite(friday))
    }

    @Test
    fun `mid-game a preset saves the blinds the clock is running`() {
        tournament.setRoundLengthMinutes(20)
        tournament.setStartingChips(5_000)
        timer.setHasTimerStarted(true)
        timer.setRoundLengthAtStart(15)
        timer.setStartingChipsAtStart(10_000)
        timer.setSmallestChipAtStart(25)

        val blinds = current.capture(includeChipSet = false).blinds
        assertEquals(listOf(15, 10_000, 25), listOf(blinds.roundLengthMinutes, blinds.startingChips, blinds.smallestChip))
    }
}
