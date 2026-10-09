package com.huntercoles.pokerpayout.core.audio.music

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The playlist as a value: adding, removing and moving songs never loses the place; the end of a
 * song and the Next and Previous buttons go where a music player goes, with repeat off, all or one;
 * songs whose files have gone are passed over; and shuffle, on a seeded random, plays every song
 * once a pass in an order a seed repeats exactly.
 */
class PlaylistTest {

    private fun song(n: Int) = MusicTrack("content://music/$n", "Song $n")

    private val five = (1..5).map(::song)
    private val refs = five.map { it.ref }

    private fun list(repeat: RepeatMode = RepeatMode.OFF) = Playlist().add(five, Random(0)).copy(repeat = repeat)

    private val none = emptySet<String>()

    // ------------------------------------------------------------------ the list

    @Test
    fun `the first song added is the one to play`() {
        val playlist = Playlist().add(listOf(song(1), song(2)), Random(0))
        assertEquals(song(1), playlist.current)
        assertEquals(1, playlist.position)
        assertTrue(Playlist().isEmpty)
        assertNull(Playlist().current)
        assertEquals(0, Playlist().position)
    }

    @Test
    fun `a song already in the list isn't added twice`() {
        val playlist = list().add(listOf(song(2), song(6), song(6)), Random(0))
        assertEquals(refs + song(6).ref, playlist.tracks.map { it.ref })
    }

    @Test
    fun `moving songs keeps the one playing`() {
        val playing3 = list().select(song(3).ref)
        val moved = playing3.move(from = 2, to = 0)
        assertEquals(listOf(3, 1, 2, 4, 5).map { song(it).ref }, moved.tracks.map { it.ref })
        assertEquals(song(3), moved.current)
        assertEquals(1, moved.position)
        // Out of range or onto itself: nothing moves
        assertEquals(playing3, playing3.move(0, 9))
        assertEquals(playing3, playing3.move(2, 2))
    }

    @Test
    fun `removing the song playing goes on to the next`() {
        val playing3 = list().select(song(3).ref)
        assertEquals(song(4), playing3.remove(song(3).ref).current)
        // The last song's next is the first
        assertEquals(song(1), list().select(song(5).ref).remove(song(5).ref).current)
        // Removing another song leaves the current one alone
        assertEquals(song(3), playing3.remove(song(1).ref).current)
        // The last song gone: none to play
        assertNull(Playlist().add(listOf(song(1)), Random(0)).remove(song(1).ref).current)
        // A song not in the list: nothing changes
        assertEquals(playing3, playing3.remove("content://music/99"))
    }

    // ------------------------------------------------------------------ the end of a song, and the buttons

    @Test
    fun `repeat off plays through the list once and stops at the end`() {
        var playlist = list(RepeatMode.OFF)
        val played = mutableListOf(playlist.current!!.title)
        repeat(4) {
            val step = playlist.advance(manual = false, none, Random(0))
            assertFalse(step.ended)
            playlist = step.playlist
            played += playlist.current!!.title
        }
        assertEquals(five.map { it.title }, played)
        val end = playlist.advance(manual = false, none, Random(0))
        assertTrue(end.ended)
        assertEquals(song(1), end.playlist.current) // ready to start again
    }

    @Test
    fun `repeat all goes round the list`() {
        val last = list(RepeatMode.ALL).select(song(5).ref)
        val step = last.advance(manual = false, none, Random(0))
        assertFalse(step.ended)
        assertEquals(song(1), step.playlist.current)
    }

    @Test
    fun `repeat one plays the song again, but Next moves on`() {
        val one = list(RepeatMode.ONE).select(song(2).ref)
        assertEquals(song(2), one.advance(manual = false, none, Random(0)).playlist.current)
        assertEquals(song(3), one.advance(manual = true, none, Random(0)).playlist.current)
        // Next on the last song goes round, and keeps playing
        val step = one.select(song(5).ref).advance(manual = true, none, Random(0))
        assertEquals(song(1), step.playlist.current)
        assertFalse(step.ended)
    }

    @Test
    fun `previous goes back a song, and from the first only with repeat all`() {
        assertEquals(song(2), list().select(song(3).ref).previous(none).current)
        assertEquals(song(1), list(RepeatMode.OFF).previous(none).current)
        assertEquals(song(5), list(RepeatMode.ALL).previous(none).current)
    }

    @Test
    fun `songs whose files have gone are passed over`() {
        val gone = setOf(song(2).ref, song(3).ref)
        assertEquals(song(4), list().advance(manual = false, gone, Random(0)).playlist.current)
        assertEquals(song(1), list().select(song(4).ref).previous(gone).current)
        // Repeat one on a song that has gone moves on rather than failing again
        val stuck = list(RepeatMode.ONE).select(song(2).ref)
        assertEquals(song(4), stuck.advance(manual = false, gone, Random(0)).playlist.current)
    }

