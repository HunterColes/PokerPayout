package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.utils.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tournament settings. Money is stored as whole cents (`Long`). Versions up to v1.1.12 stored
 * `Float` dollars under other keys; [migrateLegacyMoney] converts those once, on first use.
 */
@Singleton
class TournamentPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)

    init {
        // Before any flow below reads a money value.
        migrateLegacyMoney()
    }

    private val _playerCount = MutableStateFlow(getPlayerCount())
    val playerCount: Flow<Int> = _playerCount.asStateFlow()

    private val _tournamentLocked = MutableStateFlow(getTournamentLocked())
    val tournamentLocked: Flow<Boolean> = _tournamentLocked.asStateFlow()

    private val _payoutWeights = MutableStateFlow(getPayoutWeights())
    val payoutWeights: Flow<List<Int>> = _payoutWeights.asStateFlow()

    private val _isConfigExpanded = MutableStateFlow(getIsConfigExpanded())
    val isConfigExpanded: Flow<Boolean> = _isConfigExpanded.asStateFlow()

    private val _rebuyUntilLevel = MutableStateFlow(getRebuyUntilLevel())

    /** The last blind level at which rebuys are allowed; 0 means no cutoff. */
    val rebuyUntilLevel: StateFlow<Int> = _rebuyUntilLevel.asStateFlow()

    private val _lateEntryUntilLevel = MutableStateFlow(getLateEntryUntilLevel())

    /** The last blind level at which a player can join late or re-enter (PP-116); 0 means no cutoff. */
    val lateEntryUntilLevel: StateFlow<Int> = _lateEntryUntilLevel.asStateFlow()

    private val _config = MutableStateFlow(getCurrentTournamentConfig())

    /** Everything the pool and payout math reads; emits after any change to it. */
    val config: StateFlow<TournamentConfigData> = _config.asStateFlow()

    private val _setupRevision = MutableStateFlow(0)

    /**
     * Goes up by one each time a whole setup is put in at once (a preset loaded, PP-032). Screens that
     * keep their own copy of the blind fields read them again. Not saved: it only counts this process.
     */
    val setupRevision: StateFlow<Int> = _setupRevision.asStateFlow()

    /** Says the whole setup was just replaced; see [setupRevision]. */
    fun setupReplaced() {
        _setupRevision.value = _setupRevision.value + 1
    }

    fun setPlayerCount(count: Int) {
        val oldCount = getPlayerCount()
        prefs.edit().putInt(PLAYER_COUNT_KEY, count).apply()
        _playerCount.value = count

        // A preset at the recommended number of places follows the player count until the user
        // picks another number of places or edits the weights.
        val preset = getPayoutPreset()
        val currentWeights = getPayoutWeights()
        if (!prefs.contains(PAYOUT_WEIGHTS_KEY)) {
            _payoutWeights.value = defaultPayoutWeightsFor(count)
        } else if (preset != null && currentWeights == preset.weightsFor(PayoutPlaces.recommended(oldCount))) {
            setPayoutPreset(preset, PayoutPlaces.recommended(count))
        }
        publish()
    }

    fun getPlayerCount(): Int {
        return prefs.getInt(PLAYER_COUNT_KEY, DEFAULT_PLAYER_COUNT)
    }

    fun setTournamentLocked(locked: Boolean) {
        prefs.edit().putBoolean(TOURNAMENT_LOCKED_KEY, locked).apply()
        _tournamentLocked.value = locked
    }

    fun getTournamentLocked(): Boolean {
        return prefs.getBoolean(TOURNAMENT_LOCKED_KEY, false)
    }

    // Money, in cents ---------------------------------------------------------------------------

    fun getMoneySettings(): MoneySettings {
        val defaults = MoneySettings.DEFAULT
        return MoneySettings(
            buyInCents = prefs.getLong(BUY_IN_CENTS_KEY, defaults.buyInCents),
            foodCents = prefs.getLong(FOOD_CENTS_KEY, defaults.foodCents),
            bountyCents = prefs.getLong(BOUNTY_CENTS_KEY, defaults.bountyCents),
            rebuyCents = prefs.getLong(REBUY_CENTS_KEY, defaults.rebuyCents),
            addOnCents = prefs.getLong(ADDON_CENTS_KEY, defaults.addOnCents),
            bountyMode = getBountyMode()
        )
    }

    /**
     * How knockouts pay (PP-035), under its own key. Games saved before PP-035 have none stored, so
     * they load as [BountyMode.STANDARD] and play exactly as before; nothing to migrate.
     */
    fun getBountyMode(): BountyMode = BountyMode.fromKey(prefs.getString(BOUNTY_MODE_KEY, null))

    fun setBountyMode(mode: BountyMode) {
        prefs.edit().putString(BOUNTY_MODE_KEY, mode.key).apply()
        publish()
    }

    fun setBuyInCents(cents: Long) = putCents(BUY_IN_CENTS_KEY, cents)
    fun setFoodCents(cents: Long) = putCents(FOOD_CENTS_KEY, cents)
    fun setBountyCents(cents: Long) = putCents(BOUNTY_CENTS_KEY, cents)
    fun setRebuyCents(cents: Long) = putCents(REBUY_CENTS_KEY, cents)
    fun setAddOnCents(cents: Long) = putCents(ADDON_CENTS_KEY, cents)

    // Dollar conveniences, converted to the exact nearest cent.
    fun setBuyIn(buyIn: Double) = setBuyInCents(Money.centsOf(buyIn))
    fun setFoodPerPlayer(food: Double) = setFoodCents(Money.centsOf(food))
    fun setBountyPerPlayer(bounty: Double) = setBountyCents(Money.centsOf(bounty))
    fun setRebuyAmount(rebuy: Double) = setRebuyCents(Money.centsOf(rebuy))
    fun setAddOnAmount(addOn: Double) = setAddOnCents(Money.centsOf(addOn))

    private fun putCents(key: String, cents: Long) {
        prefs.edit().putLong(key, cents.coerceAtLeast(0L)).apply()
        publish()
    }

    // Payout structure ----------------------------------------------------------------------------

    fun setPayoutWeights(weights: List<Int>) {
        val weightsString = weights.joinToString(",")
        prefs.edit().putString(PAYOUT_WEIGHTS_KEY, weightsString).apply()
        _payoutWeights.value = weights
        publish()
    }

    fun getPayoutWeights(): List<Int> {
        val weightsString = prefs.getString(PAYOUT_WEIGHTS_KEY, null)
        val parsedWeights = weightsString
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it > 0 }
            ?.takeIf { it.isNotEmpty() }

        return parsedWeights ?: defaultPayoutWeightsFor()
    }

    /** Pays [places] places with [preset]'s weights. */
    fun setPayoutPreset(preset: PayoutPreset, places: Int) {
        prefs.edit().putString(PAYOUT_PRESET_KEY, preset.name).apply()
        setPayoutWeights(preset.weightsFor(places))
    }

    /** The preset the current weights come from, or null once they have been edited by hand. */
    fun getPayoutPreset(): PayoutPreset? {
        val weights = getPayoutWeights()
        val stored = prefs.getString(PAYOUT_PRESET_KEY, null)
            ?.let { name -> PayoutPreset.entries.firstOrNull { it.name == name } }
        return stored?.takeIf { it.weightsFor(weights.size) == weights } ?: PayoutPreset.matching(weights)
    }

    fun getPayoutSettings(): PayoutSettings =
        PayoutSettings(weights = getPayoutWeights(), preset = getPayoutPreset(), rounding = getPayoutRounding())

    /** Saves a structure from the payout editor. Weights edited by hand drop the preset. */
    fun setPayoutSettings(settings: PayoutSettings) {
        val preset = settings.preset?.takeIf { it.weightsFor(settings.weights.size) == settings.weights }
        if (preset != null) {
            setPayoutPreset(preset, settings.weights.size)
        } else {
            prefs.edit().remove(PAYOUT_PRESET_KEY).apply()
            setPayoutWeights(settings.weights)
        }
        setPayoutRounding(settings.rounding)
    }

    fun setPayoutRounding(rounding: PayoutRounding) {
        prefs.edit().putLong(PAYOUT_ROUNDING_CENTS_KEY, rounding.unitCents).apply()
        publish()
    }

    fun getPayoutRounding(): PayoutRounding =
        PayoutRounding.fromUnitCents(prefs.getLong(PAYOUT_ROUNDING_CENTS_KEY, PayoutRounding.DEFAULT.unitCents))

    fun setRebuyUntilLevel(level: Int) {
        val value = level.coerceAtLeast(0)
        prefs.edit().putInt(REBUY_UNTIL_LEVEL_KEY, value).apply()
        _rebuyUntilLevel.value = value
    }

    fun getRebuyUntilLevel(): Int = prefs.getInt(REBUY_UNTIL_LEVEL_KEY, 0).coerceAtLeast(0)

    fun setLateEntryUntilLevel(level: Int) {
        val value = level.coerceAtLeast(0)
        prefs.edit().putInt(LATE_ENTRY_UNTIL_LEVEL_KEY, value).apply()
        _lateEntryUntilLevel.value = value
    }

    fun getLateEntryUntilLevel(): Int = prefs.getInt(LATE_ENTRY_UNTIL_LEVEL_KEY, 0).coerceAtLeast(0)

    fun setIsConfigExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(IS_CONFIG_EXPANDED_KEY, expanded).apply()
        _isConfigExpanded.value = expanded
    }

    fun getIsConfigExpanded(): Boolean {
        return prefs.getBoolean(IS_CONFIG_EXPANDED_KEY, true) // Default to expanded
    }

    // Blind Configuration Persistence
    fun setGameDurationHours(hours: Int) {
        prefs.edit().putInt(GAME_DURATION_HOURS_KEY, hours).apply()
    }

    fun getGameDurationHours(): Int {
        return prefs.getInt(GAME_DURATION_HOURS_KEY, TournamentDefaults.GAME_DURATION_HOURS)
    }

    fun setRoundLengthMinutes(minutes: Int) {
        prefs.edit().putInt(ROUND_LENGTH_MINUTES_KEY, minutes).apply()
    }

    fun getRoundLengthMinutes(): Int {
        return prefs.getInt(ROUND_LENGTH_MINUTES_KEY, TournamentDefaults.ROUND_LENGTH_MINUTES)
    }

    fun setSmallestChip(chip: Int) {
        prefs.edit().putInt(SMALLEST_CHIP_KEY, chip).apply()
    }

    fun getSmallestChip(): Int {
        return prefs.getInt(SMALLEST_CHIP_KEY, TournamentDefaults.SMALLEST_CHIP)
    }

    fun setStartingChips(chips: Int) {
        prefs.edit().putInt(STARTING_CHIPS_KEY, chips).apply()
    }

    fun getStartingChips(): Int {
        return prefs.getInt(STARTING_CHIPS_KEY, TournamentDefaults.STARTING_CHIPS)
    }

    fun setSelectedPanel(panel: String) {
        prefs.edit().putString(SELECTED_PANEL_KEY, panel).apply()
    }

    fun getSelectedPanel(): String {
        return prefs.getString(SELECTED_PANEL_KEY, "player") ?: "player"
    }

    /**
     * The tournament settings the pool and payout math needs, as one value. Other modules read
     * this (or observe [config]) instead of the individual keys.
     */
    data class TournamentConfigData(
        val numPlayers: Int,
        val money: MoneySettings,
        val payoutWeights: List<Int>,
        val payoutRounding: PayoutRounding
    )

    fun getCurrentTournamentConfig(): TournamentConfigData {
        return TournamentConfigData(
            numPlayers = getPlayerCount(),
            money = getMoneySettings(),
            payoutWeights = getPayoutWeights(),
            payoutRounding = getPayoutRounding()
        )
    }

    /**
     * Check if tournament settings are in default state
     */
    fun isInDefaultState(): Boolean {
        val playerCount = getPlayerCount()
        return playerCount == DEFAULT_PLAYER_COUNT &&
            getMoneySettings() == MoneySettings.DEFAULT &&
            getPayoutWeights() == defaultPayoutWeightsFor(playerCount) &&
            getPayoutRounding() == PayoutRounding.DEFAULT &&
            !getTournamentLocked() &&
            getGameDurationHours() == TournamentDefaults.GAME_DURATION_HOURS &&
            getRoundLengthMinutes() == TournamentDefaults.ROUND_LENGTH_MINUTES &&
            getSmallestChip() == TournamentDefaults.SMALLEST_CHIP &&
            getStartingChips() == TournamentDefaults.STARTING_CHIPS
        // Note: selectedPanel is preserved during reset, so not included in default state check
    }

    /**
     * Reset all tournament data to default values
     */
    fun resetAllTournamentData() {
        val defaults = MoneySettings.DEFAULT
        // Reset specific keys instead of clearing all preferences
        prefs.edit()
            .putBoolean(TOURNAMENT_LOCKED_KEY, false)
            .putInt(PLAYER_COUNT_KEY, DEFAULT_PLAYER_COUNT)
            .putLong(BUY_IN_CENTS_KEY, defaults.buyInCents)
            .putLong(FOOD_CENTS_KEY, defaults.foodCents)
            .putLong(BOUNTY_CENTS_KEY, defaults.bountyCents)
            .putLong(REBUY_CENTS_KEY, defaults.rebuyCents)
            .putLong(ADDON_CENTS_KEY, defaults.addOnCents)
            .remove(PAYOUT_WEIGHTS_KEY)
            .remove(PAYOUT_PRESET_KEY)
            .remove(PAYOUT_ROUNDING_CENTS_KEY)
            .putInt(GAME_DURATION_HOURS_KEY, TournamentDefaults.GAME_DURATION_HOURS)
            .putInt(ROUND_LENGTH_MINUTES_KEY, TournamentDefaults.ROUND_LENGTH_MINUTES)
            .putInt(SMALLEST_CHIP_KEY, TournamentDefaults.SMALLEST_CHIP)
            .putInt(STARTING_CHIPS_KEY, TournamentDefaults.STARTING_CHIPS)
            .putString(SELECTED_PANEL_KEY, "player")
            .putBoolean(IS_CONFIG_EXPANDED_KEY, true)
            .remove(REBUY_UNTIL_LEVEL_KEY)
            .remove(LATE_ENTRY_UNTIL_LEVEL_KEY)
            .remove(BOUNTY_MODE_KEY)
            .apply()

        // Reset all state flows to default values (keep current player count)
        _tournamentLocked.value = false
        _playerCount.value = DEFAULT_PLAYER_COUNT
        _payoutWeights.value = defaultPayoutWeightsFor(DEFAULT_PLAYER_COUNT)
        _isConfigExpanded.value = true
        _rebuyUntilLevel.value = 0
        _lateEntryUntilLevel.value = 0
        publish()
    }

    private fun publish() {
        _config.value = getCurrentTournamentConfig()
    }

    private fun defaultPayoutWeightsFor(playerCount: Int = getPlayerCount()): List<Int> =
        PayoutPreset.DEFAULT.weightsFor(PayoutPlaces.recommended(playerCount))

    /**
     * v1.1.12 and earlier stored money as Float dollars, which keeps about 7 significant digits
     * (123456.78 came back as 123456.78125). Convert each to cents once, under a new key, and drop
     * the Float. A cents value that already exists wins.
     */
    private fun migrateLegacyMoney() {
        val legacy = LEGACY_MONEY_KEYS.filterKeys { prefs.contains(it) }
        if (legacy.isEmpty()) return
        val stored = prefs.all
        val editor = prefs.edit()
        legacy.forEach { (floatKey, centsKey) ->
            val value = stored[floatKey]
            if (value is Float && !prefs.contains(centsKey)) {
                editor.putLong(centsKey, Money.centsOfLegacyFloat(value).coerceAtLeast(0L))
            }
            editor.remove(floatKey)
        }
        editor.apply()
    }

    companion object {
        private const val PLAYER_COUNT_KEY = "player_count"
        private const val TOURNAMENT_LOCKED_KEY = "tournament_locked"
        private const val BUY_IN_CENTS_KEY = "buy_in_cents"
        private const val FOOD_CENTS_KEY = "food_per_player_cents"
        private const val BOUNTY_CENTS_KEY = "bounty_per_player_cents"
        private const val REBUY_CENTS_KEY = "rebuy_per_player_cents"
        private const val ADDON_CENTS_KEY = "addon_per_player_cents"
        private const val PAYOUT_WEIGHTS_KEY = "payout_weights"
        private const val PAYOUT_PRESET_KEY = "payout_preset"
        private const val PAYOUT_ROUNDING_CENTS_KEY = "payout_rounding_cents"
        private const val IS_CONFIG_EXPANDED_KEY = "is_config_expanded"
        private const val GAME_DURATION_HOURS_KEY = "game_duration_hours"
        private const val ROUND_LENGTH_MINUTES_KEY = "round_length_minutes"
        private const val SMALLEST_CHIP_KEY = "smallest_chip"
        private const val STARTING_CHIPS_KEY = "starting_chips"
        private const val SELECTED_PANEL_KEY = "selected_panel"
        private const val REBUY_UNTIL_LEVEL_KEY = "rebuy_until_level"

        /** PP-116: the late entry cutoff; a new key, absent (no cutoff) on old installs. Never rename it. */
        private const val LATE_ENTRY_UNTIL_LEVEL_KEY = "late_entry_until_level"

        /** PP-035: "standard", "progressive" or "mystery" ([BountyMode.key]); absent means standard. */
        private const val BOUNTY_MODE_KEY = "bounty_mode"
        private const val DEFAULT_PLAYER_COUNT = TournamentDefaults.PLAYER_COUNT

        /** v1.1.x Float keys and the cents keys that replace them. */
        private val LEGACY_MONEY_KEYS = mapOf(
            "buy_in" to BUY_IN_CENTS_KEY,
            "food_per_player" to FOOD_CENTS_KEY,
            "bounty_per_player" to BOUNTY_CENTS_KEY,
            "rebuy_per_player" to REBUY_CENTS_KEY,
            "addon_per_player" to ADDON_CENTS_KEY
        )
    }
}
