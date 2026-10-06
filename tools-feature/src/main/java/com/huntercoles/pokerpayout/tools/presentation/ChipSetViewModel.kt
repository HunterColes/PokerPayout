package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.coroutines.DefaultDispatcher
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindSchedule
import com.huntercoles.pokerpayout.core.utils.BlindScheduleProvider
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.core.utils.PlanStacksUseCase
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.core.utils.StackPlanRequest
import com.huntercoles.pokerpayout.tools.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

/**
 * The chip set screen's state (S11, PP-033).
 *
 * @property players and [tournamentStack] come from the Tournament setup, the one source of truth
 *   (the stack the clock runs, once it has started); [ChipSetSettings.stackOverride] can stand in.
 * @property smallBlind the first small blind (the Tournament's smallest chip).
 * @property plan the plan for everything above; null until the first one is worked out.
 * @property hasSchedule whether the Tournament setup makes a blind schedule (the color-up plan).
 * @property editor the colour sheet, when open.
 */
data class ChipSetUiState(
    val settings: ChipSetSettings = ChipSetSettings(),
    val players: Int = 0,
    val tournamentStack: Int = 0,
    val smallBlind: Int = 0,
    val plan: StackPlan? = null,
    val hasSchedule: Boolean = false,
    val editor: ColourEditor? = null
) {
    val inventory: ChipInventory get() = settings.inventory
    val startingStack: Int get() = settings.stackOverride ?: tournamentStack
    val stackFromTournament: Boolean get() = settings.stackOverride == null
}

/** The colour sheet: edits [editing], or adds a colour when it is null. */
data class ColourEditor(val editing: ChipColour?)

/** Everything the chip set screen can ask for. */
sealed interface ChipSetIntent {
    /** How many chips of [colour] you own (the row's stepper). */
    data class SetCount(val colour: ChipColour, val count: Int) : ChipSetIntent
    data object AddColour : ChipSetIntent
    data class EditColour(val colour: ChipColour) : ChipSetIntent
    data object CloseEditor : ChipSetIntent

    /** Saves the colour sheet: [chip] in place of [replacing] (null when adding). */
    data class SaveColour(val chip: InventoryChip, val replacing: ChipColour?) : ChipSetIntent

    /** Takes a colour out of the set, with Undo. */
    data class RemoveColour(val colour: ChipColour) : ChipSetIntent
    data class SetReserve(val stacks: Int) : ChipSetIntent
    data class SetMaxColours(val count: Int) : ChipSetIntent
    data class SetShape(val shape: ChipDistributionCurve) : ChipSetIntent

    /** Plan a different starting stack; null (or the Tournament's own) follows the Tournament again. */
    data class SetStackOverride(val stack: Int?) : ChipSetIntent

    /** Back to the starting set and default settings, at once, with Undo. */
    data object Reset : ChipSetIntent
}

/**
 * The chip set (S11, PP-033): plans starting stacks from the chips you own, live. Every change to
 * the set or the settings is saved and re-planned at once (no Generate button); the plan runs on the
 * compute dispatcher and a newer change cancels it. Players and the starting stack follow the
 * Tournament setup; the clock's schedule ([BlindScheduleProvider]) gives the color-up plan.
 */