    @Test
    fun `with every file gone the list has ended`() {
        val step = list(RepeatMode.ALL).advance(manual = true, refs.toSet(), Random(0))
        assertTrue(step.ended)
        assertEquals(list(RepeatMode.ALL), step.playlist)
        assertTrue(Playlist().advance(manual = true, none, Random(0)).ended)
    }

    // ------------------------------------------------------------------ shuffle, on a seeded random

    @Test
    fun `shuffle keeps the song playing first and deals the rest`() {
        val shuffled = list().select(song(3).ref).withShuffle(true, Random(42))
        assertEquals(song(3).ref, shuffled.shuffleOrder.first())
        assertEquals(refs.toSet(), shuffled.shuffleOrder.toSet())
        assertEquals(refs.size, shuffled.shuffleOrder.size)
        assertEquals(1, shuffled.position)
        // The host's order is untouched
        assertEquals(refs, shuffled.tracks.map { it.ref })
    }

    @Test
    fun `the same seed deals the same order, and other seeds other orders`() {
        val ten = Playlist().add((1..10).map(::song), Random(0))
        val a = ten.withShuffle(true, Random(7)).shuffleOrder
        val b = ten.withShuffle(true, Random(7)).shuffleOrder
        assertEquals(a, b)
        val others = (1..20).map { seed -> ten.withShuffle(true, Random(seed)).shuffleOrder }.toSet()
        assertTrue(others.size > 10, "20 seeds dealt only ${others.size} orders")
    }

    @Test
    fun `a shuffled pass plays every song once, then deals a new order`() {
        val random = Random(1234)
        var playlist = list(RepeatMode.ALL).withShuffle(true, random)
        val firstPass = mutableListOf(playlist.currentRef!!)
        repeat(4) {
            playlist = playlist.advance(manual = false, none, random).playlist
            firstPass += playlist.currentRef!!
        }
        assertEquals(refs.toSet(), firstPass.toSet())
        assertEquals(playlist.shuffleOrder, firstPass)

        val next = playlist.advance(manual = false, none, random).playlist
        // A new pass: every song again, and never the last song twice in a row
        assertEquals(refs.toSet(), next.shuffleOrder.toSet())
        assertNotEquals(firstPass.last(), next.currentRef)
        assertEquals(next.shuffleOrder.first(), next.currentRef)
    }

    @Test
    fun `no song plays twice in a row across passes, whatever the seed`() {
        (0 until 200).forEach { seed ->
            val random = Random(seed)
            var playlist = Playlist(repeat = RepeatMode.ALL)
                .add(listOf(song(1), song(2), song(3)), random)
                .withShuffle(true, random)
            var last = playlist.currentRef
            repeat(9) {
                playlist = playlist.advance(manual = false, none, random).playlist
                assertNotEquals(last, playlist.currentRef, "seed $seed")
                last = playlist.currentRef
            }
        }
    }

    @Test
    fun `songs added while shuffled come later in this pass`() {
        val random = Random(99)
        val playing = list().withShuffle(true, random).let { it.select(it.shuffleOrder[2]) }
        val added = playing.add(listOf(song(6), song(7)), random)
        val order = added.shuffleOrder
        assertEquals(playing.shuffleOrder.take(3), order.take(3)) // what has played stays put
        assertTrue(order.indexOf(song(6).ref) > 2)
        assertTrue(order.indexOf(song(7).ref) > 2)
        assertEquals(7, order.toSet().size)
    }

    @Test
    fun `shuffle off goes back to the host's order, same song playing`() {
        val shuffled = list().select(song(4).ref).withShuffle(true, Random(5))
        val plain = shuffled.withShuffle(false, Random(5))
        assertEquals(refs, plain.playOrder)
        assertEquals(song(4), plain.current)
        assertTrue(plain.shuffleOrder.isEmpty())
    }

    @Test
    fun `a playlist read back with parts that disagree is put right`() {
        val broken = Playlist(
            tracks = listOf(song(1), song(2), song(1), song(3)),
            currentRef = "content://music/gone",
            shuffle = true,
            shuffleOrder = listOf(song(3).ref, "content://music/gone", song(3).ref),
        ).normalized()
        assertEquals(listOf(song(1), song(2), song(3)), broken.tracks)
        assertEquals(listOf(song(3).ref, song(1).ref, song(2).ref), broken.shuffleOrder)
        assertEquals(song(3), broken.current)
    }

    @Test
    fun `the repeat button goes off, all, one, off`() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }
}
