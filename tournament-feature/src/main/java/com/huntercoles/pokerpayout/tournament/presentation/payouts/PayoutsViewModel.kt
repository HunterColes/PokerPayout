package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.NightResults
import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Payouts tab can be asked to do. Every change is saved and shared with the Bank. */
sealed interface PayoutsIntent {
    data class SelectPreset(val preset: PayoutPreset) : PayoutsIntent

    data class SelectRounding(val rounding: PayoutRounding) : PayoutsIntent

    data class SetPlaces(val places: Int) : PayoutsIntent

    data object ShowStructure : PayoutsIntent

    data object HideStructure : PayoutsIntent

    data class SaveStructure(val settings: PayoutSettings) : PayoutsIntent

    /** Saves the finished night to History (PP-037); offered once everyone is paid. */
    data object SaveNight : PayoutsIntent
}

/**
 * The Payouts tab (S6). One settlement of what the Bank recorded (the same one the Bank shows), so
 * the pool, the table and the names agree with the Bank to the cent, rebuys at their prices (PP-085).
 * The structure is saved in the tournament settings and locked while the clock runs. Once the night
 * is over and everyone is paid, it can be saved to History (PP-037).
 */
@HiltViewModel
class PayoutsViewModel @Inject constructor(
    private val tournamentPreferences: TournamentPreferences,
    private val bankPreferences: BankPreferences,
    private val settleTournament: SettleTournamentUseCase,
    private val calculatePayouts: CalculatePayoutsUseCase,
    private val nights: NightRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayoutsUiState())
    val uiState: StateFlow<PayoutsUiState> = _uiState.asStateFlow()

    /** Tonight's results while the night is over and everyone is paid; null before. */
    private var finished: List<NightPlayer>? = null

    init {
        refresh()
        viewModelScope.launch { tournamentPreferences.config.collect { refresh() } }
        viewModelScope.launch { bankPreferences.revision.collect { refresh() } }
        viewModelScope.launch { nights.nights.collect { refresh() } }
        viewModelScope.launch {
            tournamentPreferences.tournamentLocked.collect { locked -> _uiState.update { it.copy(isLocked = locked) } }
        }
    }

    fun acceptIntent(intent: PayoutsIntent) {
        when (intent) {
            is PayoutsIntent.SelectPreset -> change { it.copy(preset = intent.preset).withPlaces(currentPlaces()) }
            is PayoutsIntent.SelectRounding -> change { it.copy(rounding = intent.rounding) }
            is PayoutsIntent.SetPlaces -> change { it.withPlaces(intent.places.coerceIn(1, _uiState.value.maxPlaces)) }
            PayoutsIntent.ShowStructure -> _uiState.update { it.copy(showStructureSheet = true) }
            PayoutsIntent.HideStructure -> _uiState.update { it.copy(showStructureSheet = false) }
            is PayoutsIntent.SaveStructure -> {
                _uiState.update { it.copy(showStructureSheet = false) }
                change { intent.settings }
            }
            PayoutsIntent.SaveNight -> saveNight()
        }
    }

    /** Saves tonight once: nothing happens before it is over, or once History holds it. */
    private fun saveNight() {
        val players = finished ?: return
        val pool = _uiState.value.pool.prizePoolCents
        if (!nights.isSaved(players, pool)) nights.save(players, pool)
        refresh()
    }

    private fun nightSave(tonight: List<NightPlayer>?, prizePoolCents: Long): NightSave = when {
        tonight == null -> NightSave.NotOver
        nights.isSaved(tonight, prizePoolCents) -> NightSave.Saved
        else -> NightSave.Offered
    }

    /** The places paid now (the table never pays more places than there are players). */
    private fun currentPlaces(): Int =
        _uiState.value.places.takeIf { it > 0 } ?: PayoutPlaces.recommended(_uiState.value.playerCount)

    /** Saves a new structure, unless the clock is running. */
    private fun change(transform: (PayoutSettings) -> PayoutSettings) {
        if (_uiState.value.isLocked) return
        val current = tournamentPreferences.getPayoutSettings()
        val next = transform(current)
        if (next != current) tournamentPreferences.setPayoutSettings(next)
        refresh()
    }

    private fun refresh() {
        val config = tournamentPreferences.getCurrentTournamentConfig()
        val ids = (1..config.numPlayers).toList()
        val players = ids.map { id ->
            BankPlayer(
                id = id,
                boughtIn = bankPreferences.getPlayerBuyInStatus(id),
                paidOut = bankPreferences.getPlayerPayedOutStatus(id),
                eliminatedBy = bankPreferences.getPlayerEliminatedBy(id),
                rebuyPricesCents = bankPreferences.getPlayerRebuyPrices(id),
                addOnPricesCents = bankPreferences.getPlayerAddonPrices(id),
                bountyDrawCents = bankPreferences.getPlayerBountyDraw(id),
                entryPrice = bankPreferences.getPlayerEntryPrice(id),
                reEntryOf = bankPreferences.getPlayerReEntryOf(id)
            )
        }
        val names = ids.associateWith { bankPreferences.getPlayerName(it) }
        val settlement = settleTournament(
            players = players,
            eliminationOrder = bankPreferences.getEliminationOrder(),
            money = config.money,
            weights = config.payoutWeights,
            rounding = config.payoutRounding
        )
        val settings = tournamentPreferences.getPayoutSettings()
        val places = settlement.payoutTable.places.size.coerceAtLeast(1)
        // The stepper stops at the most places that each pay something (a place that would get $0 isn't paid)
        val payable = calculatePayouts.payablePlaces(settlement.pool.prizePoolCents, settings, config.numPlayers)
        val tonight = NightResults.of(settlement, players, names)
        finished = tonight
        _uiState.update {
            it.copy(
                playerCount = config.numPlayers,
                money = config.money,
                pool = settlement.pool,
                rebuyCount = players.sumOf { player -> player.rebuyPricesCents.orEmpty().size },
                addOnCount = players.sumOf { player -> player.addOnPricesCents.orEmpty().size },
                settings = settings,
                table = settlement.payoutTable,
                firstPlaceByPreset = PayoutPreset.entries.associateWith { preset ->
                    calculatePayouts(
                        prizePoolCents = settlement.pool.prizePoolCents,
                        weights = preset.weightsFor(places),
                        playerCount = config.numPlayers,
                        rounding = config.payoutRounding
                    ).amountFor(1)
                },
                recommendedPlaces = PayoutPlaces.recommended(config.numPlayers).coerceAtMost(payable),
                maxPlaces = payable,
                rows = rows(settlement, names),
                bubble = bubble(settlement, config.numPlayers),
                bounties = bounties(settlement, names, config.money.bountyCents, settlement.pool.foodCents),
                night = nightSave(tonight, settlement.pool.prizePoolCents)
            )
        }
    }

    private fun rows(settlement: Settlement, names: Map<Int, String>): List<PayoutRowModel> {
        val pool = settlement.payoutTable.prizePoolCents
        return settlement.payoutTable.places.map { row ->
            PayoutRowModel(
                place = row.place,
                amountCents = row.amountCents,
                // The rounded amount's share, not the weight's: $130 of $450 is 28.9%, whatever 20/70 says.
                sharePercent = if (pool > 0L) row.amountCents * PERCENT / pool else row.sharePercent,
                holderName = settlement.standings.playerAt(row.place)?.let { names[it] }
            )
        }
    }

    private fun bubble(settlement: Settlement, playerCount: Int): BubbleModel {
        val paid = settlement.payoutTable.places.size
        val decided = settlement.standings.placeByPlayer.values.toSet()
        val seats = (playerCount downTo 1).map { place ->
            val state = when {
                place in decided && place <= paid -> SeatState.Cashed
                place in decided -> SeatState.Out
                place <= paid -> SeatState.Money
                else -> SeatState.Bubble
            }
            SeatModel(place, state)
        }
        val stillIn = playerCount - settlement.standings.eliminated.size
        return BubbleModel(
            seats = seats,
            moreOutToMoney = (stillIn - paid).coerceAtLeast(0),
            nextOutPlace = stillIn.takeIf { settlement.championId == null && it > 1 }
        )
    }

    /**
     * Who claimed which bounties, and for how much: in every bounty mode (PP-035) a claim is what the
     * settlement pays its eliminator for knockouts (a bounty each, the cash halves, or the envelopes
     * drawn), so the card and the share text agree with the Bank to the cent.
     */
    private fun bounties(settlement: Settlement, names: Map<Int, String>, perHead: Long, foodCents: Long): BountiesModel {
        val champion = settlement.championId
        val victimsBy = settlement.standings.eliminated
            .filter { it != champion }
            .mapNotNull { victim -> creditOf(settlement, victim, names)?.let { it to victim } }
            .groupBy({ it.first }, { it.second })
        val claims = victimsBy.map { (eliminator, victims) ->
            BountyClaim(
                names[eliminator].orEmpty(),
                victims.map { names[it].orEmpty() },
                settlement.forPlayer(eliminator)?.knockoutBountyCents ?: 0L
            )
        }
        val championOwed = champion?.let { settlement.forPlayer(it) }
        val championCents = (championOwed?.kingsBountyCents ?: 0L) + (championOwed?.unclaimedBountyCents ?: 0L)
        return BountiesModel(
            perHeadCents = perHead,
            claims = claims,
            stillOutCents = (settlement.pool.bountyPoolCents - claims.sumOf { it.cents } - championCents).coerceAtLeast(0L),
            championName = champion?.let { names[it] },
            championCents = championCents,
            foodCents = foodCents,
            mode = settlement.bountyMode,
            envelopes = if (perHead > 0L) names.size else 0
        )
    }

    /** Who [victim]'s knockout is credited to, as the settlement counts it. */
    private fun creditOf(settlement: Settlement, victim: Int, names: Map<Int, String>): Int? =
        bankPreferences.getPlayerEliminatedBy(victim)?.takeIf { it != victim && it in names && settlement.forPlayer(it) != null }

    private companion object {
        const val PERCENT = 100.0
    }
}
