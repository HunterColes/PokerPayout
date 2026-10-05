package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Money in TournamentPreferences: whole cents (Long), and the one-time migration from the Float
 * dollars that v1.1.12 and earlier stored.
 */
@RunWith(RobolectricTestRunner::class)
class TournamentPreferencesMoneyTest {

    private lateinit var context: Context
    private lateinit var raw: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        raw = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
    }

    /** What v1.1.12 wrote: `putFloat(key, amount.toFloat())`. */
    private fun writeLegacy(vararg values: Pair<String, Float>) {
        val editor = raw.edit()
        values.forEach { (key, value) -> editor.putFloat(key, value) }
        editor.commit()
    }

    @Test
    fun `legacy Float amounts become exact cents and the Float keys are removed`() {
        writeLegacy(
            "buy_in" to 123456.78f, // read back as 123456.78125 before v1.2.0
            "food_per_player" to 12.5f,
            "bounty_per_player" to 0.1f,
            "rebuy_per_player" to 33.33f,
            "addon_per_player" to 0f
        )

        val money = TournamentPreferences(context).getMoneySettings()

        assertEquals(
            MoneySettings(buyInCents = 12_345_678, foodCents = 1_250, bountyCents = 10, rebuyCents = 3_333, addOnCents = 0),
            money
        )
        listOf("buy_in", "food_per_player", "bounty_per_player", "rebuy_per_player", "addon_per_player")
            .forEach { assertFalse(raw.contains(it), "legacy key $it should be gone") }
        assertEquals(12_345_678L, raw.getLong("buy_in_cents", -1))
    }

    @Test
    fun `migration runs once and a second start reads the same cents`() {
        writeLegacy("buy_in" to 25f, "rebuy_per_player" to 10f)

        val first = TournamentPreferences(context).getMoneySettings()
        val second = TournamentPreferences(context).getMoneySettings()

        assertEquals(2_500L, first.buyInCents)
        assertEquals(1_000L, first.rebuyCents)
        assertEquals(first, second)
    }

    @Test
    fun `missing legacy values keep the defaults`() {
        writeLegacy("food_per_player" to 7f)

        val money = TournamentPreferences(context).getMoneySettings()

        assertEquals(MoneySettings.DEFAULT.copy(foodCents = 700), money)
    }

    @Test
    fun `a cents value already present wins over a leftover Float`() {
        raw.edit().putLong("buy_in_cents", 4_200).putFloat("buy_in", 99f).commit()

        assertEquals(4_200L, TournamentPreferences(context).getMoneySettings().buyInCents)
        assertFalse(raw.contains("buy_in"))
    }

    @Test
    fun `a corrupt legacy value is dropped`() {
        raw.edit().putString("buy_in", "twenty").commit()

        assertEquals(MoneySettings.DEFAULT.buyInCents, TournamentPreferences(context).getMoneySettings().buyInCents)
        assertFalse(raw.contains("buy_in"))
    }

    @Test
    fun `fresh installs start in the default state`() {
        val preferences = TournamentPreferences(context)
        assertTrue(preferences.isInDefaultState())
        assertEquals(2_000L, preferences.getMoneySettings().buyInCents)
    }

    @Test
    fun `large amounts keep every cent`() {
        val preferences = TournamentPreferences(context)
        preferences.setBuyInCents(99_999_999_999L)
        preferences.setBuyIn(123456.78)

        assertEquals(12_345_678L, TournamentPreferences(context).getMoneySettings().buyInCents)
    }

    @Test
    fun `rounding and preset persist, and reset restores them`() {
        val preferences = TournamentPreferences(context)
        preferences.setPlayerCount(10)
        preferences.setPayoutPreset(PayoutPreset.FLAT, places = 1)
        preferences.setPayoutRounding(PayoutRounding.TEN_DOLLARS)

        val reloaded = TournamentPreferences(context)
        // One place is [100] for both Top-heavy and Flat; the stored preset tells them apart
        assertEquals(PayoutPreset.FLAT, reloaded.getPayoutPreset())
        assertEquals(PayoutRounding.TEN_DOLLARS, reloaded.getPayoutRounding())
        assertFalse(reloaded.isInDefaultState())

        reloaded.resetAllTournamentData()
        assertEquals(PayoutRounding.ONE_DOLLAR, reloaded.getPayoutRounding())
        assertEquals(PayoutPreset.STANDARD, reloaded.getPayoutPreset())
        assertTrue(reloaded.isInDefaultState())
    }

    @Test
    fun `the config flow carries every change`() {
        val preferences = TournamentPreferences(context)

        preferences.setRebuyCents(1_500)
        preferences.setPayoutRounding(PayoutRounding.FIVE_DOLLARS)
        preferences.setPlayerCount(8)

        val config = preferences.config.value
        assertEquals(1_500L, config.money.rebuyCents)
        assertEquals(PayoutRounding.FIVE_DOLLARS, config.payoutRounding)
        assertEquals(8, config.numPlayers)
        assertEquals(listOf(35, 20), config.payoutWeights)
    }
}
