package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** How TournamentPreferences keeps payout weights in step with the player count and resets. */
@RunWith(RobolectricTestRunner::class)
class PayoutWeightsResetTest {

    private lateinit var tournamentPreferences: TournamentPreferences

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        tournamentPreferences = TournamentPreferences(context)
    }

    @Test
    fun `reset replaces custom weights with the default for 5 players`() {
        tournamentPreferences.setPayoutWeights(listOf(50, 30, 20))
        assertEquals(listOf(50, 30, 20), tournamentPreferences.getPayoutWeights())

        tournamentPreferences.resetAllTournamentData()

        assertEquals(listOf(35, 20), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `reset restores default player count and matching weights`() {
        tournamentPreferences.setPlayerCount(18)
        tournamentPreferences.setPayoutWeights(listOf(50, 25, 15, 5, 3, 2))

        tournamentPreferences.resetAllTournamentData()

        assertEquals(5, tournamentPreferences.getPlayerCount())
        assertEquals(listOf(35, 20), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `default weights follow the player count until the user customises them`() {
        tournamentPreferences.setPlayerCount(9)
        assertEquals(listOf(35, 20, 15), tournamentPreferences.getPayoutWeights())

        tournamentPreferences.setPlayerCount(18)
        assertEquals(listOf(35, 20, 15, 10, 8, 6), tournamentPreferences.getPayoutWeights())

        tournamentPreferences.setPayoutWeights(listOf(60, 40))
        tournamentPreferences.setPlayerCount(12)
        assertEquals(listOf(60, 40), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `stored weights that fail to parse fall back to the defaults`() {
        tournamentPreferences.setPayoutWeights(emptyList())

        assertEquals(listOf(35, 20), tournamentPreferences.getPayoutWeights())
    }
}
