package com.huntercoles.pokerpayout.core.audio.music

import kotlin.random.Random

/**
 * A song: where it is ([ref]) and what to call it. A ref is either a bundled track
 * ([BundledTracks.ref], "bundled:<id>") or the content URI of a file the host picked.
 */
data class MusicTrack(val ref: String, val title: String)

/** What happens at the end of a song. */
enum class RepeatMode {
    /** Through the list once, then stop. */
    OFF,

    /** Round the list again and again. */
    ALL,

    /** The same song again. */
    ONE,
    ;

    /** The next mode on the repeat button: off, all, one, off. */
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

/**
 * The music playlist as a value: the songs in the host's order, the one playing ([currentRef]),
 * shuffle and its order, and repeat. Every change returns a new playlist; nothing here plays a
 * sound. Songs are known by their ref, so moving or removing one never loses the place.
 *
 * Shuffle plays every song once in a random order (the song playing when it is turned on goes
 * first), then deals a new order for the next pass. The random numbers come from the caller, so a
 * test can seed them.
 */
data class Playlist(
    val tracks: List<MusicTrack> = emptyList(),
    val currentRef: String? = null,
    val shuffle: Boolean = false,
    /** While [shuffle] is on: every ref once, in the order they play. */
    val shuffleOrder: List<String> = emptyList(),
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    /** The song playing, or next to play; null with no songs. */
    val current: MusicTrack? get() = tracks.firstOrNull { it.ref == currentRef }

    val isEmpty: Boolean get() = tracks.isEmpty()

    /** The refs in the order they play. */
    val playOrder: List<String> get() = if (shuffle) shuffleOrder else tracks.map { it.ref }

    /** 1-based place of the current song in [playOrder] ("song 2 of 5"); 0 with none. */
    val position: Int get() = playOrder.indexOf(currentRef) + 1

    /**
     * The same playlist with its parts agreeing: a shuffle order that holds every song once, and a
     * current song that is in the list. A playlist read back from storage goes through this.
     */
    fun normalized(): Playlist {
        val unique = tracks.distinctBy { it.ref }
        val refs = unique.map { it.ref }
        val kept = shuffleOrder.filter { it in refs }.distinct()
        val order = if (shuffle) kept + (refs - kept.toSet()) else emptyList()
        val playing = currentRef?.takeIf { it in refs } ?: (if (shuffle) order else refs).firstOrNull()
        return copy(tracks = unique, currentRef = playing, shuffleOrder = order)
    }

    /**
     * Adds [newTracks] at the end, leaving out any already in the list. While shuffled, each new song
     * goes to a random place later in this pass. With no song playing yet, the first one in play
     * order becomes the current one.
     */
    fun add(newTracks: List<MusicTrack>, random: Random): Playlist {
        val known = tracks.map { it.ref }.toSet()
        val fresh = newTracks.distinctBy { it.ref }.filter { it.ref !in known }
        if (fresh.isEmpty()) return this
        val order = if (shuffle) {
            fresh.fold(shuffleOrder) { order, track ->
                val from = order.indexOf(currentRef) + 1
                val at = from + random.nextInt(order.size - from + 1)
                order.toMutableList().apply { add(at, track.ref) }
            }
        } else {
            shuffleOrder
        }
        val added = copy(tracks = tracks + fresh, shuffleOrder = order)
        return if (currentRef == null) added.copy(currentRef = added.playOrder.first()) else added
    }

    /**
     * Takes the song [ref] out. If it was the current one, the next in play order takes its place
     * (the first, at the end of the list); with no songs left there is none.
     */
    fun remove(ref: String): Playlist {
        if (tracks.none { it.ref == ref }) return this
        val order = playOrder
        val next = if (ref == currentRef) {
            val at = order.indexOf(ref)
            (order.drop(at + 1) + order.take(at)).firstOrNull()
        } else {
            currentRef
        }
        return copy(tracks = tracks.filterNot { it.ref == ref }, currentRef = next, shuffleOrder = shuffleOrder - ref)
    }

    /** Moves the song at [from] to [to] in the host's order; the song playing stays the current one. */
    fun move(from: Int, to: Int): Playlist {
        if (from !in tracks.indices || to !in tracks.indices || from == to) return this
        val moved = tracks.toMutableList().apply { add(to, removeAt(from)) }
        return copy(tracks = moved)
    }

    /** Makes [ref] the current song, if it is in the list. */
    fun select(ref: String): Playlist = if (tracks.any { it.ref == ref }) copy(currentRef = ref) else this

    /** Turns shuffle on (a fresh order, the current song first) or off (back to the host's order). */
    fun withShuffle(on: Boolean, random: Random): Playlist = when {
        on == shuffle -> this
        on -> copy(shuffle = true, shuffleOrder = dealt(random, first = currentRef))
        else -> copy(shuffle = false, shuffleOrder = emptyList())
    }

    /**
     * The next song. At the end of a song ([manual] false) repeat-one plays it again; a tap on Next
     * ([manual] true) always moves on. Past the last song the list starts over (a new shuffle order
     * when shuffled) and, with repeat off, [Advance.ended] says to stop there. Songs in [missing]
     * (files that can't be found) are passed over; when none is left the list has ended.
     */
    fun advance(manual: Boolean, missing: Set<String>, random: Random): Advance {
        val playable = tracks.any { it.ref !in missing }
        val sameAgain = !manual && repeat == RepeatMode.ONE && current != null && currentRef !in missing
        return when {
            !playable -> Advance(this, ended = true)
            sameAgain -> Advance(this, ended = false)
            else -> moveOn(missing, random)
        }
    }

    /** The next playable song in play order; past the last, the first of the next pass. */
    private fun moveOn(missing: Set<String>, random: Random): Advance {
        val order = playOrder
        val after = order.drop(order.indexOf(currentRef) + 1).firstOrNull { it !in missing }
        if (after != null) return Advance(copy(currentRef = after), ended = false)
        val nextPass = if (shuffle) copy(shuffleOrder = dealt(random, avoidFirst = currentRef)) else this
        val first = nextPass.playOrder.first { it !in missing }
        return Advance(nextPass.copy(currentRef = first), ended = repeat == RepeatMode.OFF)
    }

    /**
     * The song before the current one in play order, passing over [missing] ones. At the first song,
     * repeat-all goes round to the last; otherwise the first stays.
     */
    fun previous(missing: Set<String>): Playlist {
        val order = playOrder
        val here = order.indexOf(currentRef)
        val before = order.take(here.coerceAtLeast(0)).lastOrNull { it !in missing }
        val wrapped = order.lastOrNull { it !in missing }.takeIf { repeat == RepeatMode.ALL }
        return (before ?: wrapped)?.let { copy(currentRef = it) } ?: this
    }

    /**
     * Every ref once, in a random order: [first] at the front if given; otherwise, with more than one
     * song, never [avoidFirst] at the front (no song twice in a row across two passes).
     */
    private fun dealt(random: Random, first: String? = null, avoidFirst: String? = null): List<String> {
        val refs = tracks.map { it.ref }
        if (first != null && first in refs) return listOf(first) + (refs - first).shuffled(random)
        val order = refs.shuffled(random).toMutableList()
        if (order.size > 1 && order.first() == avoidFirst) {
            val swap = 1 + random.nextInt(order.size - 1)
            order[0] = order[swap].also { order[swap] = order[0] }
        }
        return order
    }

    /** Where [advance] leads: the playlist on the next song, and whether the list has ended there. */
    data class Advance(val playlist: Playlist, val ended: Boolean)
}
