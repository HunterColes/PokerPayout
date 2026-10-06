package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The rebuy cutoff ("rebuys until level N") that the clock and the Bank both read. */
@RunWith(RobolectricTestRunner::class)
class TournamentPreferencesRebuyCutoffTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `no cutoff by default, so existing users keep rebuys open all night`() {
        val prefs = TournamentPreferences(context)
        assertEquals(0, prefs.getRebuyUntilLevel())
        assertEquals(0, prefs.rebuyUntilLevel.value)
    }

    @Test
    fun `a cutoff is saved, published and survives a restart`() {
        TournamentPreferences(context).setRebuyUntilLevel(6)

        val reopened = TournamentPreferences(context)
        assertEquals(6, reopened.getRebuyUntilLevel())
        assertEquals(6, reopened.rebuyUntilLevel.value)
    }

    @Test
    fun `negative levels mean no cutoff`() {
        val prefs = TournamentPreferences(context)
        prefs.setRebuyUntilLevel(-3)
        assertEquals(0, prefs.getRebuyUntilLevel())
        assertEquals(0, prefs.rebuyUntilLevel.value)
    }

    @Test
    fun `reset clears the cutoff`() {
        val prefs = TournamentPreferences(context)
        prefs.setRebuyUntilLevel(4)
        prefs.resetAllTournamentData()
        assertEquals(0, prefs.getRebuyUntilLevel())
        assertEquals(0, prefs.rebuyUntilLevel.value)
    }
}
