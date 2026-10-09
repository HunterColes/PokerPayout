package com.huntercoles.pokerpayout.tools.dealers

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dealer's choice, saved: which games are on the wheel, the house games the host added, and the
 * last game picked (so the next spin can skip it, and the screen opens on it).
 */
@Singleton
class DealersChoiceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The ids on the wheel, or null until the host first changes it (the starting wheel). */
    fun onWheel(): Set<String>? = prefs.getString(KEY_ON_WHEEL, null)?.let { text ->
        text.split(SEPARATOR).filter { it.isNotEmpty() }.map(::decode).toSet()
    }

    fun setOnWheel(ids: Set<String>) {
        prefs.edit().putString(KEY_ON_WHEEL, ids.joinToString(SEPARATOR, transform = ::encode)).apply()
    }

    /** The house games' names, in the order they were added. */
    fun houseGames(): List<String> = prefs.getString(KEY_HOUSE_GAMES, null)
        ?.split(SEPARATOR)
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull { runCatching { decode(it) }.getOrNull() }
        .orEmpty()

    fun setHouseGames(names: List<String>) {
        val edit = prefs.edit()
        if (names.isEmpty()) {
            edit.remove(KEY_HOUSE_GAMES)
        } else {
            edit.putString(KEY_HOUSE_GAMES, names.joinToString(SEPARATOR, transform = ::encode))
        }
        edit.apply()
    }

    fun lastPick(): String? = prefs.getString(KEY_LAST_PICK, null)

    fun setLastPick(id: String?) {
        val edit = prefs.edit()
        if (id == null) edit.remove(KEY_LAST_PICK) else edit.putString(KEY_LAST_PICK, id)
        edit.apply()
    }

    private fun encode(text: String): String = URLEncoder.encode(text, UTF_8)

    private fun decode(text: String): String = URLDecoder.decode(text, UTF_8)

    private companion object {
        const val PREFS_NAME = "dealers_choice_prefs"
        const val KEY_ON_WHEEL = "on_wheel"
        const val KEY_HOUSE_GAMES = "house_games"
        const val KEY_LAST_PICK = "last_pick"
        const val SEPARATOR = ","
        const val UTF_8 = "UTF-8"
    }
}
