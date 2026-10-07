package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.presets.CurrentSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetStore
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Saves a finished night to History (PP-037) from the Payouts tab: dated today on the phone's clock,
 * and named after the preset the setup still matches, the last used first (none when it matches none).
 */
class NightRecorder @Inject constructor(
    private val store: NightStore,
    private val presets: PresetStore,
    private val setup: CurrentSetup,
    private val time: TimeSource,
) {
    val nights: StateFlow<List<SavedNight>> get() = store.nights

    /** True when History already holds a night with exactly these results. */
    fun isSaved(players: List<NightPlayer>, prizePoolCents: Long): Boolean =
        store.nights.value.any { it.prizePoolCents == prizePoolCents && it.players == players }

    fun save(players: List<NightPlayer>, prizePoolCents: Long): SavedNight = store.add(
        SavedNight(
            id = 0L,
            date = today(),
            structureName = presets.presets.value.firstOrNull { setup.matches(it.setup) }?.name,
            prizePoolCents = prizePoolCents,
            players = players,
        ),
    )

    /** Today on the phone's clock and in its time zone. */
    private fun today(): LocalDate = Instant.ofEpochMilli(time.wallClockMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
}
