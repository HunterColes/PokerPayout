package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The names other features read for tonight's players (the seat draw, PP-036): the Bank's, one per
 * Tournament player, on Robolectric's real SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPlayerNamesProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("bank_prefs", "tournament_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun provider() = BankPlayerNamesProvider(TournamentPreferences(context), BankPreferences(context))

    @Test
    fun `a fresh install has the Tournament's five players under their default names`() {
        assertEquals((1..5).map { "Player $it" }, provider().currentNames())
    }

    @Test
    fun `names typed in the Bank come through in the Bank's order, one per Tournament player`() {
        TournamentPreferences(context).setPlayerCount(4)
        BankPreferences(context).apply {
            savePlayerName(1, "Alice")
            savePlayerName(3, "  Carol ")
        }

        assertEquals(listOf("Alice", "Player 2", "Carol", "Player 4"), provider().currentNames())
    }

    /** PP-116: Carol re-entered as row 5, a late arrival took row 6: Carol is named once. */
    @Test
    fun `a re-entry adds no name, a late arrival does`() {
        TournamentPreferences(context).setPlayerCount(6)
        BankPreferences(context).apply {
            savePlayerName(3, "Carol")
            savePlayerName(5, "Carol")
            savePlayerReEntryOf(5, 3)
            savePlayerName(6, "Kai")
        }

        assertEquals(listOf("Player 1", "Player 2", "Carol", "Player 4", "Kai"), provider().currentNames())
    }

    @Test
    fun `a name cleared in the Bank reads as its default, and the count follows the Tournament`() {
        BankPreferences(context).savePlayerName(2, "   ")
        TournamentPreferences(context).setPlayerCount(9)

        val names = provider().currentNames()
        assertEquals(9, names.size)
        assertEquals("Player 2", names[1])
        assertEquals("Player 9", names.last())
    }
}
