package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.EntryPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Late entries and re-entries in storage (PP-116): new keys only, so saved nights from before read
 * exactly as before (no price recorded: today's amounts; no re-entry: a first entry). They go with
 * the rest of a player on the Bank's reset and when players are removed, and the late entry cutoff
 * goes with a new tournament.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPreferencesEntriesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank get() = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)
    private val tournament get() = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        bank.edit().clear().commit()
        tournament.edit().clear().commit()
    }

    @Test
    fun entryPricesAndReEntriesRoundTripThroughAFreshInstance() {
        val price = EntryPrice(buyInCents = 4_000L, foodCents = 500L, bountyCents = 750L)
        BankPreferences(context).apply {
            savePlayerEntryPrice(10, price)
            savePlayerReEntryOf(10, 4)
        }
        val fresh = BankPreferences(context)
        assertEquals(price, fresh.getPlayerEntryPrice(10))
        assertEquals(4, fresh.getPlayerReEntryOf(10))
        assertEquals(listOf(price), fresh.getRecordedEntryPrices(10))
        assertEquals(emptyList<EntryPrice>(), fresh.getRecordedEntryPrices(9))
        assertFalse("a late entry isn't the Bank as it starts", fresh.isInDefaultState(10))

        fresh.savePlayerEntryPrice(10, null)
        fresh.savePlayerReEntryOf(10, null)
        assertNull(fresh.getPlayerEntryPrice(10))
        assertNull(fresh.getPlayerReEntryOf(10))
        assertTrue("nothing recorded leaves no key", bank.all.keys.none { it.endsWith("_10") })
    }

    @Test
    fun aBankFromBeforeHasFirstEntriesAtTodaysAmounts() {
        bank.edit().putBoolean("player_buyin_3", true).putInt("player_eliminated_by_3", 1).commit()
        val prefs = BankPreferences(context)
        assertNull(prefs.getPlayerEntryPrice(3))
        assertNull(prefs.getPlayerReEntryOf(3))
        assertEquals(emptyList<EntryPrice>(), prefs.getRecordedEntryPrices(9))
    }

    @Test
    fun unreadableRecordsAreTodaysAmountsAndFirstEntries() {
        bank.edit()
            .putString("player_entry_price_5", "4000,500")
            .putString("player_entry_price_6", "4000,x,500")
            .putString("player_entry_price_7", "4000,-5,500")
            .putInt("player_reentry_of_5", 9)
            .putInt("player_reentry_of_6", 6)
            .commit()
        val prefs = BankPreferences(context)
        assertNull(prefs.getPlayerEntryPrice(5))
        assertNull(prefs.getPlayerEntryPrice(6))
        assertEquals(EntryPrice(4_000L, 0L, 500L), prefs.getPlayerEntryPrice(7))
        assertNull("only an earlier entry can be re-entered", prefs.getPlayerReEntryOf(5))
        assertNull(prefs.getPlayerReEntryOf(6))
    }

    @Test
    fun removingPlayersAndTheResetTakeTheirEntriesWithThem() {
        val prefs = BankPreferences(context)
        prefs.savePlayerEntryPrice(9, EntryPrice(4_000L, 500L, 500L))
        prefs.savePlayerReEntryOf(9, 2)
        prefs.savePlayerEntryPrice(5, EntryPrice(4_000L, 500L, 500L))
        prefs.removePlayersAbove(8)
        assertNull(prefs.getPlayerEntryPrice(9))
        assertNull(prefs.getPlayerReEntryOf(9))
        assertEquals(EntryPrice(4_000L, 500L, 500L), prefs.getPlayerEntryPrice(5))
        prefs.resetAllBankData()
        assertNull(prefs.getPlayerEntryPrice(5))
        assertTrue(prefs.isInDefaultState(8))
    }

    @Test
    fun theLateEntryCutoffIsSavedAndANewTournamentClearsIt() {
        val prefs = TournamentPreferences(context)
        assertEquals("no cutoff on an install from before", 0, prefs.getLateEntryUntilLevel())
        prefs.setLateEntryUntilLevel(4)
        assertEquals(4, TournamentPreferences(context).getLateEntryUntilLevel())
        assertEquals(4, TournamentPreferences(context).lateEntryUntilLevel.value)
        prefs.setLateEntryUntilLevel(-2)
        assertEquals(0, prefs.getLateEntryUntilLevel())
        prefs.setLateEntryUntilLevel(3)
        prefs.resetAllTournamentData()
        assertEquals(0, prefs.getLateEntryUntilLevel())
        assertEquals(0, prefs.lateEntryUntilLevel.value)
    }
}
