package com.huntercoles.pokerpayout.tournament.domain.presets

import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A preset as saved text (PP-032): every field comes back exactly, and anything that isn't a readable
 * format-1 preset (corrupt, another format, a field missing or out of range) is skipped, never thrown.
 * Robolectric, for the platform's org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetCodecTest {

    /** Every field off its default, with text that needs escaping. */
    private val everyField = TournamentPreset(
        id = 7L,
        name = "Friday \"deep\" stack",
        lastUsedMillis = 1_791_236_820_000L,
        setup = PresetSetup(
            money = MoneySettings(
                buyInCents = 4_050L,
                foodCents = 550L,
                bountyCents = 500L,
                rebuyCents = 3_500L,
                addOnCents = 1_000L,
            ),
            rebuyUntilLevel = 4,
            blinds = PresetBlinds(
                durationMinutes = 240,
                roundLengthMinutes = 15,
                smallestChip = 25,
                startingChips = 10_000,
                breakEveryLevels = 3,
                breakLengthMinutes = 12,
                breakNote = "Last rebuy,\nthen add-ons",
                anteFromLevel = 6,
            ),
            payouts = PresetPayouts(
                weights = listOf(55, 25, 13, 7),
                preset = PayoutPreset.TOP_HEAVY,
                rounding = PayoutRounding.FIVE_DOLLARS,
                followsPlayers = false,
            ),
            chipSet = ChipSetSettings(
                inventory = ChipInventory.of(
                    listOf(
                        InventoryChip(ChipColour.White, 25, 200),
                        InventoryChip(ChipColour.Red, 100, 175),
                        InventoryChip(ChipColour.Green, 500, 100),
                    )
                ),
                inventoryReviewed = true,
                stackOverride = 8_000,
                shape = ChipDistributionCurve.BellCurve,
                maxColours = 4,
                reserveOverride = 3,
            ),
            lateEntryUntilLevel = 5,
        ),
    )

    private fun roundTrip(preset: TournamentPreset) = PresetCodec.decode(PresetCodec.encode(preset))

    /** [everyField] as saved, changed by [change]. */
    private fun mutated(change: JSONObject.() -> Unit): String =
        JSONObject(PresetCodec.encode(everyField)).apply(change).toString()

    @Test
    fun `a preset with every field set comes back exactly`() {
        assertEquals(everyField, roundTrip(everyField))
    }

    @Test
    fun `hand-edited weights, no chip set and no chip set overrides come back exactly`() {
        val custom = everyField.copy(
            setup = everyField.setup.copy(
                payouts = PresetPayouts(listOf(60, 25, 15), preset = null, PayoutRounding.ONE_DOLLAR, followsPlayers = false),
                chipSet = null,
            )
        )
        assertEquals(custom, roundTrip(custom))

        val plainChips = everyField.copy(setup = everyField.setup.copy(chipSet = ChipSetSettings()))
        assertEquals(plainChips, roundTrip(plainChips))
    }

    @Test
    fun `every payout preset and rounding comes back as itself`() {
        (PayoutPreset.entries + null).forEach { preset ->
            PayoutRounding.entries.forEach { rounding ->
                val weights = preset?.weightsFor(3) ?: listOf(5, 3, 2)
                val payouts = PresetPayouts(weights, preset, rounding, followsPlayers = preset != null)
                val saved = everyField.copy(setup = everyField.setup.copy(payouts = payouts))
                assertEquals("$preset at $rounding", saved, roundTrip(saved))
            }
        }
    }

    /** PP-116: a preset saved before the late entry cutoff loads with none; a negative one is unreadable. */
    @Test
    fun `a preset from before the late entry cutoff loads with no cutoff`() {
        val older = mutated { getJSONObject("money").remove("lateEntryUntil") }
        val loaded = requireNotNull(PresetCodec.decode(older))
        assertEquals(0, loaded.setup.lateEntryUntilLevel)
        assertEquals(everyField.setup.copy(lateEntryUntilLevel = 0), loaded.setup)
        assertNull(PresetCodec.decode(mutated { getJSONObject("money").put("lateEntryUntil", -1) }))
    }

    @Test
    fun `text that isn't a preset is skipped, not thrown`() {
        listOf(null, "", "   ", "not json", "[]", "42", "{}", "{\"format\":1}", "{\"format\":1,\"name\":\"x\"}").forEach {
            assertNull("\"$it\" isn't a preset", PresetCodec.decode(it))
        }
    }

    @Test
    fun `a preset in another format is skipped`() {
        assertNull("a later version's", PresetCodec.decode(mutated { put("format", 2) }))
        assertNull("no version", PresetCodec.decode(mutated { remove("format") }))
        assertNull("format 0", PresetCodec.decode(mutated { put("format", 0) }))
    }

    @Test
    fun `a preset missing a field or with one out of range is skipped`() {
        val broken = mapOf<String, JSONObject.() -> Unit>(
            "no money" to { remove("money") },
            "no buy-in" to { getJSONObject("money").remove("buyIn") },
            "a negative buy-in" to { getJSONObject("money").put("buyIn", -100) },
            "a buy-in in words" to { getJSONObject("money").put("buyIn", "forty") },
            "zero-minute levels" to { getJSONObject("blinds").put("levelMinutes", 0) },
            "a 25-hour game" to { getJSONObject("blinds").put("minutes", 25 * 60) },
            "no smallest chip" to { getJSONObject("blinds").remove("smallestChip") },
            "no weights" to { getJSONObject("payouts").put("weights", JSONArray()) },
            "a zero weight" to { getJSONObject("payouts").put("weights", JSONArray(listOf(50, 0, 50))) },
            "ten places" to { getJSONObject("payouts").put("weights", JSONArray((10 downTo 1).toList())) },
            "a rounding the app doesn't offer" to { getJSONObject("payouts").put("roundingCents", 300) },
            "a payout preset the app doesn't have" to { getJSONObject("payouts").put("preset", "winner_takes_all") },
            "a chip shape the app doesn't have" to { getJSONObject("chipSet").put("shape", "zigzag") },
            "nine colours a stack" to { getJSONObject("chipSet").put("maxColours", 9) },
            "a blank name" to { put("name", "   ") },
            "id 0" to { put("id", 0) },
        )
        broken.forEach { (what, change) -> assertNull("$what: skipped", PresetCodec.decode(mutated(change))) }
    }

    @Test
    fun `fields this version doesn't know are ignored`() {
        val later = mutated {
            put("seats", 9)
            getJSONObject("blinds").put("colour", "red")
        }
        assertEquals(everyField, PresetCodec.decode(later))
    }

    /** The key names are the saved format: renaming one would lose every saved preset on update. */
    @Test
    fun `the saved format's keys`() {
        val json = JSONObject(PresetCodec.encode(everyField))
        assertEquals(1, json.getInt("format"))
        assertEquals(7L, json.getLong("id"))
        assertEquals("Friday \"deep\" stack", json.getString("name"))
        assertEquals(1_791_236_820_000L, json.getLong("lastUsed"))
        val money = json.getJSONObject("money")
        val moneyKeys = listOf("buyIn", "food", "bounty", "rebuy", "addOn", "rebuyUntil")
        assertEquals(listOf(4_050L, 550L, 500L, 3_500L, 1_000L, 4L), moneyKeys.map { money.getLong(it) })
        val blinds = json.getJSONObject("blinds")
        val blindKeys = listOf("minutes", "levelMinutes", "smallestChip", "stack", "breakEvery", "breakMinutes", "anteFrom")
        assertEquals(listOf(240, 15, 25, 10_000, 3, 12, 6), blindKeys.map { blinds.getInt(it) })
        assertEquals("Last rebuy,\nthen add-ons", blinds.getString("breakNote"))
        val payouts = json.getJSONObject("payouts")
        assertEquals("top_heavy", payouts.getString("preset"))
        assertEquals(500L, payouts.getLong("roundingCents"))
        assertEquals("[55,25,13,7]", payouts.getJSONArray("weights").toString())
        assertEquals(false, payouts.getBoolean("followsPlayers"))
        val chips = json.getJSONObject("chipSet")
        assertEquals("white:25:200;red:100:175;green:500:100", chips.getString("inventory"))
        assertEquals("bell", chips.getString("shape"))
        assertEquals(listOf(8_000, 4, 3), listOf("stackOverride", "maxColours", "reserve").map { chips.getInt(it) })
        assertEquals(true, chips.getBoolean("reviewed"))
    }
}