@HiltViewModel
class ChipSetViewModel @Inject constructor(
    private val chipPreferences: ChipCalculatorPreferences,
    private val tournamentPreferences: TournamentPreferences,
    private val schedules: BlindScheduleProvider,
    private val snackbars: SnackbarController,
    private val messages: ChipSetMessages,
    @DefaultDispatcher private val computeDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChipSetUiState(
            settings = chipPreferences.current(),
            players = tournamentPreferences.getPlayerCount(),
            tournamentStack = tournamentPreferences.getStartingChips(),
            smallBlind = SmallestChipChoices.normalize(tournamentPreferences.getSmallestChip())
        )
    )
    val uiState: StateFlow<ChipSetUiState> = _uiState.asStateFlow()

    private val planStacks = PlanStacksUseCase()
    private var schedule: BlindSchedule? = null
    private var planning: Job? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(computeDispatcher) { runCatching { schedules.currentSchedule() }.getOrNull() }
            schedule = loaded
            if (loaded != null) {
                _uiState.update {
                    it.copy(hasSchedule = true, tournamentStack = loaded.startingChips, smallBlind = loaded.smallestChip)
                }
            }
            combine(chipPreferences.settings, tournamentPreferences.config) { settings, config -> settings to config.numPlayers }
                .collect { (settings, players) ->
                    _uiState.update { it.copy(settings = settings, players = players) }
                    replan()
                }
        }
    }

    fun acceptIntent(intent: ChipSetIntent) {
        val inventory = _uiState.value.inventory
        when (intent) {
            is ChipSetIntent.SetCount -> save { setInventory(inventory.withCount(intent.colour, intent.count)) }
            ChipSetIntent.AddColour -> _uiState.update { it.copy(editor = ColourEditor(editing = null)) }
            is ChipSetIntent.EditColour -> _uiState.update { it.copy(editor = ColourEditor(editing = intent.colour)) }
            ChipSetIntent.CloseEditor -> _uiState.update { it.copy(editor = null) }
            is ChipSetIntent.SaveColour -> inventory.withChip(intent.chip, intent.replacing)?.let { saved ->
                _uiState.update { it.copy(editor = null) }
                save { setInventory(saved) }
            }
            is ChipSetIntent.RemoveColour -> removeColour(inventory, intent.colour)
            is ChipSetIntent.SetReserve -> save { setReserveStacks(intent.stacks) }
            is ChipSetIntent.SetMaxColours -> save { setMaxColours(intent.count) }
            is ChipSetIntent.SetShape -> save { setShape(intent.shape) }
            is ChipSetIntent.SetStackOverride -> save {
                setStackOverride(intent.stack?.takeIf { it > 0 && it != _uiState.value.tournamentStack })
            }
            ChipSetIntent.Reset -> reset()
        }
    }

    /**
     * Saves a change and shows it at once (the settings flow then re-plans it). Showing it here
     * rather than on the flow's next emission keeps a held stepper's count in step with the taps.
     */
    private fun save(change: ChipCalculatorPreferences.() -> Unit) {
        chipPreferences.change()
        _uiState.update { it.copy(settings = chipPreferences.current()) }
    }

    private fun removeColour(inventory: ChipInventory, colour: ChipColour) {
        val removed = inventory[colour] ?: return
        _uiState.update { it.copy(editor = null) }
        save { setInventory(inventory.without(colour)) }
        viewModelScope.launch {
            if (snackbars.showUndo(messages.removed(removed), messages.undo)) {
                _uiState.value.inventory.withChip(removed)?.let { back -> save { setInventory(back) } }
            }
        }
    }

    private fun reset() {
        val before = chipPreferences.current()
        if (before == ChipSetSettings()) return
        _uiState.update { it.copy(editor = null) }
        save { resetAllData() }
        viewModelScope.launch {
            if (snackbars.showUndo(messages.reset, messages.undo)) save { restore(before) }
        }
    }

    /** Plans the current inputs; a newer change cancels this one. The last plan stays up meanwhile. */
    private fun replan() {
        planning?.cancel()
        val state = _uiState.value
        val request = StackPlanRequest(
            inventory = state.inventory,
            startingStack = state.startingStack,
            players = state.players,
            smallBlind = state.smallBlind,
            reserveStacks = state.settings.reserveStacks,
            maxColours = state.settings.maxColours,
            curve = state.settings.shape,
            schedule = schedule
        )
        planning = viewModelScope.launch {
            val plan = withContext(computeDispatcher) { planStacks(request) }
            _uiState.update { it.copy(plan = plan) }
        }
    }
}

/** The few strings the chip set's ViewModel shows itself: its snackbars. */
class ChipSetMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val reset: String get() = context.getString(R.string.chip_set_snackbar_reset)
    val undo: String get() = context.getString(R.string.chip_set_undo)

    /** "Green 25 removed". */
    fun removed(chip: InventoryChip): String {
        val colour = ChipSetText.colourName(context.resources, chip.colour)
        return context.getString(R.string.chip_set_snackbar_removed, colour, ChipSetText.number(chip.value))
    }
}

/** Chip set wording shared by the ViewModel and the screen. */
object ChipSetText {
    private val symbols = DecimalFormatSymbols(Locale.US)

    /** "White", "Light blue", from the chip colour names. */
    fun colourName(resources: Resources, colour: ChipColour): String =
        resources.getStringArray(R.array.chip_set_colour_names)[colour.ordinal]

    /** "1,000": whole numbers as the app shows them, US digits whatever the locale. */
    fun number(value: Long): String = DecimalFormat("#,##0", symbols).format(value)

    fun number(value: Int): String = number(value.toLong())
}
