package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOptimizer
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOutcome
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the chip set (S11, PP-033) saves.
 *
 * @property inventory the chips you own.
 * @property inventoryReviewed false until you first change the set: a set the app filled in (the
 *   starting set, or one made from your old calculator setup) asks you to check its counts.
 * @property stackOverride a starting stack to plan instead of the Tournament's; null to follow it.
 * @property shape the shape the counts follow (the old "distribution curve").
 * @property maxColours at most this many chip values in a stack (the old "denominations").
 * @property reserveOverride stacks to keep back for rebuys and add-ons, once you set it; null to keep
 *   back the Tournament's estimate ([com.huntercoles.pokerpayout.core.utils.KeptBackEstimate]).
 */
data class ChipSetSettings(
    val inventory: ChipInventory = ChipInventory.HOME_SET,
    val inventoryReviewed: Boolean = false,
    val stackOverride: Int? = null,
    val shape: ChipDistributionCurve = ChipDistributionCurve.LinearSteep,
    val maxColours: Int = DEFAULT_MAX_COLOURS,
    val reserveOverride: Int? = null
) {
    companion object {
        const val DEFAULT_MAX_COLOURS = 5
        val MAX_COLOURS_RANGE = 1..8
        val RESERVE_RANGE = 0..50
    }
}

/**
 * The chip set's saved settings ([ChipSetSettings]).
 *
 * Up to v1.3.0 this was the chip calculator: a starting-chips override, a curve (saved by its
 * display name), a denomination count and the last generated breakdown. [migrateLegacySetup] turns
 * that into an inventory once, so an existing user keeps their setup: their last stack's colours,
 * with counts for their players twice over. The override, count and curve carry on as the stack
 * settings; the breakdown and fit score are gone (the plan is live now).
 */
