package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The house rake in storage (PP-145): a new key in `tournament_prefs`, so a game saved before it (an
 * install updated mid-night) reads at 0% and pays out exactly as it did. The rake goes into the money
 * settings every pool and payout reads, back to none with a new tournament.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TournamentPreferencesRakeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tournament get() = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
    private val bank get() = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        tournament.edit().clear().commit()
        bank.edit().clear().commit()
    }

    @Test
    fun `a game saved before the house rake reads at 0 percent, its pool unchanged`() {
        // As 1.4.13 left it: money, players, payouts; no rake key
        tournament.edit()
            .putInt("player_count", 9)
            .putLong("buy_in_cents", 4_000L)
            .putString("payout_weights", "35,20,15")
            .commit()
        val prefs = TournamentPreferences(context)
        assertEquals(HouseRake.NONE, prefs.getRake())
        assertEquals(HouseRake.NONE, prefs.config.value.money.rake)
        val pool = BankPreferences(context).recordedPool(prefs.getMoneySettings(), 9)
        assertEquals(36_000L, pool.prizePoolCents)
        assertEquals(0L, pool.rakeCents)
    }

    @Test
    fun `a rake is saved under its own key and reaches the pool`() {
        val prefs = TournamentPreferences(context).apply {
            setPlayerCount(10)
            setBuyInCents(4_000L)
            setRake(HouseRake.of(500))
        }
        assertEquals(500, tournament.getInt("rake_basis_points", -1)) // the saved key: never rename it
        val fresh = TournamentPreferences(context)
        assertEquals(HouseRake.of(500), fresh.getMoneySettings().rake)
        assertEquals(HouseRake.of(500), prefs.config.value.money.rake)
        val pool = BankPreferences(context).recordedPool(fresh.getMoneySettings(), 10)
        assertEquals(40_000L, pool.prizeInCents)
        assertEquals(2_000L, pool.rakeCents)
        assertEquals(38_000L, pool.prizePoolCents)
    }

    @Test
    fun `a saved rate off the scale reads onto it`() {
        tournament.edit().putInt("rake_basis_points", 333).commit()
        assertEquals(HouseRake.of(350), TournamentPreferences(context).getRake())
        tournament.edit().putInt("rake_basis_points", 9_999).commit()
        assertEquals(HouseRake.of(2_500), TournamentPreferences(context).getRake())
        tournament.edit().putInt("rake_basis_points", -50).commit()
        assertEquals(HouseRake.NONE, TournamentPreferences(context).getRake())
    }

    @Test
    fun `a new tournament takes the rake back to none`() {
        val prefs = TournamentPreferences(context)
        assertTrue(prefs.isInDefaultState())
        prefs.setRake(HouseRake.of(1_000))
        assertFalse("a rake is a change from a new tournament", prefs.isInDefaultState())
        prefs.resetAllTournamentData()
        assertEquals(HouseRake.NONE, prefs.getRake())
        assertEquals(MoneySettings.DEFAULT, prefs.getMoneySettings())
        assertFalse(tournament.contains("rake_basis_points"))
        assertTrue(prefs.isInDefaultState())
    }
}
