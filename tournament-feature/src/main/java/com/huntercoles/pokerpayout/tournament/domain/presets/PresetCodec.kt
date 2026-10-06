package com.huntercoles.pokerpayout.tournament.domain.presets

import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A preset as saved text: one JSON object, versioned by [FORMAT_KEY].
 *
 * Reading is strict and never throws: a preset that isn't valid JSON, has another format number (an
 * older one this version no longer reads, or a newer one from a later version), lacks a field or has
 * one out of range comes back as null, and the store skips it. Unknown fields are ignored, so a later
 * version can add fields under the same format number.
 *
 * The keys at the foot of this file are the saved format: renaming one would lose every saved preset
 * on update.
 */
internal object PresetCodec {
    const val FORMAT_KEY = "format"

    /** The format written. A change that older readers can't read takes the next number. */
    const val FORMAT = 1

    fun encode(preset: TournamentPreset): String = PresetJsonWriter.write(preset).toString()

    /** The preset in [text], or null when it can't be read (see the class doc). */
    fun decode(text: String?): TournamentPreset? {
        if (text.isNullOrBlank()) return null
        return try {
            val json = JSONObject(text)
            if (json.optInt(FORMAT_KEY, 0) == FORMAT) PresetJsonReader.read(json) else null
        } catch (ignored: JSONException) {
            null
        }
    }
}

/** Format 1, written. */
private object PresetJsonWriter {

    fun write(preset: TournamentPreset): JSONObject {
        val setup = preset.setup
        val json = JSONObject()
            .put(PresetCodec.FORMAT_KEY, PresetCodec.FORMAT)
            .put(ID, preset.id)
            .put(NAME, preset.name)
            .put(LAST_USED, preset.lastUsedMillis)
            .put(MONEY, money(setup))
            .put(BLINDS, blinds(setup.blinds))
            .put(PAYOUTS, payouts(setup.payouts))
        setup.chipSet?.let { json.put(CHIP_SET, chipSet(it)) }
        return json
    }

    private fun money(setup: PresetSetup): JSONObject = JSONObject()
        .put(BUY_IN, setup.money.buyInCents)
        .put(FOOD, setup.money.foodCents)
        .put(BOUNTY, setup.money.bountyCents)
        .put(REBUY, setup.money.rebuyCents)
        .put(ADD_ON, setup.money.addOnCents)
        .put(REBUY_UNTIL, setup.rebuyUntilLevel)

    private fun blinds(blinds: PresetBlinds): JSONObject = JSONObject()
        .put(DURATION, blinds.durationMinutes)
        .put(LEVEL_LENGTH, blinds.roundLengthMinutes)
        .put(SMALLEST_CHIP, blinds.smallestChip)
        .put(STACK, blinds.startingChips)
        .put(BREAK_EVERY, blinds.breakEveryLevels)
        .put(BREAK_LENGTH, blinds.breakLengthMinutes)
        .put(BREAK_NOTE, blinds.breakNote)
        .put(ANTE_FROM, blinds.anteFromLevel)

    private fun payouts(payouts: PresetPayouts): JSONObject {
        val json = JSONObject()
            .put(WEIGHTS, JSONArray().apply { payouts.weights.forEach { put(it) } })
            .put(ROUNDING, payouts.rounding.unitCents)
            .put(FOLLOWS_PLAYERS, payouts.followsPlayers)
        payouts.preset?.let { json.put(PRESET, presetKey(it)) }
        return json
    }

    private fun chipSet(chips: ChipSetSettings): JSONObject {
        val json = JSONObject()
            .put(INVENTORY, chips.inventory.encode())
            .put(REVIEWED, chips.inventoryReviewed)
            .put(SHAPE, chips.shape.id)
            .put(MAX_COLOURS, chips.maxColours)
        chips.stackOverride?.let { json.put(STACK_OVERRIDE, it) }
        chips.reserveOverride?.let { json.put(RESERVE, it) }
        return json
    }
}

/** Format 1, read. Anything missing or out of range throws [JSONException], which [PresetCodec] turns into a skip. */
private object PresetJsonReader {

    /** The longest game the setup offers, in minutes (24 hours). */
    private const val MAX_DURATION_MINUTES = 24 * 60

    fun read(json: JSONObject): TournamentPreset {
        val name = TournamentPreset.cleanName(json.getString(NAME))
        valid(name.isNotEmpty(), NAME)
        val moneyJson = json.getJSONObject(MONEY)
        return TournamentPreset(
            id = json.atLeast(ID, 1L),
            name = name,
            lastUsedMillis = json.atLeast(LAST_USED, 0L),
            setup = PresetSetup(
                money = money(moneyJson),
                rebuyUntilLevel = moneyJson.atLeast(REBUY_UNTIL, 0),
                blinds = blinds(json.getJSONObject(BLINDS)),
                payouts = payouts(json.getJSONObject(PAYOUTS)),
                chipSet = json.optJSONObject(CHIP_SET)?.let { chipSet(it) },
            ),
        )
    }

