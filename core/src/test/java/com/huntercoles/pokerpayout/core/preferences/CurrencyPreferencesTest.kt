package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.backup.SettingsGroup
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * PP-114: which currency the app shows, and that an update changes nothing under an existing install.
 * Written as earlier versions saved their data, into Robolectric's real SharedPreferences, on phones
 * set to different countries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrencyPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BackupCatalog.FILES.forEach { prefs(it).edit().clear().commit() }
    }

    @After
    fun tearDown() {
        MoneyFormat.current = AppCurrency.DEFAULT
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun saved(): String? = prefs(CurrencyPreferences.FILE).getString("currency", null)

    @Test
    @Config(qualifiers = "de-rDE")
    fun `an install from before keeps the dollar on a German phone`() {
        // A Bank saved by 1.4.6: a player and the buy-in, nothing about currencies
        prefs("bank_prefs").edit().putString("player_names", "Dana").putBoolean("buy_in_0", true).commit()
        val currency = CurrencyPreferences(context)
        assertEquals(AppCurrency.DOLLAR, currency.getCurrency())
        assertEquals("dollar", saved())
        assertEquals("$450", FormatUtils.formatMoney(45_000))
    }

    @Test
    @Config(qualifiers = "hi-rIN")
    fun `an install with only History or presets from before keeps the dollar too`() {
        prefs("night_history").edit().putString("nights", "[]").commit()
        assertEquals(AppCurrency.DOLLAR, CurrencyPreferences(context).getCurrency())
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `a new install on a German phone shows euros`() {
        val currency = CurrencyPreferences(context)
        assertEquals(AppCurrency.EURO, currency.getCurrency())
        assertEquals("euro", saved())
        assertEquals(AppCurrency.EURO, MoneyFormat.current)
        assertEquals("450 €", FormatUtils.formatMoney(45_000))
    }

    @Test
    @Config(qualifiers = "hi-rIN")
    fun `a new install in India shows rupees`() {
        assertEquals(AppCurrency.RUPEE, CurrencyPreferences(context).getCurrency())
    }

    @Test
    @Config(qualifiers = "ja-rJP")
    fun `a new install in Japan shows whole yen`() {
        assertEquals(AppCurrency.YEN, CurrencyPreferences(context).getCurrency())
        assertEquals("¥96", FormatUtils.formatMoney(9_550))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `a new install in the US shows dollars`() {
        assertEquals(AppCurrency.DOLLAR, CurrencyPreferences(context).getCurrency())
        assertEquals("dollar", saved())
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `the first pick stays when the phone's language changes`() {
        CurrencyPreferences(context)
        RuntimeEnvironment.setQualifiers("ja-rJP")
        assertEquals(AppCurrency.EURO, CurrencyPreferences(context).getCurrency())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `a pick is saved, shows at once and comes back on the next start`() {
        val currency = CurrencyPreferences(context)
        currency.setCurrency(AppCurrency.KRONA)
        assertEquals(AppCurrency.KRONA, currency.currency.value)
        assertEquals(AppCurrency.KRONA, MoneyFormat.current)
        assertEquals("krona", saved())
        MoneyFormat.current = AppCurrency.DEFAULT // a new process
        assertEquals(AppCurrency.KRONA, CurrencyPreferences(context).getCurrency())
        assertEquals(AppCurrency.KRONA, MoneyFormat.current)
    }

    @Test
    fun `a currency a later version saved shows as the dollar and stays saved`() {
        prefs(CurrencyPreferences.FILE).edit().putString("currency", "doubloon").commit()
        assertEquals(AppCurrency.DOLLAR, CurrencyPreferences(context).getCurrency())
        assertEquals("doubloon", saved())
    }

    @Test
    fun `the first choice is the dollar for earlier data, else the phone's`() {
        assertEquals(AppCurrency.DOLLAR, CurrencyPreferences.firstChoice(hasEarlierData = true, locale = Locale.GERMANY))
        assertEquals(AppCurrency.EURO, CurrencyPreferences.firstChoice(hasEarlierData = false, locale = Locale.GERMANY))
    }

    @Test
    fun `the currency is in backups, with the game`() {
        val file = BackupCatalog.SETTINGS.single { it.name == CurrencyPreferences.FILE }
        assertEquals(SettingsGroup.GAME, file.group)
    }
}
