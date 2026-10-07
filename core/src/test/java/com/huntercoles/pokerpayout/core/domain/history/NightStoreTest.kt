package com.huntercoles.pokerpayout.core.domain.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The saved nights (PP-037): they survive a fresh store (a restart), the latest first; a deleted one
 * put back keeps its id; and a night that can't be read is skipped and left in place while the others
 * load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NightStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = context.getSharedPreferences("night_history", Context.MODE_PRIVATE)

    private val friday = night("2026-10-02", listOf("Dana", "Marcus", "Priya"), prizes = listOf(9_000L, 3_000L))
    private val saturday = night("2026-10-03", listOf("Priya", "Dana"), prizes = listOf(8_000L))
    private val lastYear = night("2025-12-19", listOf("Theo", "Jo"), prizes = listOf(6_000L), structure = "Holiday")

    @Before
    fun clear() {
        file.edit().clear().commit()
    }

    @Test
    fun `saved nights come back from a fresh store, the latest night first`() {
        val store = NightStore(context)
        val a = store.add(saturday)
        val b = store.add(lastYear)
        val c = store.add(friday)
        assertEquals(3, listOf(a.id, b.id, c.id).distinct().size)
        assertTrue(listOf(a, b, c).all { it.id >= 1L })
        assertEquals(saturday.copy(id = a.id), a)

        assertEquals(listOf(a, c, b), NightStore(context).nights.value)
        assertEquals(c, store.get(c.id))
    }

    @Test
    fun `two nights on one day, the one saved last comes first`() {
        val store = NightStore(context)
        val early = store.add(friday)
        val late = store.add(friday.copy(players = saturday.players))
        assertEquals(listOf(late.id, early.id), NightStore(context).nights.value.map { it.id })
    }

    @Test
    fun `a deleted night put back keeps its id, and a new one never takes it`() {
        val store = NightStore(context)
        val first = store.add(friday)
        store.delete(first.id)
        assertNull(store.get(first.id))
        assertEquals(emptyList<SavedNight>(), NightStore(context).nights.value)

        val second = store.add(saturday)
        assertNotEquals(first.id, second.id)
        store.put(first) // Undo
        assertEquals(listOf(second, first), store.nights.value)
    }

    @Test
    fun `a night that can't be read is skipped, left in place, and its key never reused`() {
        val store = NightStore(context)
        val kept = store.add(friday)
        val later = JSONObject(NightCodec.encode(saturday.copy(id = 3L))).put("format", 2).toString()
        file.edit()
            .putString("night_2", "{not json")
            .putString("night_3", later)
            .putString("night_4", "")
            .putInt("night_5", 42)
            .putString("night_6", NightCodec.encode(saturday.copy(id = 9L)))
            .putString("night_x", NightCodec.encode(saturday.copy(id = 11L)))
            .commit()

        val again = NightStore(context)
        assertEquals(listOf(kept), again.nights.value)
        val added = again.add(lastYear)
        assertEquals(7L, added.id)
        assertEquals(later, file.getString("night_3", null))
        assertTrue(listOf("night_2", "night_4", "night_5", "night_6", "night_x").all { file.contains(it) })
    }

    /** A file and keys of their own: nothing the app saved before is renamed or read. */
    @Test
    fun `nights live in a file of their own, one key each`() {
        val saved = NightStore(context).add(friday)
        assertEquals(setOf("night_${saved.id}", "next_night_id"), file.all.keys)
        assertEquals(saved, NightCodec.decode(file.getString("night_${saved.id}", null)))
    }
}
