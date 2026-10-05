package com.huntercoles.pokerpayout.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.constants.AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * drift. The anchor is saved when the clock starts, pauses, jumps, finishes or resets, so a killed
 * process resumes exactly where the clock would be, overtime included.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences,
    private val bankPreferences: BankPreferences,
    private val soundManager: SoundManager,
    private val timeSource: TimeSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var anchor = ClockAnchor()
    private var tickJob: Job? = null

    /** Play time at the previous look, to detect sound cues crossed since. */
    private var lastSeenMillis = 0L

    private var money = TableMoney()
    private var bank = BankCounts()

    init {
        soundManager.preloadSound(R.raw.blind_level_up)
        normalizeStoredSmallestChip()
        restore()
        observeTable()
    }

    fun acceptIntent(intent: TimerIntent) {
        when (intent) {
            TimerIntent.ToggleTimer -> toggleTimer()
            TimerIntent.ResetTimer -> resetTimer()
            TimerIntent.NextBlindLevel -> jumpToSegment(_uiState.value.currentSegmentIndex + 1)
            TimerIntent.PreviousBlindLevel -> jumpToSegment(_uiState.value.currentSegmentIndex - 1)
            TimerIntent.ShowInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = true) }
            TimerIntent.HideInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = false) }
            is TimerIntent.SetTableView -> _uiState.update { it.copy(isTableView = intent.enabled) }
            is TimerIntent.ApplyFix -> applyFix(intent.fix)
            else -> acceptSetupIntent(intent)
        }
    }

    private fun acceptSetupIntent(intent: TimerIntent) {
        when (intent) {
            is TimerIntent.GameDurationHoursChanged -> changeSetup {
                it.copy(gameDurationMinutes = intent.hours.coerceIn(1, MAX_HOURS) * MINUTES_PER_HOUR)
            }
            is TimerIntent.UpdateSmallestChip -> changeSetup { it.copy(smallestChip = intent.value.coerceAtLeast(1)) }
            is TimerIntent.UpdateStartingChips -> changeSetup { it.copy(startingChips = intent.value.coerceAtLeast(1)) }
            is TimerIntent.UpdateRoundLength -> changeSetup {
                it.copy(roundLengthMinutes = intent.minutes.coerceAtLeast(1))
            }
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

    private fun changeBreaks(transform: (BreakSettings) -> BreakSettings) =
        changeKeepingPosition { it.copy(breaks = transform(it.breaks)) }

    // ------------------------------------------------------------------ restore

    private fun restore() {
        val hasStarted = timerPreferences.getHasTimerStarted()
        _uiState.update {
            it.copy(
                config = loadConfig(frozen = hasStarted),
                hasTimerStarted = hasStarted,
                isFinished = timerPreferences.getIsFinished()
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
        val elapsed = anchor.elapsedAt(timeSource)
        anchor = ClockAnchor.runningFrom(elapsed, timeSource)
        timerPreferences.saveClock(anchor)
        tournamentPreferences.setTournamentLocked(true)
        tournamentPreferences.setIsConfigExpanded(false)
        lastSeenMillis = elapsed
        _uiState.update { it.copy(isRunning = true, isFinished = false) }
        startTicking()
    }

    private fun pause() {
        val elapsed = anchor.elapsedAt(timeSource)
        tickJob?.cancel()
        tickJob = null
        anchor = ClockAnchor.stopped(elapsed)
        timerPreferences.saveClock(anchor)
        tournamentPreferences.setTournamentLocked(false)
        _uiState.update { it.copy(isRunning = false) }
        show(elapsed, playCues = false)
    }

    /** The first start (or jump) freezes the setup so the schedule survives until reset. */
    private fun markStarted() {
        if (_uiState.value.hasTimerStarted) return
        val config = _uiState.value.config
        timerPreferences.setSmallestChipAtStart(config.smallestChip)
        timerPreferences.setStartingChipsAtStart(config.startingChips)
        timerPreferences.setRoundLengthAtStart(config.roundLengthMinutes)
        timerPreferences.setHasTimerStarted(true)
        timerPreferences.setIsFinished(false)
        _uiState.update { it.copy(hasTimerStarted = true) }
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

    /** Shows play time [elapsedMillis], plays any cue crossed since the last look, and finishes at the end. */
    private fun show(elapsedMillis: Long, playCues: Boolean) {
        val state = _uiState.value
        val timeline = state.timeline
        if (playCues) playCrossedCues(lastSeenMillis, elapsedMillis, timeline)
        lastSeenMillis = elapsedMillis

        val endMillis = timeline.endSeconds * MILLIS_PER_SECOND
        if (state.isRunning && !timeline.isEmpty && elapsedMillis >= endMillis) {
            finish(timeline.endSeconds)
            return
        }
        val seconds = (elapsedMillis / MILLIS_PER_SECOND).toInt()
        if (seconds != state.elapsedSeconds) _uiState.update { it.copy(elapsedSeconds = seconds) }
    }

    /**
     * One chime [LEVEL_CHANGE_SOUND_LEAD_SECONDS] before every level change, break start, break end and
     * the end of the last level. Crossing-based, so pausing or resuming can't skip or repeat one (B17),
     * and cues more than a moment stale (the device slept through them) stay silent.
     */
    private fun playCrossedCues(fromMillis: Long, toMillis: Long, timeline: ClockTimeline) {
        if (toMillis <= fromMillis) return
        val lead = LEVEL_CHANGE_SOUND_LEAD_SECONDS * MILLIS_PER_SECOND
        val crossed = timeline.segments.any { segment ->
            val boundary = segment.endSeconds * MILLIS_PER_SECOND
            val cue = boundary - lead
            cue > fromMillis && cue <= toMillis && toMillis <= boundary + CUE_GRACE_MILLIS
        }
        if (crossed) soundManager.playSound(R.raw.blind_level_up)
    }

    private fun finish(endSeconds: Int) {
        tickJob?.cancel()
        tickJob = null
        anchor = ClockAnchor.stopped(endSeconds * MILLIS_PER_SECOND)
        timerPreferences.saveClock(anchor)
        timerPreferences.setIsFinished(true)
        lastSeenMillis = anchor.elapsedMillis
        _uiState.update { it.copy(isRunning = false, isFinished = true, elapsedSeconds = endSeconds) }
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
    }

    private fun resetTimer() {
        tickJob?.cancel()
        tickJob = null
        anchor = ClockAnchor()
        lastSeenMillis = 0
        timerPreferences.resetTimer()
        tournamentPreferences.setTournamentLocked(false)
        _uiState.update { TimerUiState(config = loadConfig(frozen = false), table = it.table) }
        rebuildSchedule()
        refreshTable()
    }

    private fun anchorAt(elapsedMillis: Long, running: Boolean) =
        if (running) ClockAnchor.runningFrom(elapsedMillis, timeSource) else ClockAnchor.stopped(elapsedMillis)

    override fun onCleared() {
        // The anchor is already saved; a running clock resumes from it in the next process.
        tickJob?.cancel()
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
            anchor = ClockAnchor()
            lastSeenMillis = 0
            timerPreferences.resetTimer()
            tournamentPreferences.setTournamentLocked(false)
        }
        _uiState.update {
            it.copy(config = new, elapsedSeconds = 0, isRunning = false, isFinished = false, hasTimerStarted = false)
        }
        rebuildSchedule()
        refreshTable()
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

    private fun rebuildSchedule() {
        val config = _uiState.value.config
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
                        players = money.players.coerceAtLeast(1),
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
        val timeline = ClockTimeline.build(
            regularLevels = levels,
            roundLengthMinutes = config.roundLengthMinutes,
            breaks = config.breaks,
            smallestChip = config.smallestChip,
            bigBlindAnteFromLevel = config.bigBlindAnteFromLevel
        )
        _uiState.update { it.copy(baseBlindLevels = levels, timeline = timeline, setupProblem = problem) }
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

    // ------------------------------------------------------------------ table numbers

    private data class TableMoney(
        val players: Int = 0,
        val buyIn: Double = 0.0,
        val rebuy: Double = 0.0,
        val addOn: Double = 0.0
    )

    private data class BankCounts(val eliminated: List<Int> = emptyList(), val rebuys: Int = 0, val addOns: Int = 0)

    private fun observeTable() {
        money = TableMoney(
            tournamentPreferences.getPlayerCount(),
            tournamentPreferences.getBuyIn(),
            tournamentPreferences.getRebuyAmount(),
            tournamentPreferences.getAddOnAmount()
        )
        bank = BankCounts(
            bankPreferences.getEliminationOrder(),
            bankPreferences.getTotalRebuyCount(),
            bankPreferences.getTotalAddonCount()
        )
        refreshTable()
        viewModelScope.launch {
            combine(
                tournamentPreferences.playerCount,
                tournamentPreferences.buyIn,
                tournamentPreferences.rebuyPerPlayer,
                tournamentPreferences.addOnPerPlayer
            ) { players, buyIn, rebuy, addOn -> TableMoney(players, buyIn, rebuy, addOn) }
                .combine(
                    combine(
                        bankPreferences.eliminationOrder,
                        bankPreferences.totalRebuys,
                        bankPreferences.totalAddons
                    ) { eliminated, rebuys, addOns -> BankCounts(eliminated, rebuys, addOns) }
                ) { tableMoney, bankCounts -> tableMoney to bankCounts }
                .collect { (tableMoney, bankCounts) ->
                    money = tableMoney
                    bank = bankCounts
                    refreshTable()
                }
        }
    }

    private fun refreshTable() {
        val players = money.players
        val out = bank.eliminated.filter { it in 1..players }.distinct().size
        val left = (players - out).coerceAtLeast(0)
        val stacks = players.toLong() + bank.rebuys + bank.addOns
        val chips = stacks * _uiState.value.config.startingChips
        val table = TableStats(
            playerCount = players,
            playersLeft = left,
            averageStack = if (left > 0) (chips / left).toInt() else 0,
            prizePool = money.buyIn * players + money.rebuy * bank.rebuys + money.addOn * bank.addOns
        )
        _uiState.update { it.copy(table = table) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MINUTES_PER_HOUR = 60
        const val MAX_HOURS = 24
        const val MAX_BREAK_EVERY = 20
        const val MAX_BREAK_MINUTES = 120
        const val MAX_NOTE = BreakSettings.MAX_MESSAGE_LENGTH

        /** A cue that's this late (the device slept through it) is skipped rather than played late. */
        const val CUE_GRACE_MILLIS = 2_000L
    }
}
