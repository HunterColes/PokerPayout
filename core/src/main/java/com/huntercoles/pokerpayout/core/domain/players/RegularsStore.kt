package com.huntercoles.pokerpayout.core.domain.players

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.domain.history.Season
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the regulars keep of their own (PP-110), in a SharedPreferences file of their own: the names
 * the Bank has used and the day it last did ([RegularsCodec], one JSON array under one key), and the
 * names merged in History (another array under another key). The roster itself is worked out from
 * these and the saved nights ([Roster]). Resetting the Bank or the tournament leaves this file alone.
 *
 * Writes are on disk before the app could be closed (apply); a replace from a backup commits.
 */
@Singleton
class RegularsStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _players = MutableStateFlow(readPlayers())

    /** The names the Bank has used, one per name (as [Season.key] matches them), the latest seen first. */
    val players: StateFlow<List<KnownPlayer>> = _players.asStateFlow()

    private val _merges = MutableStateFlow(readMerges())

    /** The names merged in History. */
    val merges: StateFlow<PlayerMerges> = _merges.asStateFlow()

    /**
     * The Bank used [name] on [day] (typed in a row, or picked for tonight): it joins the roster, in
     * this spelling, seen on [day] unless it was seen later already. A seat nobody named is nobody.
     */
    fun remember(name: String, day: LocalDate) = rememberAll(listOf(name), day)

    /** [remember] for each of [names], in one write. */
    fun rememberAll(names: Collection<String>, day: LocalDate) {
        val byKey = LinkedHashMap<String, KnownPlayer>()
        _players.value.forEach { byKey[Season.key(it.name)] = it }
        names.map(PlayerNames::clean).filterNot(PlayerNames::isPlaceholder).forEach { name ->
            val seen = byKey[Season.key(name)]?.lastSeen?.let { maxOf(it, day) } ?: day
            byKey[Season.key(name)] = KnownPlayer(name, seen)
        }
        val next = sorted(byKey.values)
        if (next != _players.value) writePlayers(next)
    }

    /** Adds [players] the store doesn't know yet, by name; returns the ones added (a backup merged in). */
    fun addPlayers(players: List<KnownPlayer>): List<KnownPlayer> {
        val here = _players.value.map { Season.key(it.name) }.toSet()
        val added = players
            .filter { !PlayerNames.isPlaceholder(it.name) && Season.key(it.name) !in here }
            .distinctBy { Season.key(it.name) }
        if (added.isNotEmpty()) writePlayers(sorted(_players.value + added))
        return added
    }

    /** Forgets the names in [players] (Undo after a backup's names were added). */
    fun removePlayers(players: List<KnownPlayer>) {
        val keys = players.map { Season.key(it.name) }.toSet()
        val next = _players.value.filter { Season.key(it.name) !in keys }
        if (next != _players.value) writePlayers(next)
    }

    /** Saves [merges] in place of the merges here (a merge, a separate, or Undo). */
    fun setMerges(merges: PlayerMerges) {
        if (merges == _merges.value) return
        prefs.edit().putString(MERGES_KEY, RegularsCodec.encodeMerges(merges).toString()).apply()
        _merges.value = merges
    }

    /** Makes the store exactly [players] and [merges] (a backup restored in place of this), on disk when it returns. */
    fun replaceAll(players: List<KnownPlayer>, merges: PlayerMerges) {
        val clean = sorted(players.filterNot { PlayerNames.isPlaceholder(it.name) }.distinctBy { Season.key(it.name) })
        prefs.edit()
            .clear()
            .putString(PLAYERS_KEY, RegularsCodec.encodePlayers(clean).toString())
            .putString(MERGES_KEY, RegularsCodec.encodeMerges(merges).toString())
            .commit()
        _players.value = clean
        _merges.value = merges
    }

    private fun writePlayers(players: List<KnownPlayer>) {
        prefs.edit().putString(PLAYERS_KEY, RegularsCodec.encodePlayers(players).toString()).apply()
        _players.value = players
    }

    /** What's saved; entries that can't be read are left out ([RegularsCodec]), and a name saved twice is read once. */
    private fun readPlayers(): List<KnownPlayer> {
        val saved = RegularsCodec.array(prefs.getString(PLAYERS_KEY, null)) ?: return emptyList()
        return sorted(RegularsCodec.decodePlayers(saved).items).distinctBy { Season.key(it.name) }
    }

    private fun readMerges(): PlayerMerges {
        val saved = RegularsCodec.array(prefs.getString(MERGES_KEY, null)) ?: return PlayerMerges.NONE
        return PlayerMerges.of(RegularsCodec.decodeMerges(saved).items)
    }

    private fun sorted(players: Collection<KnownPlayer>): List<KnownPlayer> =
        players.sortedWith(compareByDescending<KnownPlayer> { it.lastSeen }.thenBy { Season.key(it.name) })

    private companion object {
        // The saved format (PP-110): a new file and new keys; never rename them.
        const val PREFS_NAME = "regulars"
        const val PLAYERS_KEY = "known_players"
        const val MERGES_KEY = "merges"
    }
}
