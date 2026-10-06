package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-085 in storage: each rebuy and add-on keeps its price, and the purchases a v1.3.1 Bank recorded
 * (a count, no prices) are priced once, at the amount set when the new version first opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPreferencesPricesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank get() = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)
    private val tournament get() = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        bank.edit().clear().commit()
        tournament.edit().clear().commit()
    }

    /** What v1.3.1 left behind: counts only. */
    private fun v131Bank(rebuys: Map<Int, Int>, addOns: Map<Int, Int> = emptyMap()) {
        val editor = bank.edit()
        rebuys.forEach { (id, count) -> editor.putInt("player_rebuys_$id", count) }
        addOns.forEach { (id, count) -> editor.putInt("player_addons_$id", count) }
        editor.commit()
    }

    @Test
    fun purchasesRecordedBeforeThePricesWerePricedOnceAtTheAmountSetThen() {
        TournamentPreferences(context).apply {
            setRebuyCents(1_000)
            setAddOnCents(500)
        }
        v131Bank(rebuys = mapOf(1 to 2, 3 to 1), addOns = mapOf(2 to 1))

        val prefs = BankPreferences(context)

        assertEquals(listOf(1_000L, 1_000L), prefs.getPlayerRebuyPrices(1))
        assertEquals(listOf(1_000L), prefs.getPlayerRebuyPrices(3))
        assertEquals(listOf(500L), prefs.getPlayerAddonPrices(2))
        assertEquals(3_000L, prefs.getRecordedRebuyCents())
        assertEquals(500L, prefs.getRecordedAddOnCents())
        // The counts every other reader uses are unchanged
        assertEquals(2, prefs.getPlayerRebuys(1))
        assertEquals(3, prefs.getTotalRebuyCount())
    }

    @Test
    fun theMigrationRunsOnceSoALaterPriceChangeDoesNotRevalueThem() {
        val tournamentPrefs = TournamentPreferences(context).apply { setRebuyCents(1_000) }
        v131Bank(rebuys = mapOf(1 to 2))
        BankPreferences(context)

        tournamentPrefs.setRebuyCents(1_500)
        val reopened = BankPreferences(context)

        assertEquals(listOf(1_000L, 1_000L), reopened.getPlayerRebuyPrices(1))
        assertEquals(2_000L, reopened.getRecordedRebuyCents())
    }

    @Test
    fun aVersionThatStillHadFloatAmountsIsPricedFromTheFloat() {
        // v1.1.x stored $12.50 as a Float, and the Bank may open before the Tournament settings migrate
        tournament.edit().putFloat("rebuy_per_player", 12.5f).commit()
        v131Bank(rebuys = mapOf(4 to 1))

        assertEquals(listOf(1_250L), BankPreferences(context).getPlayerRebuyPrices(4))
    }

    @Test
    fun withNoAmountSavedTheDefaultIsUsed() {
        v131Bank(rebuys = mapOf(1 to 1))
        // The default rebuy amount is $0
        assertEquals(listOf(0L), BankPreferences(context).getPlayerRebuyPrices(1))
    }

    @Test
    fun countsAreKeptInStepWithThePrices() {
        val prefs = BankPreferences(context)
        prefs.savePlayerRebuyPrices(2, listOf(1_000L, 1_500L))
        assertEquals(2, prefs.getPlayerRebuys(2))
        assertEquals(2, prefs.getTotalRebuyCount())

        prefs.savePlayerRebuyPrices(2, emptyList())
        assertEquals(0, prefs.getPlayerRebuys(2))
        assertEquals(0L, prefs.getRecordedRebuyCents())
        assertEquals(true, prefs.isInDefaultState(5))
    }

    @Test
    fun callersThatOnlyCountKeepThePricesOfWhatTheyKeep() {
        TournamentPreferences(context).setRebuyCents(1_000)
        val prefs = BankPreferences(context)
        prefs.savePlayerRebuyPrices(1, listOf(1_000L, 2_000L))

        prefs.savePlayerRebuys(1, 1)
        assertEquals(listOf(1_000L), prefs.getPlayerRebuyPrices(1))

        prefs.savePlayerRebuys(1, 3)
        assertEquals("new ones at the newest price", listOf(1_000L, 1_000L, 1_000L), prefs.getPlayerRebuyPrices(1))
    }

    @Test
    fun clearingAndRemovingPlayersTakesThePricesWithThem() {
        val prefs = BankPreferences(context)
        prefs.savePlayerRebuyPrices(1, listOf(1_000L))
        prefs.savePlayerRebuyPrices(7, listOf(1_000L, 1_000L))
        prefs.savePlayerAddonPrices(7, listOf(500L))

        prefs.removePlayersAbove(5)
        assertEquals(1_000L, prefs.getRecordedRebuyCents())
        assertEquals(0L, prefs.getRecordedAddOnCents())
        assertEquals(emptyList<Long>(), prefs.getPlayerRebuyPrices(7))

        prefs.clearAllRebuys()
        assertEquals(0L, prefs.getRecordedRebuyCents())
        assertEquals(false, bank.all.keys.any { it.startsWith("player_rebuy") })
    }

    @Test
    fun theOutLevelIsSavedAndCleared() {
        val prefs = BankPreferences(context)
        prefs.savePlayerOutLevel(3, 5)
        assertEquals(5, prefs.getPlayerOutLevel(3))
        prefs.savePlayerOutLevel(3, null)
        assertEquals(null, prefs.getPlayerOutLevel(3))
    }
}
