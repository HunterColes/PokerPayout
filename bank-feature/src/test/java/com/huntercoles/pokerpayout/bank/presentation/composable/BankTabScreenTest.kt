package com.huntercoles.pokerpayout.bank.presentation.composable

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Bank tab inside the app's shell on every cell of the device matrix, with goldens on the
 * [DeviceMatrix.goldens] cells: the real ViewModel over real (in-memory) preferences, mid-game in the
 * mockups' game (9 named players, 7 bought in, one rebuy).
 *
 * The Bank's body is the pre-makeover one until M4 (S5), so the checks here cover what M2 owns: the
 * new top bar and its reset button. (The body's 32 dp pool-summary buttons are M4's to fix.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BankTabScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val store = ViewModelStore()
    private lateinit var viewModel: BankViewModel

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val tournament = TournamentPreferences(context).apply {
            setPlayerCount(PLAYERS.size)
            setBuyIn(40.0)
            setFoodPerPlayer(5.0)
            setBountyPerPlayer(5.0)
            setRebuyAmount(40.0)
            setAddOnAmount(10.0)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BankViewModel(
                tournament,
                BankPreferences(context),
                TimerPreferences(context),
                SettleTournamentUseCase(CalculatePayoutsUseCase()),
            ) as T
        }
        viewModel = ViewModelProvider(store, factory)[BankViewModel::class.java]
        PLAYERS.forEachIndexed { index, name -> viewModel.acceptIntent(BankIntent.PlayerNameChanged(index + 1, name)) }
        (1..7).forEach { viewModel.acceptIntent(BankIntent.BuyInToggled(it)) }
        viewModel.acceptIntent(BankIntent.PlayerRebuyChanged(2, 1))
    }

    @After
    fun tearDown() = store.clear()

    @Test
    fun bankTab() {
        screen.compose.setContent {
            val state by viewModel.uiState.collectAsState()
            InAppShell(NavTab.Bank) { BankScreen(uiState = state, onIntent = {}) }
        }
        screen.compose.onNodeWithContentDescription("Reset bank")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", "Shell_bank", config)
    }

    companion object {
        private val PLAYERS = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
