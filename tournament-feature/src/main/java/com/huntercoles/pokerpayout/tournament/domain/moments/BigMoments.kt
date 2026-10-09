package com.huntercoles.pokerpayout.tournament.domain.moments

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.tools.seats.SeatDrawStore
import javax.inject.Inject

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
 * payout calculation) and [seatsPerTable] (the seat draw's tables).
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
 * Marks each moment once (PP-111). The moments the field had reached at the clock's last look are
 * saved, with how many players were out then. A moment is new when it is reached now and wasn't
 * then, and a knockout brought it: more players are out than at the last look. So a restart
 * (process death included) marks nothing again; an Undo takes a moment back, and the knockout
 * recorded again marks it again; and a late arrival or a change of tables that moves the field past
 * a line without anyone going out marks nothing.
 */
class MomentTracker(private val preferences: TimerPreferences) {

    /** What [field] newly reaches since the last look, and everything it reaches; saves the latter. */
    fun look(field: Field): MomentLook {
        val reached = BigMoments.reached(field)
        val out = field.players - field.left
        val saved = preferences.getBigMomentsReached()?.let(::parse)
        val savedOut = preferences.getBigMomentsOut()
        // Written only when it changes, never per tick
        if (saved != reached || savedOut != out) preferences.setBigMoments(reached.map { it.name }.toSet(), out)
        // Before the first look (a fresh install, or an update mid-game) whatever is reached is old news
        val fresh = if (saved != null && out > savedOut) reached - saved else emptySet()
        return MomentLook(fresh = fresh, reached = reached)
    }

    private fun parse(names: Set<String>): Set<BigMoment> =
        names.mapNotNull { name -> BigMoment.entries.firstOrNull { it.name == name } }.toSet()
}

/** One look at the field: the moments [fresh] since the last look, and all those [reached]. */
data class MomentLook(val fresh: Set<BigMoment>, val reached: Set<BigMoment>) {
    /** The moment to show for this look, if any. */
    val headline: BigMoment? get() = BigMoments.headline(fresh)
}

/** How many seats a table has, for the final table: the seat draw's setting (Tools > Seat draw). */
fun interface TableSeats {
    fun seatsPerTable(): Int
}

/** The seat draw's tables (9 seats until the host changes it there). */
class SeatDrawTableSeats @Inject constructor(private val store: SeatDrawStore) : TableSeats {
    override fun seatsPerTable(): Int = store.seatsPerTable()
}
