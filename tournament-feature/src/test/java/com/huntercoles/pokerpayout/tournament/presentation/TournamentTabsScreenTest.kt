package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.presentation.composable.TournamentActions
import com.huntercoles.pokerpayout.tournament.presentation.composable.TournamentContent
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tournament tab inside the app's shell, on every cell of the device matrix, with
 * goldens on the [DeviceMatrix.goldens] cells. The ViewModels are the real ones over real (in-memory)
 * preferences, set up as the mockups' game: 9 players, $40 buy-in, $5 food, $5 bounty, $40 rebuy,
 * $10 add-on, before the clock starts.
 *
 * The Tournament tab here is its setup page (S1 v2) inside the shell; its own tests
 * (TournamentScreenGoldenTest) check every state of it across the matrix. The Payouts tab has its
 * own test (`PayoutsTabScreenTest`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TournamentTabsScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val store = ViewModelStore()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        tournamentPreferences.setPlayerCount(9)
        tournamentPreferences.setBuyIn(40.0)
        tournamentPreferences.setFoodPerPlayer(5.0)
        tournamentPreferences.setBountyPerPlayer(5.0)
        tournamentPreferences.setRebuyAmount(40.0)
        tournamentPreferences.setAddOnAmount(10.0)
    }

    @After
    fun tearDown() = store.clear()

    @Test
    fun tournamentTab() {
        val setup = configViewModel()
        val timer = viewModel {
            val sound = mockk<SoundManager>(relaxed = true)
            val audio = AudioPreferences(ApplicationProvider.getApplicationContext())
            TimerViewModel(
                timerPreferences,
                tournamentPreferences,
                bankPreferences,
                ClockCues(sound, audio, CueVibrator { }, StillClock),
                StillClock,
                audio,
                FakeChipSets(),
                NineSeats,
            )
        }
        screen.compose.setContent {
            val configState by setup.uiState.collectAsState()
            val timerState by timer.uiState.collectAsState()
            InAppShell(NavTab.Tournament) {
                TournamentContent(setup = configState, timer = timerState, ui = TournamentUi(), actions = TournamentActions())
            }
        }
        LayoutAssertions.assertTouchTargets(screen.compose, "Tournament tab on ${config.id}", strict = false)
        golden("Shell_tournament")
    }

    private fun configViewModel() = viewModel {
        TournamentConfigViewModel(CalculatePayoutsUseCase(), tournamentPreferences, timerPreferences, bankPreferences)
    }

    /** A ViewModel in [store], so [tearDown] cancels its coroutines. */
    private inline fun <reified T : ViewModel> viewModel(crossinline build: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = build() as V
        }
        return ViewModelProvider(store, factory)[T::class.java]
    }

    private fun golden(name: String) {
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    /** A clock that never moves: the tournament hasn't started. */
    private object StillClock : TimeSource {
        override fun elapsedRealtimeMillis() = 1_000_000L
        override fun wallClockMillis() = 1_760_000_000_000L
        override fun bootCount() = 1
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
