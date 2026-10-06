package com.huntercoles.pokerpayout.tournament.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * The Tournament tab is one page in two moods (S1 v2): setup before the start, the clock after it.
 *
 * ```
 * Setup ──Start──▶ Folding ──fold ends──▶ Running ◀──strip / close──▶ PanelOpen
 *   ▲                                        │                           │
 *   └───────────────── reset (no clock) ─────┴───────────────────────────┘
 * ```
 * The fold is a one-shot animation; under Reduce motion, or when a clock already exists (a restart,
 * a rotation), the page cuts straight to Running.
 */
enum class TournamentMode { Setup, Folding, Running, PanelOpen }

/**
 * The tab's own UI state, kept across rotation and process death (it is saved with the screen).
 *
 * @property startPressed Start was pressed in setup: when the clock starts, fold into it.
 * @property panelUnlocked the setup panel's money and blind fields are open ("Unlock to edit…").
 * @property rotationPaused ✕ in a table view the phone was turned into: stay portrait on this visit.
 */
@Immutable
@Parcelize
data class TournamentUi(
    val mode: TournamentMode = TournamentMode.Setup,
    val startPressed: Boolean = false,
    val panelUnlocked: Boolean = false,
    val rotationPaused: Boolean = false,
) : Parcelable {

    /** The state to show while the clock [clockStarted] (true from the first start until a reset). */
    fun settledFor(clockStarted: Boolean, reducedMotion: Boolean): TournamentUi = when {
        !clockStarted -> if (mode == TournamentMode.Setup) this else TournamentUi(startPressed = startPressed)
        mode != TournamentMode.Setup -> this
        startPressed && !reducedMotion -> copy(mode = TournamentMode.Folding, startPressed = false)
        else -> copy(mode = TournamentMode.Running, startPressed = false)
    }

    fun pressStart(): TournamentUi = if (mode == TournamentMode.Setup) copy(startPressed = true) else this

    fun foldFinished(): TournamentUi = if (mode == TournamentMode.Folding) copy(mode = TournamentMode.Running) else this

    fun openPanel(): TournamentUi = if (mode == TournamentMode.Running) copy(mode = TournamentMode.PanelOpen) else this

    fun closePanel(): TournamentUi =
        if (mode == TournamentMode.PanelOpen) copy(mode = TournamentMode.Running, panelUnlocked = false) else this

    fun unlock(): TournamentUi = if (mode == TournamentMode.PanelOpen) copy(panelUnlocked = true) else this

    fun lock(): TournamentUi = copy(panelUnlocked = false)

    fun pauseRotation(paused: Boolean): TournamentUi = copy(rotationPaused = paused)

    companion object {
        /** Where the tab opens: setup, or the clock when one exists. */
        fun initial(clockStarted: Boolean): TournamentUi =
            TournamentUi(mode = if (clockStarted) TournamentMode.Running else TournamentMode.Setup)
    }
}