    private fun money(json: JSONObject) = MoneySettings(
        buyInCents = json.atLeast(BUY_IN, 0L),
        foodCents = json.atLeast(FOOD, 0L),
        bountyCents = json.atLeast(BOUNTY, 0L),
        rebuyCents = json.atLeast(REBUY, 0L),
        addOnCents = json.atLeast(ADD_ON, 0L),
    )

    private fun blinds(json: JSONObject) = PresetBlinds(
        durationMinutes = json.atLeast(DURATION, 1).also { valid(it <= MAX_DURATION_MINUTES, DURATION) },
        roundLengthMinutes = json.atLeast(LEVEL_LENGTH, 1),
        smallestChip = json.atLeast(SMALLEST_CHIP, 1),
        startingChips = json.atLeast(STACK, 1),
        breakEveryLevels = json.atLeast(BREAK_EVERY, 0),
        breakLengthMinutes = json.atLeast(BREAK_LENGTH, 1),
        breakNote = json.optString(BREAK_NOTE, "").take(BreakSettings.MAX_MESSAGE_LENGTH),
        anteFromLevel = json.atLeast(ANTE_FROM, 0),
    )

    private fun payouts(json: JSONObject): PresetPayouts {
        val array = json.getJSONArray(WEIGHTS)
        valid(array.length() in 1..PayoutPlaces.MAX, WEIGHTS)
        val weights = (0 until array.length()).map { index -> array.getInt(index).also { valid(it > 0, WEIGHTS) } }
        val unitCents = json.getLong(ROUNDING)
        val rounding = PayoutRounding.entries.firstOrNull { it.unitCents == unitCents } ?: invalid(ROUNDING)
        val preset = if (json.has(PRESET)) {
            val key = json.getString(PRESET)
            PayoutPreset.entries.firstOrNull { presetKey(it) == key } ?: invalid(PRESET)
        } else {
            null
        }
        return PresetPayouts(weights, preset, rounding, json.optBoolean(FOLLOWS_PLAYERS, false))
    }

    private fun chipSet(json: JSONObject): ChipSetSettings {
        val shape = ChipDistributionCurve.fromId(json.getString(SHAPE)) ?: invalid(SHAPE)
        return ChipSetSettings(
            inventory = ChipInventory.decode(json.getString(INVENTORY)) ?: ChipInventory.EMPTY,
            inventoryReviewed = json.getBoolean(REVIEWED),
            stackOverride = if (json.has(STACK_OVERRIDE)) json.atLeast(STACK_OVERRIDE, 1) else null,
            shape = shape,
            maxColours = json.getInt(MAX_COLOURS).also { valid(it in ChipSetSettings.MAX_COLOURS_RANGE, MAX_COLOURS) },
            reserveOverride = if (json.has(RESERVE)) {
                json.getInt(RESERVE).also { valid(it in ChipSetSettings.RESERVE_RANGE, RESERVE) }
            } else {
                null
            },
        )
    }

    private fun JSONObject.atLeast(key: String, min: Int): Int = getInt(key).also { valid(it >= min, key) }

    private fun JSONObject.atLeast(key: String, min: Long): Long = getLong(key).also { valid(it >= min, key) }

    /** A value out of range makes the whole preset unreadable, as a missing one does. */
    private fun valid(ok: Boolean, key: String) {
        if (!ok) invalid(key)
    }

    private fun invalid(key: String): Nothing = throw JSONException("Preset field out of range: $key")
}

/** Payout presets by a saved key of their own, so renaming the enum can't lose them. */
private fun presetKey(preset: PayoutPreset): String = when (preset) {
    PayoutPreset.TOP_HEAVY -> "top_heavy"
    PayoutPreset.STANDARD -> "standard"
    PayoutPreset.FLAT -> "flat"
}

// The saved format's keys (format 1).
private const val ID = "id"
private const val NAME = "name"
private const val LAST_USED = "lastUsed"
private const val MONEY = "money"
private const val BUY_IN = "buyIn"
private const val FOOD = "food"
private const val BOUNTY = "bounty"
private const val REBUY = "rebuy"
private const val ADD_ON = "addOn"
private const val REBUY_UNTIL = "rebuyUntil"
private const val BLINDS = "blinds"
private const val DURATION = "minutes"
private const val LEVEL_LENGTH = "levelMinutes"
private const val SMALLEST_CHIP = "smallestChip"
private const val STACK = "stack"
private const val BREAK_EVERY = "breakEvery"
private const val BREAK_LENGTH = "breakMinutes"
private const val BREAK_NOTE = "breakNote"
private const val ANTE_FROM = "anteFrom"
private const val PAYOUTS = "payouts"
private const val WEIGHTS = "weights"
private const val PRESET = "preset"
private const val ROUNDING = "roundingCents"
private const val FOLLOWS_PLAYERS = "followsPlayers"
private const val CHIP_SET = "chipSet"
private const val INVENTORY = "inventory"
private const val REVIEWED = "reviewed"
private const val STACK_OVERRIDE = "stackOverride"
private const val SHAPE = "shape"
private const val MAX_COLOURS = "maxColours"
private const val RESERVE = "reserve"
