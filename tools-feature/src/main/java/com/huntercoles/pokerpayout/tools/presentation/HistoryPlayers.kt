package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.history.Season
import com.huntercoles.pokerpayout.core.domain.history.Standing
import com.huntercoles.pokerpayout.core.domain.players.KnownPlayer
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import com.huntercoles.pokerpayout.core.domain.players.Regular
import com.huntercoles.pokerpayout.core.domain.players.Roster

/**
 * Someone the player opened could be (S26b, PP-110): [name] as the regulars have them, and the saved
 * nights they played (none: only the Bank has used the name).
 */
data class MergeCandidate(val name: String, val nights: Int)

/**
 * A player opened from the standings (S26b, PP-110): their season, all time; the other names counted
 * as them, each of which can be separated again; and everyone they could be the same person as,
 * likely spellings first ("Mike", "Mike R."), then by name. Players who played a night with them
 * can't be them, so they aren't offered ([leftOut] says how many).
 *
 * @property picked the one picked as the same person: the sheet then asks which name to keep.
 */
data class PlayerPanel(
    val name: String,
    val standing: Standing?,
    val aliases: List<String>,
    val candidates: List<MergeCandidate>,
    val leftOut: Int,
    val picked: MergeCandidate? = null,
) {
    companion object {
        /** [name]'s panel over these nights, names and merges, or null when no regular has that name. */
        fun of(
            name: String,
            nights: List<SavedNight>,
            known: List<KnownPlayer>,
            merges: PlayerMerges,
            picked: String?,
        ): PlayerPanel? {
            val person = merges.key(name)
            val roster = Roster.of(nights, known, merges)
            val me = roster.firstOrNull { it.key == person } ?: return null
            val (free, clash) = roster.filter { it.key != person }
                .partition { Roster.sharedNight(nights, me.name, it.name, merges) == null }
            val candidates = free
                .sortedWith(compareByDescending<Regular> { Roster.looksAlike(me.name, it.name) }.thenBy { it.key })
                .map { MergeCandidate(it.name, it.nights) }
            return PlayerPanel(
                name = me.name,
                standing = Season.standings(nights, merges = merges).firstOrNull { Season.key(it.name) == person },
                aliases = merges.aliasesOf(me.name),
                candidates = candidates,
                leftOut = clash.size,
                picked = picked?.let { other -> candidates.firstOrNull { merges.same(it.name, other) } },
            )
        }
    }
}
