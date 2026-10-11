package com.huntercoles.pokerpayout.tournament.domain.moments

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences

/**
 * PP-111: the night's milestones, which the clock marks as the knockouts bring them. Declared from
 * the least to the biggest: when one knockout reaches two at once (2 paid: heads-up is also in the
 * money), the clock shows the bigger one.
 */
enum class BigMoment(val cue: CueEvent) {
    /** One more out, then everyone left is paid. */
    BUBBLE(CueEvent.BIG_MOMENT),

    /** Everyone left fits one table, in a night that started on two or more. */
    FINAL_TABLE(CueEvent.BIG_MOMENT),

    /** The bubble has burst: everyone left is paid. */
    IN_THE_MONEY(CueEvent.BIG_MOMENT),

    /** Two left. */
    HEADS_UP(CueEvent.BIG_MOMENT),

    /** One left: the champion. */
    CHAMPION(CueEvent.CHAMPION),
}

/**
 * Where the field stands: [players] in the night, [left] still in, [paid] places paid (from the one
 * payout calculation) and [seatsPerTable] (the seat draw's tables, core's `TableSeats`).
 */
data class Field(val players: Int, val left: Int, val paid: Int, val seatsPerTable: Int)

/**
 * Which moments a field has reached. A moment counts only once the knockouts have brought the field
 * to it: one the night started at (four players, three paid: the bubble from the first deal; a
 * single table: the final table) is no moment. Bounty types, re-entries and rebuys don't matter
 * here: only who is still in, against the places paid.
 */
object BigMoments {
    fun reached(field: Field): Set<BigMoment> = buildSet {
        fun at(moment: BigMoment, left: Int) {
            if (left in 1 until field.players && field.left <= left) add(moment)
        }
        if (field.paid > 0) {
            at(BigMoment.BUBBLE, field.paid + 1)
            at(BigMoment.IN_THE_MONEY, field.paid)
        }
        at(BigMoment.FINAL_TABLE, field.seatsPerTable)
        at(BigMoment.HEADS_UP, HEADS_UP_LEFT)
        at(BigMoment.CHAMPION, 1)
    }

    /** The one to show of [moments]: the biggest. */
    fun headline(moments: Set<BigMoment>): BigMoment? = moments.maxOrNull()

    private const val HEADS_UP_LEFT = 2
}

/**
 * Marks each moment once (PP-111). Each moment the clock marks is saved with how many entries were
 * out then, and so is the count at the clock's last look. Only a knockout (more out than at the last
 * look) brings a moment: the ones it newly reaches. Only someone coming back in (fewer out: an Undo
 * of a knockout, Bring back) takes moments away: those marked with more out than now, so recorded
 * again they show again. A late arrival or a re-entry (a new entry; nobody out comes back) moves the
 * field but neither brings nor takes a moment, so the next knockouts don't show the bubble or the
 * final table a second time. A restart, process death included, marks nothing again.
 */
class MomentTracker(private val preferences: TimerPreferences) {

    /** What [field] newly brings since the last look, everything it reaches, and what stays marked; saves the marks. */
    fun look(field: Field): MomentLook {
        val reached = BigMoments.reached(field)
        val out = field.players - field.left
        val saved = preferences.getBigMomentsMarked()?.let(::parse)
        val savedOut = preferences.getBigMomentsOut()
        val fresh = if (saved != null && out > savedOut) reached - saved.keys else emptySet()
        val marked = when {
            // Before the first look (a fresh install, an update mid-game) whatever is reached is old news
            saved == null -> reached.associateWith { out }
            out < savedOut -> saved.filterValues { it <= out }
            else -> saved + (reached - saved.keys).associateWith { out }
        }
        // Written only when it changes, never per tick
        if (marked != saved || out != savedOut) preferences.setBigMoments(marked.mapKeys { it.key.name }, out)
        return MomentLook(fresh = fresh, reached = reached, marked = marked.keys)
    }

    private fun parse(saved: Map<String, Int>): Map<BigMoment, Int> =
        saved.mapNotNull { (name, at) -> BigMoment.entries.firstOrNull { it.name == name }?.let { it to at } }.toMap()
}

/**
 * One look at the field: the moments [fresh] since the last look, all those it [reached], and all
 * those [marked] (a re-entry can leave a moment marked that the field no longer reaches).
 */
data class MomentLook(val fresh: Set<BigMoment>, val reached: Set<BigMoment>, val marked: Set<BigMoment>) {
    /** The moment to show for this look, if any. */
    val headline: BigMoment? get() = BigMoments.headline(fresh)
}
