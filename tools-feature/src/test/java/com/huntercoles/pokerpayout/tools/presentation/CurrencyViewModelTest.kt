package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.CurrencyPreferences
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Currency (S25, PP-114) against real saved preferences: it shows the saved pick, a pick saves under
 * the same key and at once changes how every amount reads, and the next start comes back to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class CurrencyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(CurrencyPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        MoneyFormat.current = AppCurrency.DEFAULT
    }

    private fun prefs() = context.getSharedPreferences(CurrencyPreferences.FILE, Context.MODE_PRIVATE)

    private fun newViewModel(preferences: CurrencyPreferences = CurrencyPreferences(context)): CurrencyViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CurrencyViewModel(preferences) as T
        }
        return ViewModelProvider(store, factory)[CurrencyViewModel::class.java].also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun aNewInstallOnAnAmericanPhoneShowsTheDollarPicked() {
        val state = newViewModel().uiState.value
        assertEquals(AppCurrency.DOLLAR, state.picked)
        assertEquals(AppCurrency.entries, state.choices)
    }

    @Test
    fun showsThePickSavedBefore() {
        prefs().edit().putString("currency", "rupee").commit()
        assertEquals(AppCurrency.RUPEE, newViewModel().uiState.value.picked)
    }

    @Test
    fun aPickIsSavedAndEveryAmountReadsInItAtOnce() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(CurrencyIntent.Pick(AppCurrency.EURO))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppCurrency.EURO, viewModel.uiState.value.picked)
        assertEquals("euro", prefs().getString("currency", null))
        assertEquals("12,50 €", FormatUtils.formatMoney(1_250))
        // The next start
        MoneyFormat.current = AppCurrency.DEFAULT
        assertEquals(AppCurrency.EURO, CurrencyPreferences(context).getCurrency())
    }

    @Test
    fun switchingBackChangesNothingSaved() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(CurrencyIntent.Pick(AppCurrency.YEN))
        assertEquals("¥13", FormatUtils.formatMoney(1_250))
        viewModel.acceptIntent(CurrencyIntent.Pick(AppCurrency.DOLLAR))
        assertEquals("$12.50", FormatUtils.formatMoney(1_250))
    }
}
