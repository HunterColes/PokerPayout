package com.huntercoles.pokerpayout.core.domain.players

import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The regulars as the screens use them (PP-110): the [roster] from the saved nights and the names
 * the Bank has used, with the merges made in History; and the Bank's names to [remember], dated today
 * on the phone's clock.
 */
class Regulars @Inject constructor(
    private val nights: NightStore,
    private val store: RegularsStore,
    private val time: TimeSource,
) {
    /** Everyone the host has played with, regulars first ([Roster]); follows every change to what it's made of. */
    val roster: Flow<List<Regular>>
        get() = combine(nights.nights, store.players, store.merges) { saved, known, merges -> Roster.of(saved, known, merges) }

    val merges: StateFlow<PlayerMerges> get() = store.merges

    /** The roster as it stands. */
    fun current(): List<Regular> = Roster.of(nights.nights.value, store.players.value, store.merges.value)

    /** The Bank used [names] today: typed in a row, or picked for tonight. */
    fun remember(vararg names: String) = store.rememberAll(names.toList(), today())

    /** Today on the phone's clock and in its time zone. */
    fun today(): LocalDate = Instant.ofEpochMilli(time.wallClockMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
}
