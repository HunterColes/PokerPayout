package com.huntercoles.pokerpayout.tournament.domain.presets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
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
 * The saved presets (PP-032): they survive a fresh store (a restart), sort the last used first, save
 * over a name in use, and a preset that can't be read is skipped while the others load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = context.getSharedPreferences("tournament_presets", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        file.edit().clear().commit()
    }

    private fun setup(buyInCents: Long) = PresetSetup(
        money = MoneySettings(buyInCents = buyInCents, foodCents = 0L, bountyCents = 0L, rebuyCents = 0L, addOnCents = 0L),
        rebuyUntilLevel = 0,
        blinds = PresetBlinds.DEFAULT,
        payouts = PresetPayouts(PayoutPreset.STANDARD.weightsFor(3), PayoutPreset.STANDARD, PayoutRounding.DEFAULT, true),
    )

    @Test
    fun `saved presets come back from a fresh store, the last used first`() {
        val store = PresetStore(context)
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        val turbo = store.save("Turbo", setup(2_000L), nowMillis = 300L)
        val deep = store.save("Deep stack", setup(6_000L), nowMillis = 200L)

        val again = PresetStore(context)
        assertEquals(listOf(turbo, deep, friday), again.presets.value)
        assertEquals(setup(6_000L), again.get(deep.id)?.setup)
        assertEquals(3, listOf(friday.id, turbo.id, deep.id).distinct().size)
    }

    @Test
    fun `saving under a name in use saves over that preset, whatever its case and spacing`() {
        val store = PresetStore(context)
        val first = store.save("Friday night", setup(4_000L), nowMillis = 100L)
        val second = store.save("  friday   NIGHT ", setup(5_000L), nowMillis = 200L)

        assertEquals(first.id, second.id)
        assertEquals("friday NIGHT", second.name)
        assertEquals(listOf(second), store.presets.value)
        assertEquals(first.id, store.named("FRIDAY night")?.id)
    }

    @Test
    fun `rename, use and delete change only that preset`() {
        val store = PresetStore(context)
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        val turbo = store.save("Turbo", setup(2_000L), nowMillis = 200L)

        store.rename(friday.id, "  Friday   night ")
        assertEquals("Friday night", store.get(friday.id)?.name)
        assertEquals(turbo, store.get(turbo.id))

        store.markUsed(friday.id, nowMillis = 500L)
        assertEquals(listOf(friday.id, turbo.id), store.presets.value.map { it.id })

        store.delete(turbo.id)
        assertNull(store.get(turbo.id))
        assertEquals(listOf("Friday night"), PresetStore(context).presets.value.map { it.name })
    }

    @Test
    fun `a deleted preset put back keeps its id, and a new one never takes it`() {
        val store = PresetStore(context)
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        store.delete(friday.id)
        val turbo = store.save("Turbo", setup(2_000L), nowMillis = 200L)
        assertNotEquals(friday.id, turbo.id)

        store.put(friday) // Undo
        assertEquals(friday, store.get(friday.id))
        assertEquals(2, store.presets.value.size)
    }

    @Test
    fun `a preset that can't be read is skipped and the others still load`() {
        val store = PresetStore(context)
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        val later = JSONObject(PresetCodec.encode(friday.copy(id = 3L))).put("format", 2).toString()
        val wrongId = PresetCodec.encode(friday.copy(id = 9L))
        file.edit()
            .putString("preset_2", "{not json")
            .putString("preset_3", later)
            .putString("preset_4", "")
            .putInt("preset_5", 42)
            .putString("preset_6", wrongId)
            .putString("preset_x", PresetCodec.encode(friday.copy(id = 11L)))
            .commit()

        val again = PresetStore(context)
        assertEquals(listOf(friday), again.presets.value)

        // The unreadable ones are left as they are (a later version may read the format-2 one), and a
        // new preset takes a key none of them has
        val turbo = again.save("Turbo", setup(2_000L), nowMillis = 200L)
        assertEquals(7L, turbo.id)
        assertEquals(listOf(turbo, friday), again.presets.value)
        assertEquals(later, file.getString("preset_3", null))
        assertTrue(listOf("preset_2", "preset_4", "preset_5", "preset_6", "preset_x").all { file.contains(it) })
    }

    /** A file and keys of their own: nothing the app saved before is renamed or read. */
    @Test
    fun `presets live in a file of their own, one key each`() {
        val store = PresetStore(context)
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        assertEquals(setOf("preset_${friday.id}", "next_preset_id"), file.all.keys)
        assertEquals(friday, PresetCodec.decode(file.getString("preset_${friday.id}", null)))
    }
}
