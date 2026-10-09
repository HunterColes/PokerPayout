package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The settle-up's ticks in storage (1.4), and an install that still has a cash game from 1.3.14: the
 * cash page is gone, its keys are never read, and nothing about them gets in the Bank's way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPreferencesSettleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank get() = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        bank.edit().clear().commit()
    }

    @Test
    fun ticksRoundTripThroughAFreshInstanceWithTheBankAsZero() {
        val ticks = setOf(Transfer(4, 0, 2_500L), Transfer(0, 1, 17_000L), Transfer(7, 3, 1L))
        BankPreferences(context).saveSettlePaid(ticks)
        assertEquals(ticks, BankPreferences(context).getSettlePaid())

        BankPreferences(context).saveSettlePaid(emptySet())
        assertEquals(emptySet<Transfer>(), BankPreferences(context).getSettlePaid())
        assertFalse("nothing ticked leaves no key", bank.contains("settle_paid"))
    }

    @Test
    fun unreadableTicksAreSkipped() {
        bank.edit().putString("settle_paid", "4>0:2500,junk,3>3:100,5>1:0,>1:5,9>2:-300, 2>1:700 ").commit()
        assertEquals(setOf(Transfer(4, 0, 2_500L), Transfer(2, 1, 700L)), BankPreferences(context).getSettlePaid())
    }

    @Test
    fun theBanksResetClearsTheTicksAndRemovingPlayersLeavesThem() {
        val prefs = BankPreferences(context)
        prefs.savePlayerBuyInStatus(1, true)
        prefs.saveSettlePaid(setOf(Transfer(3, 1, 500L)))
        prefs.removePlayersAbove(2)
        assertEquals(setOf(Transfer(3, 1, 500L)), prefs.getSettlePaid())
        prefs.resetAllBankData()
        assertEquals(emptySet<Transfer>(), prefs.getSettlePaid())
        assertTrue(prefs.isInDefaultState(5))
    }

    /** A 1.3.14 install mid cash game, on the Cash game tab, with a tournament Bank beside it. */
    @Test
    fun anOldCashGameIsLeftAloneAndDoesntDisturbTheBank() {
        val cash = mapOf(
            "bank_mode" to "cash",
            "cash_players" to "3,1",
            "cash_name_3" to "Dana",
            "cash_buy_ins_3" to "4000,2000",
            "cash_name_1" to "Sam",
            "cash_buy_ins_1" to "2000",
            "cash_paid" to "1>3:2000",
        )
        bank.edit().apply {
            cash.forEach { (key, value) -> putString(key, value) }
            putLong("cash_out_3", 8_000L)
            putLong("cash_split", -500L)
            putLong("cash_out_1", 0L)
        }.commit()

        val prefs = BankPreferences(context)
        // The Bank reads as a fresh one: no names, no buy-ins, no purchases, nothing ticked
        assertTrue(prefs.isInDefaultState(9))
        assertEquals((1..9).map { "Player $it" }, (1..9).map { prefs.getPlayerName(it) })
        assertEquals(0, prefs.getTotalRebuyCount())
        assertEquals(0L, prefs.getRecordedRebuyCents())
        assertEquals(emptySet<Transfer>(), prefs.getSettlePaid())

        // Working the Bank, resetting it and removing players never touch the old keys
        prefs.savePlayerName(1, "Alice")
        prefs.savePlayerBuyInStatus(1, true)
        prefs.saveSettlePaid(setOf(Transfer(2, 0, 3_000L)))
        prefs.removePlayersAbove(2)
        prefs.resetAllBankData()
        cash.forEach { (key, value) -> assertEquals(key, value, bank.getString(key, null)) }
        assertEquals(8_000L, bank.getLong("cash_out_3", -1L))
        assertEquals(-500L, bank.getLong("cash_split", 0L))
        assertTrue(prefs.isInDefaultState(9))
    }
}
