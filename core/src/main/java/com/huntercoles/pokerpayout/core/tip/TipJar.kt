package com.huntercoles.pokerpayout.core.tip

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the "Tip the dealer?" card stands (PP-112, rules in [TipAsk]), saved in a file of its own
 * (`tip_prefs`) the moment anything changes, so a card shows again after process death and an answer
 * holds for good. Written on a save or a tap, never per tick.
 *
 * The file stays with this phone: the in-app backup leaves it out (`BackupCatalog.OWNER_FILES`),
 * so restoring an older backup can never bring back a card the host said no to, and a backup opened
 * on a friend's phone neither asks them nor silences them. Android's own backup, the same person's
 * phone moved to a new one, takes it along.
 *
 * The keys at the foot of this file are saved data: never rename one.
 */
@Singleton
class TipJar @Inject constructor(
    @ApplicationContext context: Context,
    private val timer: TimerPreferences,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * True when no earlier run of the app kept this file: a new install, or the first start of the
     * version that brought the card. The card never shows then, however many nights are saved.
     */
    val firstRun: Boolean = !prefs.getBoolean(STARTED_KEY, false)

    private val _ask = MutableStateFlow(read())

    /** The card's counters and its answer, as saved. */
    val ask: StateFlow<TipAsk> = _ask.asStateFlow()

    /** The saved night the card shows under, or null; never while the clock runs. */
    val cardNight: Flow<Long?> = combine(_ask, timer.timerRunning) { ask, running -> ask.cardNight.takeUnless { running } }

    init {
        if (firstRun) prefs.edit().putBoolean(STARTED_KEY, true).apply()
    }

    /** As [cardNight], now. */
    fun cardNightNow(): Long? = _ask.value.cardNight.takeUnless { timer.getTimerRunning() }

    /** The host saved night [nightId] from the Payouts tab: the card may come with it. */
    fun nightSaved(nightId: Long) =
        save(_ask.value.afterSave(nightId, clockRunning = timer.getTimerRunning(), firstRun = firstRun))

    /** "Not now" on the card. */
    fun notNow() = save(_ask.value.notNow())

    /** "Don't ask again" on the card, "Leave a tip", or a tip method used on the Tip the dealer page. */
    fun stopAsking() {
        if (!_ask.value.stopped || _ask.value.cardNight != null) save(_ask.value.stop())
    }

    private fun read(): TipAsk = TipAsk(
        nightsSaved = prefs.getInt(NIGHTS_SAVED_KEY, 0),
        asksShown = prefs.getInt(ASKS_SHOWN_KEY, 0),
        nextAskAt = prefs.getInt(NEXT_ASK_AT_KEY, TipAsk.FIRST_ASK_AT),
        stopped = prefs.getBoolean(STOPPED_KEY, false),
        cardNight = if (prefs.contains(CARD_NIGHT_KEY)) prefs.getLong(CARD_NIGHT_KEY, 0L) else null,
    )

    private fun save(ask: TipAsk) {
        val editor = prefs.edit()
            .putInt(NIGHTS_SAVED_KEY, ask.nightsSaved)
            .putInt(ASKS_SHOWN_KEY, ask.asksShown)
            .putInt(NEXT_ASK_AT_KEY, ask.nextAskAt)
            .putBoolean(STOPPED_KEY, ask.stopped)
        ask.cardNight?.let { editor.putLong(CARD_NIGHT_KEY, it) } ?: editor.remove(CARD_NIGHT_KEY)
        editor.apply()
        _ask.value = ask
    }

    companion object {
        const val FILE = "tip_prefs"

        // The saved format (PP-112): never rename these.
        private const val STARTED_KEY = "started"
        private const val NIGHTS_SAVED_KEY = "nights_saved"
        private const val ASKS_SHOWN_KEY = "asks_shown"
        private const val NEXT_ASK_AT_KEY = "next_ask_at"
        private const val STOPPED_KEY = "stopped"
        private const val CARD_NIGHT_KEY = "card_night"

        /** Every key the file holds. */
        val KEYS: Set<String> get() =
            setOf(STARTED_KEY, NIGHTS_SAVED_KEY, ASKS_SHOWN_KEY, NEXT_ASK_AT_KEY, STOPPED_KEY, CARD_NIGHT_KEY)
    }
}