@Singleton
class ChipCalculatorPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val tournamentPreferences: TournamentPreferences
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacySetup()
    }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ChipSetSettings> = _settings.asStateFlow()

    fun current(): ChipSetSettings = _settings.value

    /** Saves [inventory] as the set you own, and marks it as checked by you. */
    fun setInventory(inventory: ChipInventory) {
        prefs.edit().putString(INVENTORY_KEY, inventory.encode()).putBoolean(INVENTORY_REVIEWED_KEY, true).apply()
        publish()
    }

    /** Plan [stack] instead of the Tournament's starting stack; null follows the Tournament again. */
    fun setStackOverride(stack: Int?) {
        val editor = prefs.edit()
        if (stack != null && stack > 0) editor.putInt(CUSTOM_TOTAL_CHIPS_KEY, stack) else editor.remove(CUSTOM_TOTAL_CHIPS_KEY)
        editor.apply()
        publish()
    }

    fun setShape(shape: ChipDistributionCurve) {
        prefs.edit().putString(STACK_SHAPE_KEY, shape.id).apply()
        publish()
    }

    fun setMaxColours(count: Int) {
        val valid = count.coerceIn(ChipSetSettings.MAX_COLOURS_RANGE)
        prefs.edit().putInt(DENOMINATION_COUNT_KEY, valid).apply()
        publish()
    }

    /**
     * Keep [stacks] back for rebuys and add-ons from now on, whatever the Tournament says; null keeps
     * back the Tournament's estimate again (PP-091 #3).
     */
    fun setReserveOverride(stacks: Int?) {
        val editor = prefs.edit()
        if (stacks != null) {
            editor.putInt(RESERVE_STACKS_KEY, stacks.coerceIn(ChipSetSettings.RESERVE_RANGE))
        } else {
            editor.remove(RESERVE_STACKS_KEY)
        }
        editor.apply()
        publish()
    }

    /** Back to the starting set and default stack settings. */
    fun resetAllData() {
        prefs.edit().clear().putString(INVENTORY_KEY, ChipInventory.HOME_SET.encode()).apply()
        publish()
    }

    /** Puts back everything [settings] holds (for Undo after a reset). */
    fun restore(settings: ChipSetSettings) {
        val editor = prefs.edit().clear()
            .putString(INVENTORY_KEY, settings.inventory.encode())
            .putBoolean(INVENTORY_REVIEWED_KEY, settings.inventoryReviewed)
            .putString(STACK_SHAPE_KEY, settings.shape.id)
            .putInt(DENOMINATION_COUNT_KEY, settings.maxColours)
        settings.stackOverride?.let { editor.putInt(CUSTOM_TOTAL_CHIPS_KEY, it) }
        settings.reserveOverride?.let { editor.putInt(RESERVE_STACKS_KEY, it) }
        editor.apply()
        publish()
    }

    private fun publish() {
        _settings.value = read()
    }

    private fun read(): ChipSetSettings = ChipSetSettings(
        inventory = ChipInventory.decode(prefs.getString(INVENTORY_KEY, null)) ?: ChipInventory.HOME_SET,
        inventoryReviewed = prefs.getBoolean(INVENTORY_REVIEWED_KEY, false),
        stackOverride = prefs.getInt(CUSTOM_TOTAL_CHIPS_KEY, 0).takeIf { it > 0 },
        shape = prefs.getString(STACK_SHAPE_KEY, null)?.let(ChipDistributionCurve::fromId) ?: ChipDistributionCurve.LinearSteep,
        maxColours = prefs.getInt(DENOMINATION_COUNT_KEY, ChipSetSettings.DEFAULT_MAX_COLOURS)
            .coerceIn(ChipSetSettings.MAX_COLOURS_RANGE),
        // Saved only once you set it; with nothing saved, the Tournament's estimate applies
        reserveOverride = if (prefs.contains(RESERVE_STACKS_KEY)) {
            prefs.getInt(RESERVE_STACKS_KEY, 0).coerceIn(ChipSetSettings.RESERVE_RANGE)
        } else {
            null
        }
    )

    /**
     * Once, before anything reads the inventory: build it from the chip calculator's settings.
     *
     * - A saved breakdown (the last Generate) gives its colours and per-player counts.
     * - Otherwise, if the calculator was ever used, its settings are run once more as it would have
     *   (starting chips or the override, the Tournament's smallest chip, the denomination count and
     *   the curve) and the answer's colours are used.
     * - A calculator that was never used starts from [ChipInventory.HOME_SET].
     *
     * Counts are the per-player counts for the Tournament's players twice over, in rolls of 25
     * ([ChipInventory.fromLastStack]); the screen asks you to check them. The curve moves from its
     * display name to its id; the breakdown, fit score and v1.1.x total are removed.
     */
    private fun migrateLegacySetup() {
        if (prefs.contains(INVENTORY_KEY)) return
        val players = tournamentPreferences.getPlayerCount()
        val lastStack = readLegacyBreakdown().ifEmpty { if (calculatorWasUsed()) rerunCalculator() else emptyList() }
        val inventory = ChipInventory.fromLastStack(lastStack, players) ?: ChipInventory.HOME_SET
        val editor = prefs.edit()
            .putString(INVENTORY_KEY, inventory.encode())
            .putBoolean(INVENTORY_REVIEWED_KEY, false)
            .remove(LEGACY_CHIP_BREAKDOWN_KEY)
            .remove(LEGACY_FIT_SCORE_KEY)
            .remove(LEGACY_TOTAL_PHYSICAL_CHIPS_KEY)
            .remove(LEGACY_SELECTED_CURVE_KEY)
        legacyCurve()?.let { editor.putString(STACK_SHAPE_KEY, it.id) }
        editor.apply()
    }

    private fun calculatorWasUsed(): Boolean = LEGACY_SETUP_KEYS.any { prefs.contains(it) }

    private fun legacyCurve(): ChipDistributionCurve? =
        prefs.getString(LEGACY_SELECTED_CURVE_KEY, null)?.let(ChipDistributionCurve::getCurveByName)

    /** The calculator's answer for its saved settings, as (chip value, chips per player). */
    private fun rerunCalculator(): List<Pair<Int, Int>> {
        val stack = prefs.getInt(CUSTOM_TOTAL_CHIPS_KEY, 0).takeIf { it > 0 } ?: tournamentPreferences.getStartingChips()
        val outcome = ChipDistributionOptimizer.optimize(
            targetValue = stack,
            smallestChip = SmallestChipChoices.normalize(tournamentPreferences.getSmallestChip()),
            denominationCount = prefs.getInt(DENOMINATION_COUNT_KEY, ChipSetSettings.DEFAULT_MAX_COLOURS),
            curve = legacyCurve() ?: ChipDistributionCurve.LinearSteep
        )
        return (outcome as? ChipDistributionOutcome.Success)?.distribution?.let { it.denominations.zip(it.quantities) }.orEmpty()
    }

    /** v1.1.x–v1.3.0's "value,count;value,count" breakdown. */
    private fun readLegacyBreakdown(): List<Pair<Int, Int>> =
        prefs.getString(LEGACY_CHIP_BREAKDOWN_KEY, null).orEmpty().split(";").mapNotNull { pair ->
            val parts = pair.split(",").takeIf { it.size == 2 }
            val value = parts?.get(0)?.toIntOrNull()?.takeIf { it > 0 }
            val count = parts?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            if (value != null && count != null) value to count else null
        }

    companion object {
        private const val PREFS_NAME = "chip_calculator_prefs"
        private const val INVENTORY_KEY = "chip_inventory"
        private const val INVENTORY_REVIEWED_KEY = "chip_inventory_reviewed"
        private const val STACK_SHAPE_KEY = "stack_shape"
        private const val RESERVE_STACKS_KEY = "reserve_stacks"

        // Kept from the chip calculator, same meaning.
        private const val CUSTOM_TOTAL_CHIPS_KEY = "custom_total_chips"
        private const val DENOMINATION_COUNT_KEY = "denomination_count"

        // The chip calculator's, read once by the migration and then removed.
        private const val LEGACY_SELECTED_CURVE_KEY = "selected_curve"
        private const val LEGACY_CHIP_BREAKDOWN_KEY = "chip_breakdown"
        private const val LEGACY_FIT_SCORE_KEY = "fit_score"
        private const val LEGACY_TOTAL_PHYSICAL_CHIPS_KEY = "total_physical_chips"

        private val LEGACY_SETUP_KEYS = listOf(
            CUSTOM_TOTAL_CHIPS_KEY,
            DENOMINATION_COUNT_KEY,
            LEGACY_SELECTED_CURVE_KEY,
            LEGACY_CHIP_BREAKDOWN_KEY
        )
    }
}
