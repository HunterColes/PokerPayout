package com.huntercoles.pokerpayout.core.domain.players

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Two spellings of one person (PP-110): a spelling counts as the name kept, as names match elsewhere
 * (trimmed, ignoring case); every spelling points straight at the name kept, so merging the kept name
 * on moves its spellings with it; a merge made twice is made once; Separate gives a spelling back;
 * and merges from another phone add without ever making a loop.
 */
class PlayerMergesTest {

    private val mike = PlayerMerges.NONE.merge("Mike R.", "Mike")

    @Test
    fun `a merged spelling counts as the name kept, in any case and spacing`() {
        assertEquals("Mike", mike.resolve("Mike R."))
        assertEquals("Mike", mike.resolve("  mike r. "))
        assertEquals("Dana", mike.resolve(" Dana "))
        assertTrue(mike.same("MIKE R.", "mike"))
        assertFalse(mike.same("Mike", "Dana"))
        assertEquals("mike", mike.key("Mike R."))
        assertEquals(listOf("Mike R."), mike.aliasesOf("Mike"))
        assertEquals(listOf("Mike R."), mike.aliasesOf("mike r."))
        assertEquals(emptyList(), mike.aliasesOf("Dana"))
    }

    @Test
    fun `a merge made twice, or between one person's names, changes nothing`() {
        assertEquals(mike, mike.merge("Mike R.", "Mike"))
        assertEquals(mike, mike.merge("mike r.", "MIKE"))
        assertSame(mike, mike.merge("Mike", "Mike R."))
        assertSame(PlayerMerges.NONE, PlayerMerges.NONE.merge("Dana", " dana"))
        assertSame(PlayerMerges.NONE, PlayerMerges.NONE.merge("", "Dana"))
    }

    @Test
    fun `merging the kept name on takes its spellings along, and nothing points at a spelling`() {
        val merges = mike.merge("Mikey", "Mike").merge("Mike", "Michael")
        assertEquals("Michael", merges.resolve("Mike R."))
        assertEquals("Michael", merges.resolve("Mikey"))
        assertEquals("Michael", merges.resolve("Mike"))
        assertEquals(setOf("Mike R.", "Mikey", "Mike"), merges.aliasesOf("Michael").toSet())
        assertTrue(merges.all.all { merge -> merges.all.none { it.from.equals(merge.into, ignoreCase = true) } })
    }

    @Test
    fun `merging a spelling merges the person it counts as`() {
        // "Mike R." is Mike already: merging it into Michael moves Mike, and Mike R. with him
        val merges = mike.merge("Mike R.", "Michael")
        assertEquals("Michael", merges.resolve("Mike"))
        assertEquals("Michael", merges.resolve("Mike R."))
    }

    @Test
    fun `separate gives a spelling its own name back, and only that one`() {
        val merges = mike.merge("Mikey", "Mike")
        val separated = merges.separate("mike r.")
        assertEquals("Mike R.", separated.resolve("Mike R."))
        assertEquals("Mike", separated.resolve("Mikey"))
        assertSame(separated, separated.separate("Dana"))
        assertEquals(PlayerMerges.NONE, separated.separate("Mikey"))
    }

    @Test
    fun `merges from another phone add, but never undo or loop one made here`() {
        val here = PlayerMerges.NONE.merge("Danny", "Dan")
        val there = PlayerMerges.of(listOf(Merge("Dan", "Danny"), Merge("Jo", "Joanne"), Merge("Danny", "Daniel")))
        val both = here + there
        assertEquals("Dan", both.resolve("Danny"))
        assertEquals("Joanne", both.resolve("Jo"))
        assertEquals(both, both + there)
        assertEquals(here, here + PlayerMerges.NONE)
    }

    @Test
    fun `a damaged file's loop is read without hanging`() {
        val loop = PlayerMerges.of(listOf(Merge("A", "B"), Merge("B", "C"), Merge("C", "A")))
        assertTrue(loop.resolve("A") in setOf("A", "B", "C"))
        assertTrue(loop.same("B", "B"))
        assertTrue(loop.aliasesOf("C").size <= 3)
        // Merging on from it never adds to the loop
        assertEquals("Ann", loop.merge("A", "Ann").resolve("A"))
    }

    @Test
    fun `reading drops merges of a name into itself or of a blank name, and the later of two for one spelling wins`() {
        val merges = PlayerMerges.of(
            listOf(Merge("Dana", " dana "), Merge(" ", "Sam"), Merge("Jo", ""), Merge("Jo", "Joe"), Merge("jo", "Joanne")),
        )
        assertEquals(listOf(Merge("jo", "Joanne")), merges.all)
        assertTrue(PlayerMerges.of(emptyList()).isEmpty)
    }
}
