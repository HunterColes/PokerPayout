package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashGame
import com.huntercoles.pokerpayout.core.domain.cash.CashLedger
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cash game in storage (PP-029): it round-trips, survives bad entries, sits under its own new
 * keys, and nothing the tournament does to the Bank reaches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPreferencesCashTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank get() = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)

    private val game = CashGame(
        ledger = CashLedger(
            listOf(
                CashPlayer(3, "Dana", listOf(4_000L), cashOutCents = 11_200L),
                CashPlayer(1, "Sam, the elder", listOf(2_000L, 2_050L), cashOutCents = 0L),
                CashPlayer(7, "Theo", listOf(4_000L, 2_000L, 2_000L), cashOutCents = null),
            )
        ),
        paid = setOf(CashTransfer(7, 3, 4_700L), CashTransfer(1, 3, 25L)),
        splitCents = -500L,
    )

    @Before
    fun clear() {
        bank.edit().clear().commit()
    }

    @Test
    fun aCashGameRoundTripsThroughAFreshInstance() {
        BankPreferences(context).saveCashGame(game)
        assertEquals(game, BankPreferences(context).getCashGame())

        BankPreferences(context).saveCashGame(CashGame())
        assertEquals(CashGame(), BankPreferences(context).getCashGame())
        assertTrue("an empty game leaves no keys", bank.all.keys.none { it.startsWith("cash_") })
    }

    @Test
    fun theModeIsTheTournamentUntilSwitched() {
        assertEquals(BankMode.TOURNAMENT, BankPreferences(context).getBankMode())
        BankPreferences(context).saveBankMode(BankMode.CASH)
        assertEquals(BankMode.CASH, BankPreferences(context).getBankMode())
        BankPreferences(context).saveBankMode(BankMode.TOURNAMENT)
        assertEquals(BankMode.TOURNAMENT, BankPreferences(context).getBankMode())
    }

    /** The key names are the saved format: renaming one would lose a night's ledger on update. */
    @Test
    fun theKeysAreTheSavedFormat() {
        bank.edit()
            .putString("bank_mode", "cash")
            .putString("cash_players", "2,5")
            .putString("cash_name_2", "Jo")
            .putString("cash_buy_ins_2", "2000,2000")
            .putLong("cash_out_2", 5_200L)
            .putString("cash_name_5", "Ben")
            .putString("cash_buy_ins_5", "1050")
            .putString("cash_paid", "5>2:1050")
            .putLong("cash_split", 300L)
            .commit()
        val prefs = BankPreferences(context)
        assertEquals(BankMode.CASH, prefs.getBankMode())
        assertEquals(
            CashGame(
                CashLedger(listOf(CashPlayer(2, "Jo", listOf(2_000L, 2_000L), 5_200L), CashPlayer(5, "Ben", listOf(1_050L)))),
                paid = setOf(CashTransfer(5, 2, 1_050L)),
                splitCents = 300L,
            ),
            prefs.getCashGame(),
        )
    }

    @Test
    fun unreadableEntriesAreSkipped() {
        bank.edit()
            .putString("cash_players", "2,x,-1,2,4")
            .putString("cash_buy_ins_2", "2000,abc,-5,0,1000")
            .putString("cash_paid", "nonsense,4>2,4>2:0,4>2:900")
            .commit()
        val read = BankPreferences(context).getCashGame()
        assertEquals(listOf(2, 4), read.ledger.players.map { it.id })
        assertEquals(listOf(2_000L, 1_000L), read.ledger.players[0].buyInsCents)
        assertEquals("Player 4", read.ledger.players[1].name)
        assertEquals(setOf(CashTransfer(4, 2, 900L)), read.paid)
    }

    @Test
    fun theTournamentsResetsLeaveTheCashGameAlone() {
        val prefs = BankPreferences(context)
        prefs.savePlayerName(1, "Alice")
        prefs.savePlayerBuyInStatus(1, true)
        prefs.savePlayerRebuyPrices(1, listOf(1_000L))
        prefs.savePlayerAddonPrices(1, listOf(500L))
        prefs.saveCashGame(game)
        prefs.saveBankMode(BankMode.CASH)

        prefs.clearAllRebuys()
        prefs.clearAllAddons()
        prefs.clearAllEliminatedBy()
        prefs.removePlayersAbove(0)
        prefs.resetAllBankData()

        assertTrue(prefs.isInDefaultState(9))
        assertEquals(game, BankPreferences(context).getCashGame())
        assertEquals(BankMode.CASH, BankPreferences(context).getBankMode())
    }

    @Test
    fun cashWritesDontTouchTheTournamentsRecordsNorItsRevision() {
        val prefs = BankPreferences(context)
        prefs.savePlayerName(2, "Marcus")
        val tournamentKeys = bank.all.filterKeys { !it.startsWith("cash_") && it != "bank_mode" }
        val revision = prefs.revision.value
        prefs.saveCashGame(game)
        prefs.saveBankMode(BankMode.CASH)
        prefs.saveCashGame(CashGame())
        assertEquals(revision, prefs.revision.value)
        assertEquals(tournamentKeys, bank.all.filterKeys { !it.startsWith("cash_") && it != "bank_mode" })
    }
}
