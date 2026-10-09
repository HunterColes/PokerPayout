package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The currency amounts show in (PP-114, Tools > Currency), in a file of its own (`currency_prefs`,
 * in backups with the game). Made when the app starts (`MainApplication` asks for it), so every
 * screen, share text and the live clock format in it from the first frame ([MoneyFormat]).
 *
 * The first start of a version with currencies picks once and saves the pick, so it never changes by
 * itself afterwards (not even if the phone's language does):
 * - an install from before (anything saved: a game, presets, History, settings) keeps the dollar it
 *   always showed, so nothing changes under it;
 * - a new install follows the phone ([AppCurrency.forLocale]): euros in Germany, rupees in India.
 */
@Singleton
class CurrencyPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(load(context))
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    init {
        MoneyFormat.current = _currency.value
    }

    fun getCurrency(): AppCurrency = _currency.value

    /** Saves [currency]; every amount on screen shows in it at once. */
    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(CURRENCY_KEY, currency.key).apply()
        _currency.value = currency
        MoneyFormat.current = currency
    }

    private fun load(context: Context): AppCurrency {
        val saved = prefs.getString(CURRENCY_KEY, null)
        // One a later version saved (a backup from it) shows as the dollar here, and stays saved
        if (saved != null) return AppCurrency.byKey(saved) ?: AppCurrency.DEFAULT
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val first = firstChoice(hasEarlierData = hasEarlierData(context), locale = locale)
        prefs.edit().putString(CURRENCY_KEY, first.key).apply()
        return first
    }

    companion object {
        const val FILE = "currency_prefs"

        /** Saved data: never rename it. */
        private const val CURRENCY_KEY = "currency"

        /** The currency of a first start: the dollar for an install from before PP-114, else the phone's. */
        fun firstChoice(hasEarlierData: Boolean, locale: Locale): AppCurrency =
            if (hasEarlierData) AppCurrency.DEFAULT else AppCurrency.forLocale(locale)

        /** True when any file a backup takes (the game, presets, History, settings) holds something. */
        private fun hasEarlierData(context: Context): Boolean =
            (BackupCatalog.FILES - FILE).any { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty() }
    }
}
