package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipRef
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chip set as the clock reads it (PP-091 #9), from what the chip set saved: nothing until you
 * have changed the set, then your chips, and nothing again after a reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedChipSetProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("chip_calculator_prefs", "tournament_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `the starting set is not a chip set of yours, a changed one is, until a reset`() {
        val preferences = ChipCalculatorPreferences(context, TournamentPreferences(context))
        val provider = SavedChipSetProvider(preferences)
        assertNull(provider.current())
        assertNull(runBlocking { provider.chipSet.first() })

        preferences.setInventory(preferences.current().inventory.withCount(ChipColour.Green, 100))
        val mine = ChipSetChips(
            listOf(
                ChipRef(ChipColour.Green, 25),
                ChipRef(ChipColour.Black, 100),
                ChipRef(ChipColour.Purple, 500),
                ChipRef(ChipColour.Yellow, 1_000),
            )
        )
        assertEquals(mine, provider.current())
        assertEquals(mine, runBlocking { provider.chipSet.first() })
        // A new reader (the next launch) sees the same set
        assertEquals(mine, SavedChipSetProvider(ChipCalculatorPreferences(context, TournamentPreferences(context))).current())

        preferences.resetAllData()
        assertNull(provider.current())
        assertNull(runBlocking { provider.chipSet.first() })
    }
}
