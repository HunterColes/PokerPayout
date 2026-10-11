package com.huntercoles.pokerpayout.core.domain.players

import com.huntercoles.pokerpayout.core.domain.history.Season

/** One spelling counted as another person's (PP-110): "Mike R." ([from]) is "Mike" ([into], the name kept). */
data class Merge(val from: String, val into: String)

/**
 * Two spellings of one person, merged in History (PP-110), so that "Mike" and "Mike R." add up to one
 * season. Saved nights are never edited: a merge only says which name a spelling counts as, so
 * Separate (or Undo) gives the night's own spelling back exactly.
 *
 * Names match as [Season.key] matches them (trimmed, ignoring case). Every spelling points straight
 * at the name kept: merging "Mike" into "Michael" moves "Mike R." along with it. Reading never loops,
 * even over a damaged file that does.
 */
class PlayerMerges private constructor(private val byKey: Map<String, Merge>) {

    /** Every merge, oldest first. */
    val all: List<Merge> get() = byKey.values.toList()

    val isEmpty: Boolean get() = byKey.isEmpty()

    /** The name [name] counts as: the name kept at the end of its merges, or [name] itself, trimmed. */
    fun resolve(name: String): String {
        var current = name.trim()
        val seen = mutableSetOf<String>()
        while (seen.add(Season.key(current))) {
            current = byKey[Season.key(current)]?.into?.trim() ?: return current
        }
        // A loop (only a damaged file has one): stop where it closes
        return current
    }

    /** Who [name] is, as a key: names with the same key are one person. */
    fun key(name: String): String = Season.key(resolve(name))

    /** True when [a] and [b] count as one person. */
    fun same(a: String, b: String): Boolean = key(a) == key(b)

    /** The other spellings that count as [name]'s person, oldest merge first. */
    fun aliasesOf(name: String): List<String> {
        val person = key(name)
        return byKey.values.filter { key(it.from) == person && Season.key(it.from) != person }.map { it.from }
    }

    /**
     * [from]'s person now counts as [into]'s, under [into]'s kept name. Merging two names that are
     * one person already changes nothing, so a merge made twice is made once.
     */
    fun merge(from: String, into: String): PlayerMerges {
        val person = resolve(from)
        val kept = resolve(into)
        val personKey = Season.key(person)
        if (personKey.isEmpty() || Season.key(kept).isEmpty() || personKey == Season.key(kept)) return this
        val next = LinkedHashMap<String, Merge>()
        byKey.forEach { (spelling, merge) ->
            next[spelling] = if (key(merge.into) == personKey) merge.copy(into = kept) else merge
        }
        next[personKey] = Merge(from = person, into = kept)
        return PlayerMerges(next)
    }

    /** [spelling] counts as itself again. */
    fun separate(spelling: String): PlayerMerges =
        if (Season.key(spelling) in byKey) PlayerMerges(byKey - Season.key(spelling)) else this

    /**
     * These merges, and the ones [other] has that these don't (a backup from another phone): a
     * spelling merged here stays as it is, and a merge that would make two names one twice over is
     * left out.
     */
    operator fun plus(other: PlayerMerges): PlayerMerges = other.all.fold(this) { merges, merge ->
        if (Season.key(merge.from) in merges.byKey) merges else merges.merge(merge.from, merge.into)
    }

    override fun equals(other: Any?): Boolean = other is PlayerMerges && other.byKey == byKey

    override fun hashCode(): Int = byKey.hashCode()

    override fun toString(): String = "PlayerMerges(${all.joinToString { "${it.from} -> ${it.into}" }})"

    companion object {
        val NONE = PlayerMerges(emptyMap())

        /**
         * The merges in [merges], as saved: one per spelling (the later one wins), none of a name into
         * itself or of a blank name.
         */
        fun of(merges: List<Merge>): PlayerMerges {
            val byKey = LinkedHashMap<String, Merge>()
            merges.forEach { merge ->
                val from = Season.key(merge.from)
                val into = Season.key(merge.into)
                if (from.isNotEmpty() && into.isNotEmpty() && from != into) {
                    byKey.remove(from)
                    byKey[from] = Merge(merge.from.trim(), merge.into.trim())
                }
            }
            return PlayerMerges(byKey)
        }
    }
}
