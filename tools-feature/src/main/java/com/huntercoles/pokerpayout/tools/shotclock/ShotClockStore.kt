package com.huntercoles.pokerpayout.tools.shotclock

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shot clock's saved settings: the time to act, the time-bank cards each player starts with,
 * and the cards each player has played (by name, so they follow a player). The running decision
 * isn't saved: it belongs to the screen, and nothing is written while it counts.
 */
@Singleton
class ShotClockStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seconds(): Int = prefs.getInt(KEY_SECONDS, DEFAULT_SECONDS).takeIf { it in PRESETS } ?: DEFAULT_SECONDS

    fun setSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SECONDS, seconds).apply()
    }

    fun cardsEach(): Int = prefs.getInt(KEY_CARDS_EACH, DEFAULT_CARDS_EACH).coerceIn(0, MAX_CARDS_EACH)

    fun setCardsEach(cards: Int) {
        prefs.edit().putInt(KEY_CARDS_EACH, cards).apply()
    }

    /** Cards played so far, by player name; anyone missing has played none. */
    fun used(): Map<String, Int> = prefs.getString(KEY_USED, null)?.let(UsedCardsCodec::decode).orEmpty()

    fun setUsed(used: Map<String, Int>) {
        val kept = used.filterValues { it > 0 }
        val edit = prefs.edit()
        if (kept.isEmpty()) edit.remove(KEY_USED) else edit.putString(KEY_USED, UsedCardsCodec.encode(kept))
        edit.apply()
    }

    companion object {
        /** The times to act a player can pick, in seconds. */
        val PRESETS = listOf(30, 45, 60)
        const val DEFAULT_SECONDS = 30

        /** Time-bank cards each player starts with: none to five. */
        const val DEFAULT_CARDS_EACH = 2
        const val MAX_CARDS_EACH = 5

        /** What one time-bank card adds to a decision. */
        const val CARD_SECONDS = 30

        private const val PREFS_NAME = "shot_clock_prefs"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_CARDS_EACH = "cards_each"
        private const val KEY_USED = "used_cards"
    }
}

/** The cards played, saved as "name=count" pairs, comma separated, the names URL-encoded. */
internal object UsedCardsCodec {
    private const val UTF_8 = "UTF-8"

    fun encode(used: Map<String, Int>): String =
        used.entries.joinToString(",") { (name, count) -> "${URLEncoder.encode(name, UTF_8)}=$count" }

    /** What could be read; a damaged pair is skipped, never thrown. */
    fun decode(text: String): Map<String, Int> = text.split(',').mapNotNull { pair ->
        val name = pair.substringBefore('=', missingDelimiterValue = "")
        val count = pair.substringAfter('=', missingDelimiterValue = "").toIntOrNull()
        runCatching { URLDecoder.decode(name, UTF_8) }.getOrNull()
            ?.takeIf { it.isNotEmpty() && count != null && count > 0 }
            ?.let { it to checkNotNull(count) }
    }.toMap()
}
