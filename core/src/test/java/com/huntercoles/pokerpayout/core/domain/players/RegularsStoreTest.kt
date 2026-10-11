package com.huntercoles.pokerpayout.core.domain.players

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * What the regulars keep (PP-110): the Bank's names, one per name with the latest spelling and day,
 * and History's merges, both back from a fresh store (a restart, or an update); the saved format
 * read as an older version wrote it; and entries that can't be read skipped while the rest load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegularsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = context.getSharedPreferences("regulars", Context.MODE_PRIVATE)

    private val monday = LocalDate.parse("2026-10-05")
    private val friday = LocalDate.parse("2026-10-09")

    @Before
    fun clear() {
        file.edit().clear().commit()
    }

    @Test
    fun `the Bank's names come back from a fresh store, one each, the latest seen first`() {
        val store = RegularsStore(context)
        store.remember("Dana", monday)
        store.rememberAll(listOf(" marcus ", "Player 3", "", "Zoë, \"Ace\""), friday)
        store.remember("dana", friday)
        // The latest spelling is kept; an older day never moves a name back
        store.remember("Marcus", monday)

        val expected = listOf(
            KnownPlayer("dana", friday),
            KnownPlayer("Marcus", friday),
            KnownPlayer("Zoë, \"Ace\"", friday),
        )
        assertEquals(expected, store.players.value)
        assertEquals(expected, RegularsStore(context).players.value)
    }

    @Test
    fun `merges come back from a fresh store, and an empty one is none`() {
        val store = RegularsStore(context)
        assertEquals(PlayerMerges.NONE, store.merges.value)
        val merges = PlayerMerges.NONE.merge("Mike R.", "Mike").merge("Danny", "Dan")
        store.setMerges(merges)
        assertEquals(merges, RegularsStore(context).merges.value)
        assertEquals("Mike", RegularsStore(context).merges.value.resolve("mike r."))
    }

    @Test
    fun `the saved format reads as this version wrote it, so an update keeps everyone`() {
        // Written by hand as 1.4 saves it: never change this test's text, add a field instead
        file.edit()
            .putString("known_players", """[{"name":"Dana","seen":"2026-10-05"},{"name":"Bea","seen":"2026-10-09","later":1}]""")
            .putString("merges", """[{"from":"Mike R.","into":"Mike"}]""")
            .commit()
        val store = RegularsStore(context)
        assertEquals(listOf(KnownPlayer("Bea", friday), KnownPlayer("Dana", monday)), store.players.value)
        assertEquals(listOf(Merge("Mike R.", "Mike")), store.merges.value.all)
    }

    @Test
    fun `what can't be read is skipped, and the rest loads`() {
        file.edit()
            .putString(
                "known_players",
                """[{"name":"Dana","seen":"2026-10-05"},{"name":"Bad day","seen":"monday"},{"seen":"2026-10-05"},""" +
                    """{"name":"Player 4","seen":"2026-10-05"},7,{"name":"dana","seen":"2026-10-01"}]""",
            )
            .putString("merges", "not json")
            .commit()
        val store = RegularsStore(context)
        assertEquals(listOf(KnownPlayer("Dana", monday)), store.players.value)
        assertTrue(store.merges.value.isEmpty)
    }

    @Test
    fun `a backup's names add once, Undo takes them out, and a replace is exact`() {
        val store = RegularsStore(context)
        store.remember("Dana", monday)
        val added = store.addPlayers(
            listOf(KnownPlayer("dana", friday), KnownPlayer("Sam", friday), KnownPlayer("Player 2", friday)),
        )
        assertEquals(listOf(KnownPlayer("Sam", friday)), added)
        assertEquals(listOf(KnownPlayer("Sam", friday), KnownPlayer("Dana", monday)), store.players.value)
        store.removePlayers(added)
        assertEquals(listOf(KnownPlayer("Dana", monday)), store.players.value)

        val merges = PlayerMerges.NONE.merge("Jo", "Joanne")
        store.replaceAll(listOf(KnownPlayer("Theo", friday)), merges)
        val fresh = RegularsStore(context)
        assertEquals(listOf(KnownPlayer("Theo", friday)), fresh.players.value)
        assertEquals(merges, fresh.merges.value)
    }
}
