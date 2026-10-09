package com.huntercoles.pokerpayout.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.core.utils.ChipSetProvider
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCueTimes
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSaves
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tournament clock (PP-015).
 *
 * Time is never counted: the clock is a [ClockAnchor] (play time at an instant, plus the monotonic
 * clock's reading then), and every tick derives play time from [TimeSource.elapsedRealtimeMillis].
 * The tick loop only decides when to look; a late, skipped or sleep-delayed tick can't make the clock
 * drift. The anchor is saved when the clock starts, pauses, jumps, is nudged, finishes or resets, so a
 * killed process resumes exactly where the clock would be, overtime included.
 *
 * Color-ups use your chip set once it is set up ([ChipSetProvider], PP-091 #9); a change to it
 * re-plans them and leaves the clock where it is.
 * The saved anchor is the one clock (PP-081): the live clock notification's Pause and Resume change
 * it too, and this follows ([followSavedClock]) through the same steps as its own button.
 */
@HiltViewModel
@Suppress("LongParameterList") // one injected source per thing the clock reads
class TimerViewModel @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences,
    private val bankPreferences: BankPreferences,
    private val cues: ClockCues,
    private val timeSource: TimeSource,
    private val audioPreferences: AudioPreferences,
    private val chipSets: ChipSetProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    /** PP-083: the gold flash at a level change or with one minute left, for the clock's screen. */
    val flashes: Flow<SilentCue> = cues.flashes

    private val saves = ClockSaves(timerPreferences, tournamentPreferences)

    private var anchor = ClockAnchor()
    private var tickJob: Job? = null

    /** While paused, the projected end time moves with the wall clock; this keeps it current. */
    private var wallJob: Job? = null

    /** Play time at the previous look, to detect sound cues crossed since. */
    private var lastSeenMillis = 0L

    private var tableConfig = tournamentPreferences.getCurrentTournamentConfig()
    private var bank = BankCounts()

    /** The one payout calculation (stateless), for how many places are paid. */
    private val payouts = CalculatePayoutsUseCase()

    /** The chip set the color-ups follow; null for a common home set's chips. */
    private var chipSet: ChipSetChips? = chipSets.current()

    init {
        cues.preload()
        normalizeStoredSmallestChip()
        restore()
        observeTable()
        observeSettings()
        observeChipSet()
        observeSavedClock()
    }

    /** PP-081: true at most once ever, the first Start asking for the live clock's notification. */
    fun takeNotificationsAsk(): Boolean = timerPreferences.takeNotificationsAsk()

    fun acceptIntent(intent: TimerIntent) {
        when (intent) {
            TimerIntent.ToggleTimer -> toggleTimer()
            TimerIntent.ResetTimer -> resetTimer()
            TimerIntent.NextBlindLevel -> jumpToSegment(_uiState.value.currentSegmentIndex + 1)
            TimerIntent.PreviousBlindLevel -> jumpToSegment(_uiState.value.currentSegmentIndex - 1)
            is TimerIntent.NudgeMinutes -> nudge(intent.minutes)
            TimerIntent.EndBreakNow -> endBreakNow()
            is TimerIntent.MarkColorUpDone -> markColorUpDone(intent.done)
            else -> acceptViewIntent(intent)
        }
    }

    private fun acceptViewIntent(intent: TimerIntent) {
        when (intent) {
            TimerIntent.ShowInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = true) }
            TimerIntent.HideInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = false) }
            is TimerIntent.SetTableView -> _uiState.update { it.copy(isTableView = intent.enabled) }
            TimerIntent.ToggleMute -> audioPreferences.toggleMute()
            is TimerIntent.UpdateRebuyUntil -> tournamentPreferences.setRebuyUntilLevel(intent.level)
            is TimerIntent.ApplyFix -> applyFix(intent.fix)
            is TimerIntent.KeepingLevel -> changeKeepingLevel(intent.edit)
            else -> acceptSetupIntent(intent)
        }
    }

    private fun acceptSetupIntent(intent: TimerIntent) {
        blindChange(intent)?.let { changeSetup(it) }
        when (intent) {
            is TimerIntent.UpdateBreakEvery -> changeBreaks {
                it.copy(everyLevels = intent.levels.coerceIn(0, MAX_BREAK_EVERY))
            }
            is TimerIntent.UpdateBreakLength -> changeBreaks {
                it.copy(lengthMinutes = intent.minutes.coerceIn(1, MAX_BREAK_MINUTES))
            }
            is TimerIntent.UpdateBreakMessage -> changeBreaks { it.copy(message = intent.message.take(MAX_NOTE)) }
            is TimerIntent.UpdateBigBlindAnte -> changeKeepingPosition {
                it.copy(bigBlindAnteFromLevel = intent.fromLevel.coerceAtLeast(0))
            }
            else -> Unit
        }
    }

    /** The change a blind setup intent makes to the levels, or null for any other intent. */
    private fun blindChange(intent: TimerIntent): ((BlindConfiguration) -> BlindConfiguration)? = when (intent) {
        is TimerIntent.GameDurationHoursChanged -> { config ->
            config.copy(gameDurationMinutes = intent.hours.coerceIn(1, MAX_HOURS) * MINUTES_PER_HOUR)
        }
        is TimerIntent.UpdateSmallestChip -> { config -> config.copy(smallestChip = intent.value.coerceAtLeast(1)) }
        is TimerIntent.UpdateStartingChips -> { config -> config.copy(startingChips = intent.value.coerceAtLeast(1)) }
        is TimerIntent.UpdateRoundLength -> { config -> config.copy(roundLengthMinutes = intent.minutes.coerceAtLeast(1)) }
        is TimerIntent.ApplyFix -> when (val fix = intent.fix) {
            is BlindSetupFix.UseRoundLength -> { config -> config.copy(roundLengthMinutes = fix.minutes) }
            is BlindSetupFix.UseStartingChips -> { config -> config.copy(startingChips = fix.chips) }
        }
        else -> null
    }

    private fun changeBreaks(transform: (BreakSettings) -> BreakSettings) =
        changeKeepingPosition { it.copy(breaks = transform(it.breaks)) }

    // ------------------------------------------------------------------ restore

    private fun restore() {
        val hasStarted = timerPreferences.getHasTimerStarted()
        _uiState.update {
            it.copy(
                config = loadConfig(frozen = hasStarted),
                hasTimerStarted = hasStarted,
                isFinished = timerPreferences.getIsFinished(),
                colorUpDoneAfterLevels = timerPreferences.getColorUpDoneAfterLevels()
            )
        }
        rebuildSchedule()
        anchor = timerPreferences.getClock() ?: migrateLegacyClock()
        val elapsed = anchor.elapsedAt(timeSource)
        lastSeenMillis = elapsed

        val state = _uiState.value
        if (anchor.running && !state.isFinished && !state.timeline.isEmpty) {
            _uiState.update { it.copy(isRunning = true) }
            tournamentPreferences.setTournamentLocked(true)
            show(elapsed, playCues = false)
            if (_uiState.value.isRunning) startTicking()
        } else {
            if (anchor.running) {
                anchor = ClockAnchor.stopped(elapsed)
                timerPreferences.saveClock(anchor)
            }
            show(elapsed, playCues = false)
            followWallClockWhilePaused()
        }
    }

    /** A v1.1.x clock (remaining seconds + wall time) becomes an anchor once, keeping any overtime. */
    private fun migrateLegacyClock(): ClockAnchor {
        val legacy = timerPreferences.getLegacyClock() ?: return ClockAnchor()
        val sinceSaved = if (legacy.running) {
            (timeSource.wallClockMillis() - legacy.savedAtWallMillis).coerceAtLeast(0)
        } else {
            0L
        }
        val elapsed = legacy.elapsedSeconds * MILLIS_PER_SECOND + sinceSaved
        val migrated = anchorAt(elapsed, running = legacy.running)
        timerPreferences.saveClock(migrated)
        return migrated
    }

    private fun loadConfig(frozen: Boolean) = BlindConfiguration(
        gameDurationMinutes = timerPreferences.getGameDurationMinutes(),
        roundLengthMinutes = if (frozen) {
            timerPreferences.getRoundLengthAtStart()
        } else {
            tournamentPreferences.getRoundLengthMinutes()
        },
        smallestChip = if (frozen) {
            timerPreferences.getSmallestChipAtStart()
        } else {
            tournamentPreferences.getSmallestChip()
        },
        startingChips = if (frozen) {
            timerPreferences.getStartingChipsAtStart()
        } else {
            tournamentPreferences.getStartingChips()
        },
        breaks = BreakSettings(
            everyLevels = timerPreferences.getBreakEveryLevels(),
            lengthMinutes = timerPreferences.getBreakLengthMinutes(),
            message = timerPreferences.getBreakMessage()
        ),
        bigBlindAnteFromLevel = timerPreferences.getBigBlindAnteFromLevel()
    )

    /** PP-051: the smallest chip is now picked from real chips; map an old free-entry value onto one. */
    private fun normalizeStoredSmallestChip() {
        val stored = tournamentPreferences.getSmallestChip()
        val normalized = SmallestChipChoices.normalize(stored)
        if (normalized != stored) tournamentPreferences.setSmallestChip(normalized)
    }

    // ------------------------------------------------------------------ the clock

    private fun toggleTimer() {
        val state = _uiState.value
        when {
            state.isFinished -> Unit
            state.isRunning -> pause()
            !isValidBlindConfiguration(state) -> _uiState.update { it.copy(showInvalidConfigDialog = true) }
            else -> start()
        }
    }

    private fun start() {
        markStarted()
        runFrom(ClockAnchor.runningFrom(anchor.elapsedAt(timeSource), timeSource))
    }

    /** Runs the clock from [running]: Start and Resume here, or Resume on the notification. */
    private fun runFrom(running: ClockAnchor) {
        stopFollowingWallClock()
        anchor = running
        saves.resumed(anchor)
        lastSeenMillis = running.elapsedMillis
        _uiState.update { it.copy(isRunning = true, isFinished = false) }
        startTicking()
    }

    private fun pause() = stopAt(ClockAnchor.stopped(anchor.elapsedAt(timeSource)))

    /** Stops the clock at [stopped]: Pause here, or Pause on the notification. */
    private fun stopAt(stopped: ClockAnchor) {
        tickJob?.cancel()
        tickJob = null
        anchor = stopped
        saves.paused(anchor)
        _uiState.update { it.copy(isRunning = false) }
        show(stopped.elapsedMillis, playCues = false)
        followWallClockWhilePaused()
    }

    /**
     * PP-081: the saved clock's running flag changed. When this clock saved it, the saved anchor is
     * this one and nothing happens; when the notification's Pause or Resume did, this clock takes it
     * up through the same steps as its own button.
     */
    private fun observeSavedClock() {
        viewModelScope.launch {
            timerPreferences.timerRunning.collect { followSavedClock() }
        }
    }

    private fun followSavedClock() {
        val saved = timerPreferences.getClock()
        val state = _uiState.value
        val live = state.hasTimerStarted && !state.isFinished
        when {
            saved == null || saved == anchor || !live -> Unit
            anchor.running && !saved.running -> stopAt(saved)
            !anchor.running && saved.running -> runFrom(saved)
        }
    }

    /** The first start (or jump) freezes the setup so the schedule survives until reset. */
    private fun markStarted() {
        if (_uiState.value.hasTimerStarted) return
        freezeSetup(_uiState.value.config)
        timerPreferences.setHasTimerStarted(true)
        timerPreferences.setIsFinished(false)
        _uiState.update { it.copy(hasTimerStarted = true) }
    }

    private fun freezeSetup(config: BlindConfiguration) {
        timerPreferences.setSmallestChipAtStart(config.smallestChip)
        timerPreferences.setStartingChipsAtStart(config.startingChips)
        timerPreferences.setRoundLengthAtStart(config.roundLengthMinutes)
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = anchor.elapsedAt(timeSource)
                show(elapsed, playCues = true)
                if (!_uiState.value.isRunning) break
                // Wake at the next whole second of play; the time shown is derived, not counted.
                delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
            }
        }
    }

    /**
     * While the clock is started but paused, the projected end slides with the wall clock: look once
     * a minute (on the minute) so "ends about 11:10" stays true. Reads only; writes nothing.
     */
    private fun followWallClockWhilePaused() {
        wallJob?.cancel()
        val state = _uiState.value
        if (!state.hasTimerStarted || state.isRunning || state.isFinished) return
        wallJob = viewModelScope.launch {
            while (isActive) {
                delay(MILLIS_PER_MINUTE - timeSource.wallClockMillis() % MILLIS_PER_MINUTE)
                refreshEndTime(anchor.elapsedAt(timeSource))
            }
        }
    }

    private fun stopFollowingWallClock() {
        wallJob?.cancel()
        wallJob = null
    }

    /** Shows play time [elapsedMillis], plays any cue crossed since the last look, and finishes at the end. */
    private fun show(elapsedMillis: Long, playCues: Boolean) {
        val state = _uiState.value
        val timeline = state.timeline
        // The chime, and the quiet cues (PP-083), for every cue passed since the last look
        if (playCues) cues.play(ClockCueTimes.crossed(timeline, lastSeenMillis, elapsedMillis))
        lastSeenMillis = elapsedMillis

        val endMillis = timeline.endSeconds * MILLIS_PER_SECOND
        if (state.isRunning && !timeline.isEmpty && elapsedMillis >= endMillis) {
            finish(timeline.endSeconds)
            return
        }
        val seconds = (elapsedMillis / MILLIS_PER_SECOND).toInt()
        if (seconds != state.elapsedSeconds) _uiState.update { it.copy(elapsedSeconds = seconds) }
        refreshEndTime(elapsedMillis)
    }

    /** "Tournament ends about 11:10": now plus the scheduled play left, to the second. */
    private fun refreshEndTime(elapsedMillis: Long) {
        val state = _uiState.value
        val regularEndMillis = state.timeline.regularEndSeconds * MILLIS_PER_SECOND
        val endsAt = if (state.isFinished || state.timeline.isEmpty || elapsedMillis >= regularEndMillis) {
            null
        } else {
            val exact = timeSource.wallClockMillis() + regularEndMillis - elapsedMillis
            (exact + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND * MILLIS_PER_SECOND
        }
        if (endsAt != state.endsAtWallClock) _uiState.update { it.copy(endsAtWallClock = endsAt) }
    }

    private fun finish(endSeconds: Int) {
        tickJob?.cancel()
        tickJob = null
        stopFollowingWallClock()
        anchor = ClockAnchor.stopped(endSeconds * MILLIS_PER_SECOND)
        timerPreferences.saveClock(anchor)
        timerPreferences.setIsFinished(true)
        lastSeenMillis = anchor.elapsedMillis
        _uiState.update { it.copy(isRunning = false, isFinished = true, elapsedSeconds = endSeconds, endsAtWallClock = null) }
    }

    private fun jumpToSegment(index: Int) {
        val state = _uiState.value
        val target = state.timeline.segments.getOrNull(index) ?: return
        markStarted()
        val elapsed = target.startSeconds * MILLIS_PER_SECOND
        anchor = anchorAt(elapsed, running = anchor.running)
        timerPreferences.saveClock(anchor)
        timerPreferences.setIsFinished(false)
        lastSeenMillis = elapsed
        _uiState.update { it.copy(isFinished = false, elapsedSeconds = target.startSeconds) }
        refreshEndTime(elapsed)
        followWallClockWhilePaused()
    }

    /**
     * D5: ± [minutes] of time left in the current level or break. Adding time can't go past the
     * segment's full length; taking it off stops a second before the end, so the change (and its
     * chime, if the clock is running) still happens. Saved like any jump.
     */
    private fun nudge(minutes: Int) {
        val state = _uiState.value
        val segment = state.currentSegment
        val live = state.hasTimerStarted && !state.isFinished
        if (segment == null || !live || minutes == 0) return
        val elapsed = anchor.elapsedAt(timeSource)
        val first = segment.startSeconds * MILLIS_PER_SECOND
        val last = (segment.endSeconds * MILLIS_PER_SECOND - MILLIS_PER_SECOND).coerceAtLeast(first)
        val target = (elapsed - minutes * MILLIS_PER_MINUTE).coerceIn(first, last)
        if (target == elapsed) return
        anchor = anchorAt(target, running = anchor.running)
        timerPreferences.saveClock(anchor)
        // Time added: the level's end (and its chime) are ahead again. Time taken off: a chime the
        // nudge skipped over is played by the next look, as when a tick runs late.
        if (target < elapsed) lastSeenMillis = target
        show(target, playCues = anchor.running)
    }

    private fun endBreakNow() {
        val state = _uiState.value
        if (state.isOnBreak && !state.isFinished) jumpToSegment(state.currentSegmentIndex + 1)
    }

    private fun markColorUpDone(done: Boolean) {
        val currentBreak = _uiState.value.currentBreak ?: return
        timerPreferences.setColorUpDone(currentBreak.afterLevel, done)
        _uiState.update { it.copy(colorUpDoneAfterLevels = timerPreferences.getColorUpDoneAfterLevels()) }
    }

    private fun resetTimer() {
        tickJob?.cancel()
        tickJob = null
        stopFollowingWallClock()
        anchor = ClockAnchor()
        lastSeenMillis = 0
        timerPreferences.resetTimer()
        tournamentPreferences.setTournamentLocked(false)
        _uiState.update {
            TimerUiState(
                config = loadConfig(frozen = false),
                table = it.table,
                rebuyUntilLevel = it.rebuyUntilLevel,
                purchases = it.purchases,
                isMuted = it.isMuted
            )
        }
        rebuildSchedule()
        refreshTable()
        refreshEndTime(0L)
    }

    private fun anchorAt(elapsedMillis: Long, running: Boolean) =
        if (running) ClockAnchor.runningFrom(elapsedMillis, timeSource) else ClockAnchor.stopped(elapsedMillis)

    override fun onCleared() {
        // The anchor is already saved; a running clock resumes from it in the next process.
        tickJob?.cancel()
        wallJob?.cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------------ setup

    /** A change to the levels themselves: back to a fresh clock, as before. */
    private fun changeSetup(transform: (BlindConfiguration) -> BlindConfiguration) {
        val old = _uiState.value.config
        val new = transform(old)
        if (new == old) return
        persist(new)
        if (_uiState.value.hasTimerStarted || anchor.elapsedMillis > 0) {
            tickJob?.cancel()
            tickJob = null
            stopFollowingWallClock()
            anchor = ClockAnchor()
            lastSeenMillis = 0
            timerPreferences.resetTimer()
            tournamentPreferences.setTournamentLocked(false)
        }
        _uiState.update {
            it.copy(
                config = new,
                elapsedSeconds = 0,
                isRunning = false,
                isFinished = false,
                hasTimerStarted = false,
                colorUpDoneAfterLevels = emptySet(),
                midGameProblem = null
            )
        }
        rebuildSchedule()
        refreshTable()
        refreshEndTime(0L)
    }

    /**
     * S1 v2, "Unlock to edit…": a blind change mid-game rebuilds the schedule and keeps the clock on
     * the same level (or break) with the same time left (cut to the new level length if that's
     * shorter). A change that can't be played isn't applied: [TimerUiState.midGameProblem] says why
     * and offers the fixes, and the clock runs on. Before the start it's an ordinary change.
     */
    private fun changeKeepingLevel(edit: TimerIntent) {
        val transform = blindChange(edit)
        val before = _uiState.value
        when {
            transform == null -> Unit
            !before.hasTimerStarted -> acceptViewIntent(edit)
            else -> rebuildKeepingLevel(before, transform(before.config))
        }
    }

    private fun rebuildKeepingLevel(before: TimerUiState, new: BlindConfiguration) {
        val (problem, levels) = scheduleFor(new)
        if (new == before.config || levels.isEmpty()) {
            _uiState.update { it.copy(midGameProblem = if (new == before.config) null else problem) }
            return
        }
        val elapsedBefore = anchor.elapsedAt(timeSource)
        val segment = before.currentSegment
        val leftMillis = segment?.let { it.endSeconds * MILLIS_PER_SECOND - elapsedBefore } ?: 0L
        persist(new)
        freezeSetup(new)
        _uiState.update { it.copy(config = new, midGameProblem = null) }
        rebuildSchedule()

        val target = segment?.let { sameSegmentIn(_uiState.value.timeline, it) }
        val elapsedAfter = target?.let { seg ->
            val duration = seg.durationSeconds * MILLIS_PER_SECOND
            seg.startSeconds * MILLIS_PER_SECOND + duration - leftMillis.coerceIn(1L, duration)
        } ?: elapsedBefore
        anchor = anchorAt(elapsedAfter, running = anchor.running)
        timerPreferences.saveClock(anchor)
        lastSeenMillis = elapsedAfter
        show(elapsedAfter, playCues = false)
        refreshTable()
    }

    /** [segment]'s place in a rebuilt [timeline]: the same level, or the break after the same level. */
    private fun sameSegmentIn(timeline: ClockTimeline, segment: ClockSegment): ClockSegment? {
        val levels = timeline.levels
        return when (segment) {
            is LevelSegment -> levels.getOrNull(segment.index) ?: levels.lastOrNull()
            is BreakSegment -> timeline.segments.filterIsInstance<BreakSegment>()
                .firstOrNull { it.afterLevel == segment.afterLevel }
                ?: levels.getOrNull(segment.afterLevel)
                ?: levels.lastOrNull()
        }
    }

    /**
     * Breaks, break text and antes don't change the levels, so the clock keeps its place: the same
     * level (or break), the same time into it.
     */
    private fun changeKeepingPosition(transform: (BlindConfiguration) -> BlindConfiguration) {
        val before = _uiState.value
        val new = transform(before.config)
        if (new == before.config) return
        persist(new)
        val elapsedBefore = anchor.elapsedAt(timeSource)
        val segment = before.currentSegment
        val into = segment?.let { elapsedBefore - it.startSeconds * MILLIS_PER_SECOND } ?: 0L
        _uiState.update { it.copy(config = new) }
        rebuildSchedule()

        val timeline = _uiState.value.timeline
        val elapsedAfter = when (segment) {
            null -> elapsedBefore
            is LevelSegment -> timeline.levels.getOrNull(segment.index)
                ?.let { it.startSeconds * MILLIS_PER_SECOND + into }
            is BreakSegment -> timeline.segments.filterIsInstance<BreakSegment>()
                .firstOrNull { it.afterLevel == segment.afterLevel }
                ?.let { breakAfter ->
                    val lastMillis = breakAfter.durationSeconds * MILLIS_PER_SECOND - 1
                    breakAfter.startSeconds * MILLIS_PER_SECOND + into.coerceAtMost(lastMillis)
                }
                ?: timeline.levels.getOrNull(segment.afterLevel)?.let { it.startSeconds * MILLIS_PER_SECOND }
        } ?: elapsedBefore
        if (elapsedAfter != elapsedBefore) {
            anchor = anchorAt(elapsedAfter, running = anchor.running)
            timerPreferences.saveClock(anchor)
            lastSeenMillis = elapsedAfter
        }
        show(elapsedAfter, playCues = false)
    }

    private fun applyFix(fix: BlindSetupFix) {
        _uiState.update { it.copy(showInvalidConfigDialog = false) }
        when (fix) {
            is BlindSetupFix.UseRoundLength -> acceptIntent(TimerIntent.UpdateRoundLength(fix.minutes))
            is BlindSetupFix.UseStartingChips -> acceptIntent(TimerIntent.UpdateStartingChips(fix.chips))
        }
    }

    private fun persist(config: BlindConfiguration) {
        timerPreferences.setGameDurationMinutes(config.gameDurationMinutes)
        timerPreferences.setBreakEveryLevels(config.breaks.everyLevels)
        timerPreferences.setBreakLengthMinutes(config.breaks.lengthMinutes)
        timerPreferences.setBreakMessage(config.breaks.message)
        timerPreferences.setBigBlindAnteFromLevel(config.bigBlindAnteFromLevel)
        tournamentPreferences.setGameDurationHours(config.gameDurationHours)
        tournamentPreferences.setRoundLengthMinutes(config.roundLengthMinutes)
        tournamentPreferences.setSmallestChip(config.smallestChip)
        tournamentPreferences.setStartingChips(config.startingChips)
    }

    /** Why [config] can't be played (or null), and its regular levels (empty when it can't). */
    private fun scheduleFor(config: BlindConfiguration): Pair<BlindSetupProblem?, List<BlindLevel>> {
        val problem = BlindSetupAdvisor.check(
            durationMinutes = config.gameDurationMinutes,
            roundLengthMinutes = config.roundLengthMinutes,
            smallestChip = config.smallestChip,
            startingChips = config.startingChips
        )
        val levels = if (problem == null) {
            runCatching {
                BlindStructureCalculator.generateSchedule(
                    BlindStructureInput(
                        players = tableConfig.numPlayers.coerceAtLeast(1),
                        targetDurationMinutes = config.gameDurationMinutes,
                        smallestChip = config.smallestChip,
                        startingStack = config.startingChips,
                        roundLengthMinutes = config.roundLengthMinutes,
                        bigBlindAnteFromLevel = config.bigBlindAnteFromLevel
                    )
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return problem to levels
    }

    private fun rebuildSchedule() {
        val config = _uiState.value.config
        val (problem, levels) = scheduleFor(config)
        val timeline = ClockTimeline.build(
            regularLevels = levels,
            roundLengthMinutes = config.roundLengthMinutes,
            breaks = config.breaks,
            smallestChip = config.smallestChip,
            bigBlindAnteFromLevel = config.bigBlindAnteFromLevel,
            chipSet = chipSet
        )
        // The break screen draws your chips only when the color-ups use them: some chip must pay the first blind
        val used = chipSet?.takeIf { it.chainFor(config.smallestChip).isNotEmpty() }
        _uiState.update { it.copy(baseBlindLevels = levels, timeline = timeline, setupProblem = problem, chipSet = used) }
    }

    /**
     * PP-091 #9: a chip set set up (or changed) in Tools re-plans the color-ups. Only the color-ups
     * change; levels and breaks keep their times, so the clock stays where it is.
     */
    private fun observeChipSet() {
        viewModelScope.launch {
            chipSets.chipSet.collect { chips ->
                if (chips != chipSet) {
                    chipSet = chips
                    rebuildSchedule()
                }
            }
        }
    }

    /** Defensive check of the schedule itself; [BlindSetupAdvisor] explains failures to the user. */
    internal fun isValidBlindConfiguration(state: TimerUiState): Boolean {
        val levels = state.baseBlindLevels
        val ordered = levels.zipWithNext().all { (a, b) ->
            b.roundStartMinute > a.roundStartMinute && b.smallBlind > a.smallBlind
        }
        return state.setupProblem == null &&
            levels.isNotEmpty() &&
            levels.last().smallBlind == state.config.startingChips &&
            ordered &&
            // Levels exactly fill the duration (overtime levels are added beyond it)
            levels.size * state.config.roundLengthMinutes == state.config.gameDurationMinutes
    }

    // ------------------------------------------------------------------ table numbers and settings

    private data class BankCounts(val eliminated: List<Int> = emptyList(), val rebuys: Int = 0, val addOns: Int = 0)

    private fun observeTable() {
        tableConfig = tournamentPreferences.getCurrentTournamentConfig()
        bank = BankCounts(
            bankPreferences.getEliminationOrder(),
            bankPreferences.getTotalRebuyCount(),
            bankPreferences.getTotalAddonCount()
        )
        refreshTable()
        viewModelScope.launch {
            tournamentPreferences.config
                .combine(
                    combine(
                        bankPreferences.eliminationOrder,
                        bankPreferences.totalRebuys,
                        bankPreferences.totalAddons,
                        bankPreferences.revision, // a purchase's price can change with its count unchanged
                    ) { eliminated, rebuys, addOns, _ -> BankCounts(eliminated, rebuys, addOns) }
                ) { config, bankCounts -> config to bankCounts }
                .collect { (config, bankCounts) ->
                    tableConfig = config
                    bank = bankCounts
                    refreshTable()
                }
        }
    }

    /**
     * The rebuy cutoff (Tournament setup) and the chime's mute (Tools, Sound), as they change; and a
     * whole setup put in before the start (a preset loaded, PP-032), read again as a blind change.
     */
    private fun observeSettings() {
        _uiState.update {
            it.copy(rebuyUntilLevel = tournamentPreferences.getRebuyUntilLevel(), isMuted = audioPreferences.getIsMuted())
        }
        viewModelScope.launch {
            tournamentPreferences.rebuyUntilLevel.collect { level -> _uiState.update { it.copy(rebuyUntilLevel = level) } }
        }
        viewModelScope.launch {
            audioPreferences.isMuted.collect { muted -> _uiState.update { it.copy(isMuted = muted) } }
        }
        viewModelScope.launch {
            tournamentPreferences.setupRevision.drop(1).collect {
                if (!_uiState.value.hasTimerStarted) changeSetup { loadConfig(frozen = false) }
            }
        }
    }

    private fun refreshTable() {
        val players = tableConfig.numPlayers
        val out = bank.eliminated.filter { it in 1..players }.distinct().size
        val left = (players - out).coerceAtLeast(0)
        val stacks = players.toLong() + bank.rebuys + bank.addOns
        val chips = stacks * _uiState.value.config.startingChips
        // Purchases, late entries and re-entries at the prices paid (PP-085, PP-116)
        val prizePool = bankPreferences.recordedPool(tableConfig.money, players).prizePoolCents
        val table = TableStats(
            playerCount = players,
            playersLeft = left,
            averageStack = if (left > 0) (chips / left).toInt() else 0,
            // The same prize pool the Payouts table splits (buy-ins, rebuys and add-ons at the prices
            // they were bought at; no food or bounty)
            prizePoolCents = prizePool,
            // The places that table pays, from the one payout calculation (PP-135: the bubble)
            paidPlaces = payouts(prizePool, tableConfig.payoutWeights, players, tableConfig.payoutRounding).places.size
        )
        val purchases = Purchases(
            rebuyCents = tableConfig.money.rebuyCents,
            addOnCents = tableConfig.money.addOnCents,
            rebuysTaken = bank.rebuys,
            addOnsTaken = bank.addOns
        )
        _uiState.update { it.copy(table = table, purchases = purchases) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60
        const val MAX_HOURS = 24
        const val MAX_BREAK_EVERY = 20
        const val MAX_BREAK_MINUTES = 120
        const val MAX_NOTE = BreakSettings.MAX_MESSAGE_LENGTH
    }
}
