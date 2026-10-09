package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.utils.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Bank recorded, per player.
 *
 * Rebuys and add-ons keep the price each was bought at (PP-085): `player_rebuy_prices_<id>` holds
 * them in cents, oldest first, next to the old `player_rebuys_<id>` count, which stays in step for
 * readers that only count. Purchases recorded before PP-085 had no price; [migratePurchasePrices]
 * prices them once at the amount set then.
 *
 * The settle-up's ticks live here too ([getSettlePaid]). The cash game (PP-029) that 1.3.14 kept here
 * went in 1.4: its keys are no longer read or written, and stay as they were on old installs (see
 * the companion object).
 */
@Singleton
class BankPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)

    init {
        // Before any total below is read.
        migratePurchasePrices()
    }

    private val _eliminationOrder = MutableStateFlow(readEliminationOrderFromPrefs())
    val eliminationOrder: Flow<List<Int>> = _eliminationOrder.asStateFlow()
    private val _totalRebuys = MutableStateFlow(calculateTotalRebuys())
    val totalRebuys: Flow<Int> = _totalRebuys.asStateFlow()
    private val _totalAddons = MutableStateFlow(calculateTotalAddons())
    val totalAddons: Flow<Int> = _totalAddons.asStateFlow()
    private val _revision = MutableStateFlow(0L)

    /** Bumped by every write, so screens that only read the Bank (the Tournament tab) can refresh. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun savePlayerName(playerId: Int, name: String) {
        prefs.edit().putString("$PLAYER_NAME_PREFIX$playerId", name).apply()
        changed()
    }

    fun getPlayerName(playerId: Int): String {
        return prefs.getString("$PLAYER_NAME_PREFIX$playerId", "Player $playerId") ?: "Player $playerId"
    }

    fun savePlayerBuyInStatus(playerId: Int, buyIn: Boolean) {
        prefs.edit().putBoolean("player_buyin_$playerId", buyIn).apply()
        changed()
    }

    fun getPlayerBuyInStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_buyin_$playerId", false)
    }

    fun savePlayerOutStatus(playerId: Int, out: Boolean) {
        prefs.edit().putBoolean("player_out_$playerId", out).apply()
        changed()
    }

    fun getPlayerOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_out_$playerId", false)
    }

    fun savePlayerPayedOutStatus(playerId: Int, payedOut: Boolean) {
        prefs.edit().putBoolean("player_payedout_$playerId", payedOut).apply()
        changed()
    }

    fun getPlayerPayedOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_payedout_$playerId", false)
    }

    fun savePlayerEliminatedBy(playerId: Int, eliminatedBy: Int?) {
        val editor = prefs.edit()
        if (eliminatedBy == null) {
            editor.remove("$PLAYER_ELIMINATED_BY_PREFIX$playerId")
        } else {
            editor.putInt("$PLAYER_ELIMINATED_BY_PREFIX$playerId", eliminatedBy)
        }
        editor.apply()
        changed()
    }

    fun getPlayerEliminatedBy(playerId: Int): Int? {
        if (!prefs.contains("$PLAYER_ELIMINATED_BY_PREFIX$playerId")) return null
        return prefs.getInt("$PLAYER_ELIMINATED_BY_PREFIX$playerId", -1)
            .takeIf { it > 0 }
    }

    /**
     * Mystery bounties (PP-035): the envelope drawn when [playerId] was knocked out, in cents, won
     * by whoever is credited with the knockout; null if none was drawn. Its key starts with
     * "player_", so the Bank's reset and removing players clear it with the rest of the player.
     */
    fun getPlayerBountyDraw(playerId: Int): Long? {
        val key = "$PLAYER_BOUNTY_DRAW_PREFIX$playerId"
        return if (prefs.contains(key)) prefs.getLong(key, 0L).coerceAtLeast(0L) else null
    }

    /**
     * Mystery bounties (PP-035): true once an envelope has been drawn for any of players 1 to
     * [playerCount]. From then on the envelopes are dealt, so the bounty and the player count that
     * made them stay put (the count can still go up for a late entry).
     */
    fun hasBountyDraws(playerCount: Int): Boolean = (1..playerCount).any { prefs.contains("$PLAYER_BOUNTY_DRAW_PREFIX$it") }

    fun savePlayerBountyDraw(playerId: Int, cents: Long?) {
        val editor = prefs.edit()
        if (cents == null) {
            editor.remove("$PLAYER_BOUNTY_DRAW_PREFIX$playerId")
        } else {
            editor.putLong("$PLAYER_BOUNTY_DRAW_PREFIX$playerId", cents.coerceAtLeast(0L))
        }
        editor.apply()
        changed()
    }

    /**
     * Sets [playerId]'s rebuy count. Rebuys kept keep their prices; new ones are priced at the
     * newest recorded one, or today's rebuy amount. The Bank records prices itself through
     * [savePlayerRebuyPrices]; this is for callers that only count.
     */
    fun savePlayerRebuys(playerId: Int, rebuys: Int) {
        savePlayerRebuyPrices(playerId, resized(getPlayerRebuyPrices(playerId), rebuys, ::currentRebuyPriceCents))
    }

    fun getPlayerRebuys(playerId: Int): Int {
        return prefs.getInt("$PLAYER_REBUYS_PREFIX$playerId", 0)
    }

    /** What each of [playerId]'s rebuys cost, oldest first. */
    fun getPlayerRebuyPrices(playerId: Int): List<Long> =
        readPrices(REBUY_PRICES_PREFIX, PLAYER_REBUYS_PREFIX, playerId, ::currentRebuyPriceCents)

    /** Records [playerId]'s rebuys at [pricesCents], oldest first (the count follows). */
    fun savePlayerRebuyPrices(playerId: Int, pricesCents: List<Long>) {
        writePrices(REBUY_PRICES_PREFIX, PLAYER_REBUYS_PREFIX, playerId, pricesCents)
        changed()
    }

    /** Sets [playerId]'s add-on count, as [savePlayerRebuys] does for rebuys. */
    fun savePlayerAddons(playerId: Int, addons: Int) {
        savePlayerAddonPrices(playerId, resized(getPlayerAddonPrices(playerId), addons, ::currentAddOnPriceCents))
    }

    fun getPlayerAddons(playerId: Int): Int {
        return prefs.getInt("$PLAYER_ADDONS_PREFIX$playerId", 0)
    }

    /** What each of [playerId]'s add-ons cost, oldest first. */
    fun getPlayerAddonPrices(playerId: Int): List<Long> =
        readPrices(ADDON_PRICES_PREFIX, PLAYER_ADDONS_PREFIX, playerId, ::currentAddOnPriceCents)

    /** Records [playerId]'s add-ons at [pricesCents], oldest first (the count follows). */
    fun savePlayerAddonPrices(playerId: Int, pricesCents: List<Long>) {
        writePrices(ADDON_PRICES_PREFIX, PLAYER_ADDONS_PREFIX, playerId, pricesCents)
        changed()
    }

    /** Every recorded rebuy, at the price it was bought at. */
    fun getRecordedRebuyCents(): Long = sumOfPrices(REBUY_PRICES_PREFIX)

    /** Every recorded add-on, at the price it was bought at. */
    fun getRecordedAddOnCents(): Long = sumOfPrices(ADDON_PRICES_PREFIX)

    /** The level [playerId] was knocked out at, if the clock was running then. */
    fun getPlayerOutLevel(playerId: Int): Int? =
        prefs.getInt("$PLAYER_OUT_LEVEL_PREFIX$playerId", 0).takeIf { it > 0 }

    fun savePlayerOutLevel(playerId: Int, level: Int?) {
        val editor = prefs.edit()
        if (level == null || level <= 0) {
            editor.remove("$PLAYER_OUT_LEVEL_PREFIX$playerId")
        } else {
            editor.putInt("$PLAYER_OUT_LEVEL_PREFIX$playerId", level)
        }
        editor.apply()
        changed()
    }

    fun getTotalRebuyCount(): Int = _totalRebuys.value

    fun getTotalAddonCount(): Int = _totalAddons.value

    fun clearAllRebuys() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_REBUYS_PREFIX) || it.startsWith(REBUY_PRICES_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun clearAllAddons() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_ADDONS_PREFIX) || it.startsWith(ADDON_PRICES_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun clearAllEliminatedBy() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_ELIMINATED_BY_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun getEliminationOrder(): List<Int> = _eliminationOrder.value

    fun saveEliminationOrder(order: List<Int>) {
        val sanitized = order
            .filter { it > 0 }
            .distinct()
        prefs.edit()
            .putString(ELIMINATION_ORDER_KEY, sanitized.joinToString(","))
            .apply()
        _eliminationOrder.value = sanitized
        changed()
    }

    /**
     * Forget everything about players numbered above [playerCount]: names, statuses, purchases and
     * knockouts, including knockouts credited to them. The Bank shows removed players as gone, so
     * their data must not come back when the count grows again or after a restart.
     */
    fun removePlayersAbove(playerCount: Int) {
        val stored = prefs.all
        val editor = prefs.edit()
        var removedAny = false
        stored.forEach { (key, value) ->
            val playerId = key.takeIf { it.startsWith(PLAYER_PREFIX) }?.substringAfterLast('_')?.toIntOrNull()
            val creditsRemovedPlayer = key.startsWith(PLAYER_ELIMINATED_BY_PREFIX) && (value as? Int ?: 0) > playerCount
            if ((playerId != null && playerId > playerCount) || creditsRemovedPlayer) {
                editor.remove(key)
                removedAny = true
            }
        }
        editor.apply()
        val order = getEliminationOrder()
        val keptOrder = order.filter { it <= playerCount }
        if (keptOrder != order) {
            saveEliminationOrder(keptOrder)
        } else if (removedAny) {
            changed()
        }
    }

    /**
     * Check if bank data is in default state (all default names, no boxes checked)
     */
    fun isInDefaultState(playerCount: Int): Boolean {
        for (playerId in 1..playerCount) {
            // Check if name was changed from default
            val savedName = getPlayerName(playerId)
            if (savedName != "Player $playerId") {
                return false
            }

            // Check if any boxes are checked or any rebuys/addons exist
            val hasStatusChange = getPlayerBuyInStatus(playerId) ||
                getPlayerOutStatus(playerId) ||
                getPlayerPayedOutStatus(playerId)
            val hasRebuyAddon = getPlayerRebuys(playerId) > 0 || getPlayerAddons(playerId) > 0
            val hasEliminationAssignment = getPlayerEliminatedBy(playerId) != null

            if (hasStatusChange || hasRebuyAddon || hasEliminationAssignment) {
                return false
            }
        }
        return true
    }

    /**
     * Reset all bank data to default values
     */
    fun resetAllBankData() {
        // Clear all bank preferences
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("player_")) {
                editor.remove(key)
            }
        }
        editor.remove(ELIMINATION_ORDER_KEY)
        editor.remove(SETTLE_PAID_KEY)
        editor.apply()
        _eliminationOrder.value = emptyList()
        clearAllEliminatedBy()
    }

    // The settle-up (1.4) -----------------------------------------------------------------------------
    //
    // The payments ticked as paid, under one new key that doesn't start with "player_", so removing
    // players and the Bank's per-player bookkeeping leave it alone; the Bank's reset clears it. Its
    // writes don't bump [revision]: no other screen reads it.

    /** The settle-up payments ticked as paid; unreadable entries are skipped. */
    fun getSettlePaid(): Set<Transfer> =
        prefs.getString(SETTLE_PAID_KEY, null).orEmpty()
            .split(",")
            .mapNotNull(::parseTransfer)
            .toSet()

    /** Replaces the ticked payments with [paid]; an empty set removes the key. */
    fun saveSettlePaid(paid: Set<Transfer>) {
        val editor = prefs.edit()
        if (paid.isEmpty()) {
            editor.remove(SETTLE_PAID_KEY)
        } else {
            editor.putString(SETTLE_PAID_KEY, paid.joinToString(",") { "${it.fromId}>${it.toId}:${it.amountCents}" })
        }
        editor.apply()
    }

    /** "4>0:2500": player 4 pays the Bank (0) $25. */
    private fun parseTransfer(stored: String): Transfer? {
        val (from, to, amount) = TRANSFER_FORMAT.matchEntire(stored.trim())?.destructured ?: return null
        return Transfer(from.toInt(), to.toInt(), amount.toLong()).takeIf { it.amountCents > 0L && it.fromId != it.toId }
    }

    // Purchase prices (PP-085) -------------------------------------------------------------------

    private fun readPrices(pricesPrefix: String, countPrefix: String, playerId: Int, price: () -> Long): List<Long> {
        val count = prefs.getInt("$countPrefix$playerId", 0).coerceAtLeast(0)
        val stored = prefs.getString("$pricesPrefix$playerId", null)
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull()?.coerceAtLeast(0L) }
            .orEmpty()
        // The count is what every reader agrees on; a missing or short list is priced at today's amount.
        return if (stored.size == count) stored else resized(stored, count, price)
    }

    private fun writePrices(pricesPrefix: String, countPrefix: String, playerId: Int, pricesCents: List<Long>) {
        val editor = prefs.edit()
        if (pricesCents.isEmpty()) {
            editor.remove("$pricesPrefix$playerId").remove("$countPrefix$playerId")
        } else {
            editor.putString("$pricesPrefix$playerId", pricesCents.joinToString(",") { it.coerceAtLeast(0L).toString() })
                .putInt("$countPrefix$playerId", pricesCents.size)
        }
        editor.apply()
    }

    private fun sumOfPrices(pricesPrefix: String): Long {
        val countPrefix = if (pricesPrefix == REBUY_PRICES_PREFIX) PLAYER_REBUYS_PREFIX else PLAYER_ADDONS_PREFIX
        val price = if (pricesPrefix == REBUY_PRICES_PREFIX) ::currentRebuyPriceCents else ::currentAddOnPriceCents
        return prefs.all.keys
            .filter { it.startsWith(countPrefix) }
            .mapNotNull { it.removePrefix(countPrefix).toIntOrNull() }
            .sumOf { id -> readPrices(pricesPrefix, countPrefix, id, price).sum() }
    }

    /**
     * Purchases recorded before PP-085 have a count and no prices. Price each once, at the rebuy or
     * add-on amount set now, so a later change to the amount doesn't re-value them.
     */
    private fun migratePurchasePrices() {
        val stored = prefs.all
        val editor = prefs.edit()
        var migrated = false
        listOf(
            Triple(PLAYER_REBUYS_PREFIX, REBUY_PRICES_PREFIX, ::currentRebuyPriceCents),
            Triple(PLAYER_ADDONS_PREFIX, ADDON_PRICES_PREFIX, ::currentAddOnPriceCents)
        ).forEach { (countPrefix, pricesPrefix, price) ->
            stored.forEach { (key, value) ->
                val playerId = key.takeIf { it.startsWith(countPrefix) }?.removePrefix(countPrefix)?.toIntOrNull()
                val count = (value as? Int) ?: 0
                if (playerId != null && count > 0 && !stored.containsKey("$pricesPrefix$playerId")) {
                    editor.putString("$pricesPrefix$playerId", List(count) { price() }.joinToString(","))
                    migrated = true
                }
            }
        }
        if (migrated) editor.apply()
    }

    /** Today's rebuy amount, read from the Tournament settings (any Bank write may need it). */
    private fun currentRebuyPriceCents(): Long =
        tournamentPriceCents(TOURNAMENT_REBUY_CENTS_KEY, TOURNAMENT_REBUY_LEGACY_KEY, MoneySettings.DEFAULT.rebuyCents)

    private fun currentAddOnPriceCents(): Long =
        tournamentPriceCents(TOURNAMENT_ADDON_CENTS_KEY, TOURNAMENT_ADDON_LEGACY_KEY, MoneySettings.DEFAULT.addOnCents)

    /**
     * An amount from `tournament_prefs` without depending on TournamentPreferences (which may not
     * exist yet, or not have migrated its v1.1.x Float amounts yet): the cents key, else the
     * legacy Float, else the default.
     */
    private fun tournamentPriceCents(centsKey: String, legacyKey: String, default: Long): Long {
        val tournament = context.getSharedPreferences(TOURNAMENT_PREFS, Context.MODE_PRIVATE)
        val legacy = tournament.all[legacyKey]
        return when {
            tournament.contains(centsKey) -> tournament.getLong(centsKey, default)
            legacy is Float -> Money.centsOfLegacyFloat(legacy)
            else -> default
        }.coerceAtLeast(0L)
    }

    private fun resized(prices: List<Long>, count: Int, price: () -> Long): List<Long> {
        val target = count.coerceAtLeast(0)
        return if (target <= prices.size) {
            prices.take(target)
        } else {
            val fill = prices.lastOrNull() ?: price()
            prices + List(target - prices.size) { fill }
        }
    }

    private fun changed() {
        _totalRebuys.value = calculateTotalRebuys()
        _totalAddons.value = calculateTotalAddons()
        _revision.value = _revision.value + 1
    }

    private fun readEliminationOrderFromPrefs(): List<Int> {
        val stored = prefs.getString(ELIMINATION_ORDER_KEY, null)
        if (stored.isNullOrBlank()) return emptyList()
        return stored.split(",")
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
    }

    private fun calculateTotalRebuys(): Int {
        return prefs.all.entries
            .filter { it.key.startsWith(PLAYER_REBUYS_PREFIX) }
            .sumOf { (it.value as? Number)?.toInt() ?: 0 }
    }

    private fun calculateTotalAddons(): Int {
        return prefs.all.entries
            .filter { it.key.startsWith(PLAYER_ADDONS_PREFIX) }
            .sumOf { (it.value as? Number)?.toInt() ?: 0 }
    }

    companion object {
        private const val ELIMINATION_ORDER_KEY = "elimination_order"
        private const val PLAYER_PREFIX = "player_"
        private const val PLAYER_NAME_PREFIX = "player_name_"
        private const val PLAYER_REBUYS_PREFIX = "player_rebuys_"
        private const val PLAYER_ADDONS_PREFIX = "player_addons_"
        private const val PLAYER_ELIMINATED_BY_PREFIX = "player_eliminated_by_"
        private const val REBUY_PRICES_PREFIX = "player_rebuy_prices_"
        private const val ADDON_PRICES_PREFIX = "player_addon_prices_"
        private const val PLAYER_OUT_LEVEL_PREFIX = "player_out_level_"

        /** PP-035, mystery bounties: a new key; never rename it. */
        private const val PLAYER_BOUNTY_DRAW_PREFIX = "player_bounty_draw_"

        /** The settle-up's ticked payments (1.4): a new key; never rename it. */
        private const val SETTLE_PAID_KEY = "settle_paid"

        // Retired with the cash game (PP-029, gone in 1.4): "bank_mode" and every key starting with
        // "cash_" ("cash_players", "cash_name_<id>", "cash_buy_ins_<id>", "cash_out_<id>",
        // "cash_paid", "cash_split"). Nothing reads or writes them any more and they are left as
        // they were on old installs; never reuse these names for anything else.

        /** One ticked payment, "from>to:cents" (0 is the Bank); the digit limits keep the numbers in range. */
        private val TRANSFER_FORMAT = Regex("""(\d{1,9})>(\d{1,9}):(\d{1,18})""")

        // TournamentPreferences' file and keys for the rebuy and add-on amounts (read only, for
        // pricing purchases; BankPreferencesPricesTest keeps the two in step).
        private const val TOURNAMENT_PREFS = "tournament_prefs"
        private const val TOURNAMENT_REBUY_CENTS_KEY = "rebuy_per_player_cents"
        private const val TOURNAMENT_ADDON_CENTS_KEY = "addon_per_player_cents"
        private const val TOURNAMENT_REBUY_LEGACY_KEY = "rebuy_per_player"
        private const val TOURNAMENT_ADDON_LEGACY_KEY = "addon_per_player"
    }
}
