package com.huntercoles.pokerpayout.bank.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.ClockStatusProvider
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.ProgressiveBounty
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.players.PlayerNames
import com.huntercoles.pokerpayout.core.domain.players.Regulars
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.domain.usecase.DrawEnvelopeUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Bank (S5 v2, PP-030): who paid what, who is out and who is owed.
 *
 * - **Undo.** Every routine action applies at once and can be taken back: from the snackbar while it
 *   shows, or from the top bar's Undo, newest first, up to [MAX_UNDO] actions. Undo restores what
 *   the Bank had recorded before the action (a snapshot, not an inverse), so a knockout's place,
 *   bounty credit and payouts all come back exactly. Names aren't part of it, and anything that
 *   changes the Bank from outside (the Tournament tab, a reset) clears the history.
 * - **Cutoffs.** Rebuys close at the end of the level set in "rebuys until", add-ons at the end of
 *   the first break after it ([PurchaseWindow]), following the clock ([ClockStatusProvider]).
 * - **Prices.** Each rebuy and add-on is recorded at today's price and keeps it (PP-085).
 * - **Bounty modes** (PP-035). Progressive bounties are worked out from the knockouts recorded, so
 *   Undo and a restart give them back exactly. A mystery knockout draws its envelope when it is
 *   recorded, keeps it with the knocked-out player, and shows it ([BankSheet.Envelope]); Undo or
 *   Bring back puts the envelope back in the pool.
 * - **Settle up.** Once the night is over, who pays whom so that everyone is square
 *   ([SettleUpUseCase]; the Bank is a party), with a tick per payment. Ticks are part of what Undo
 *   restores; a tick goes when its payment drops out of the settle-up (something else changed), and
 *   the last tick records every buy-in and payout as paid.
 * - **Regulars** (PP-110). Tonight's players picked from everyone the host has played with
 *   ([Regulars]): a regular takes the first seat nobody named, or a new seat once every seat has a
 *   name (the Tournament tab's player count goes up, as a late entry there would). Every name the Bank
 *   uses joins the regulars. Like a rename, a seat given back isn't an undoable action.
 */
@HiltViewModel
// One small function per action, and one injected source per thing the Bank reads (settings, records,
// the clock, the chime, the snackbar, the envelope draw, the settle-up, the regulars).
@Suppress("TooManyFunctions", "LongParameterList")
class BankViewModel @Inject constructor(
    private val tournamentPreferences: TournamentPreferences,
    private val bankPreferences: BankPreferences,
    private val timerPreferences: TimerPreferences,
    private val settleTournament: SettleTournamentUseCase,
    private val clockStatus: ClockStatusProvider,
    private val audioPreferences: AudioPreferences,
    private val feedback: BankFeedback,
    private val drawEnvelope: DrawEnvelopeUseCase,
    private val settleUp: SettleUpUseCase,
    private val regulars: Regulars
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankUiState())
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    /** The latest money picture; every amount on screen comes from here. */
    private var settlement: Settlement? = null

    /** Non-zero while this ViewModel writes Bank data, so it doesn't "reload" its own writes. */
    private var ownWrites = 0

    private val undoStack = ArrayDeque<UndoEntry>()
    private var snackbar: Job? = null

    init {
        // Initialize with saved player count, dropping data of players removed while we were away
        updatePlayerCount(tournamentPreferences.getPlayerCount())

        // Follow the Tournament tab live: player count, amounts, payout structure.
        viewModelScope.launch {
            tournamentPreferences.config.collect { config ->
                if (config.numPlayers != _uiState.value.players.size) {
                    updatePlayerCount(config.numPlayers)
                } else {
                    updateCalculations()
                }
            }
        }

        // Bank data changed elsewhere, e.g. the Tournament tab cleared purchases after asking.
        viewModelScope.launch {
            bankPreferences.revision.collect { reloadIfChangedElsewhere() }
        }

        // The payout structure is read-only while the clock runs.
        viewModelScope.launch {
            timerPreferences.timerRunning.collect { isRunning ->
                _uiState.update { it.copy(isTimerRunning = isRunning) }
            }
        }

        // The rebuy and add-on cutoffs follow the clock and the "rebuys until" setting.
        viewModelScope.launch {
            clockStatus.status.combine(tournamentPreferences.rebuyUntilLevel) { clock, cutoff -> clock to cutoff }
                .collect { (clock, cutoff) -> updateWindows(clock, cutoff) }
        }

        viewModelScope.launch {
            audioPreferences.isMuted.collect { muted -> _uiState.update { it.copy(isMuted = muted) } }
        }

        // The regulars follow History (a night saved, two names merged) and the names used here.
        viewModelScope.launch {
            regulars.roster.collect { roster -> _uiState.update { it.copy(roster = roster) } }
        }
        viewModelScope.launch {
            regulars.merges.collect { merges -> _uiState.update { it.copy(merges = merges) } }
        }
    }

    fun acceptIntent(intent: BankIntent) {
        ownWrites++
        try {
            handle(intent)
        } finally {
            ownWrites--
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per intent; each delegates.
    private fun handle(intent: BankIntent) {
        when (intent) {
            is BankIntent.PlayerNameChanged -> updatePlayerName(intent.playerId, intent.name)
            is BankIntent.BuyInToggled -> toggleBuyIn(intent.playerId)
            is BankIntent.AddPurchase -> addPurchase(intent.playerId, intent.kind)
            is BankIntent.OpenCount -> openCount(intent.playerId, intent.kind)
            is BankIntent.SetCount -> setCount(intent.playerId, intent.kind, intent.count)
            is BankIntent.OpenKnockout -> openKnockout(intent.playerId)
            is BankIntent.KnockOut -> knockOut(intent.playerId, intent.eliminatorId)
            is BankIntent.BringBack -> bringBack(intent.playerId)
            is BankIntent.OpenPayOut -> openPayOut(intent.playerId)
            is BankIntent.SetPaid -> setPaid(intent.playerId, intent.paid)
            BankIntent.ShowPoolBreakdown -> showSheet(BankSheet.PoolBreakdown)
            BankIntent.ShowPayoutStructure -> showSheet(BankSheet.PayoutStructure)
            BankIntent.ShowSettleUp -> if (_uiState.value.settleUp != null) showSheet(BankSheet.SettleUp)
            is BankIntent.SetSettlePaid -> setSettlePaid(intent.transfer, intent.paid)
            is BankIntent.UpdatePayoutSettings -> updatePayoutSettings(intent.settings)
            BankIntent.ShowResetConfirm -> if (_uiState.value.canReset) {
                showSheet(BankSheet.ResetConfirm(_uiState.value.players.size))
            }
            BankIntent.ConfirmReset -> resetBankData()
            BankIntent.DismissSheet -> showSheet(null)
            BankIntent.Undo -> undo(expected = null)
            BankIntent.ToggleMute -> audioPreferences.toggleMute()
            BankIntent.ShowRegulars -> showSheet(BankSheet.Regulars(order = _uiState.value.roster.map { it.key }))
            is BankIntent.ToggleRegular -> toggleRegular(intent.name)
            is BankIntent.AddRegular -> addRegular(intent.name)
        }
    }

    // Reading --------------------------------------------------------------------------------------

    private fun readPlayer(playerId: Int) = PlayerData(
        id = playerId,
        name = bankPreferences.getPlayerName(playerId),
        buyIn = bankPreferences.getPlayerBuyInStatus(playerId),
        out = bankPreferences.getPlayerOutStatus(playerId),
        paidOut = bankPreferences.getPlayerPayedOutStatus(playerId),
        rebuyPrices = bankPreferences.getPlayerRebuyPrices(playerId),
        addOnPrices = bankPreferences.getPlayerAddonPrices(playerId),
        eliminatedBy = bankPreferences.getPlayerEliminatedBy(playerId),
        outLevel = bankPreferences.getPlayerOutLevel(playerId),
        bountyDrawCents = bankPreferences.getPlayerBountyDraw(playerId)
    )

    /** The stored elimination order, limited to [players] and including every player marked out. */
    private fun normalizedEliminationOrder(players: List<PlayerData>): List<Int> {
        val validIds = players.map { it.id }.toSet()
        val sanitizedOrder = bankPreferences.getEliminationOrder().filter { it in validIds }.distinct()
        val missingEliminations = players.filter { it.out && it.id !in sanitizedOrder }.map { it.id }
        return sanitizedOrder + missingEliminations
    }

    private fun initializePlayers(count: Int) {
        val players = (1..count).map { readPlayer(it) }
        val normalizedOrder = normalizedEliminationOrder(players)
        if (normalizedOrder != bankPreferences.getEliminationOrder()) {
            bankPreferences.saveEliminationOrder(normalizedOrder)
        }
        _uiState.update {
            it.copy(players = players, eliminationOrder = normalizedOrder, settlePaid = bankPreferences.getSettlePaid())
        }
        updateCalculations()
    }

    private fun reloadIfChangedElsewhere() {
        if (ownWrites > 0) return
        val state = _uiState.value
        val stored = state.players.map { readPlayer(it.id) }
        val storedOrder = normalizedEliminationOrder(stored)
        if (stored != state.players || storedOrder != state.eliminationOrder) {
            // What Undo would restore no longer matches what is recorded.
            forgetHistory()
            _uiState.update { it.copy(players = stored, eliminationOrder = storedOrder, sheet = null) }
            updateCalculations()
        }
    }

    private fun updatePlayerCount(count: Int) {
        ownWrites++
        try {
            // Players above the new count are removed for good, so they can't come back after a restart.
            bankPreferences.removePlayersAbove(count)
            if (count != _uiState.value.players.size) forgetHistory()
            initializePlayers(count)
        } finally {
            ownWrites--
        }
    }

    private fun player(playerId: Int): PlayerData? = _uiState.value.players.firstOrNull { it.id == playerId }

    // Actions ------------------------------------------------------------------------------------

    /** A name typed in a row, or a seat given to a regular (or back); a real name joins the regulars. */
    private fun updatePlayerName(playerId: Int, name: String) {
        val normalized = name.ifBlank { "Player $playerId" }
        bankPreferences.savePlayerName(playerId, normalized)
        _uiState.update { state ->
            state.copy(players = state.players.map { if (it.id == playerId) it.copy(name = normalized) else it })
        }
        if (!PlayerNames.isPlaceholder(normalized)) regulars.remember(normalized)
        updateCalculations()
    }

    // Tonight's players (S25, PP-110) ------------------------------------------------------------

    /** At the table: their seat is nobody's again. Not yet: they sit down ([seat]). */
    private fun toggleRegular(name: String) {
        val state = _uiState.value
        val person = state.merges.key(name)
        val held = state.players.lastOrNull { !PlayerNames.isPlaceholder(it.name) && state.merges.key(it.name) == person }
        if (held != null) updatePlayerName(held.id, "") else seat(name)
    }

    /** A name typed in the sheet: a regular's spelling seats that regular; a new name seats a new regular. */
    private fun addRegular(typed: String) {
        val name = PlayerNames.clean(typed)
        if (PlayerNames.isPlaceholder(name)) return
        val state = _uiState.value
        val person = state.merges.key(name)
        if (state.players.any { !PlayerNames.isPlaceholder(it.name) && state.merges.key(it.name) == person }) return
        seat(state.roster.firstOrNull { it.key == person }?.name ?: name)
    }

    /**
     * [name] takes the first seat nobody named. With every seat named, a seat is added for them (up
     * to the Tournament tab's most players): the player count goes up, there too.
     */
    private fun seat(name: String) {
        val state = _uiState.value
        val open = state.players.firstOrNull { PlayerNames.isPlaceholder(it.name) }
        val count = state.players.size
        when {
            open != null -> updatePlayerName(open.id, name)
            count < TournamentConstants.MAX_PLAYERS -> {
                // Named before the count goes up, so the new row reads its name as it appears
                bankPreferences.savePlayerName(count + 1, name)
                regulars.remember(name)
                tournamentPreferences.setPlayerCount(count + 1)
                updatePlayerCount(count + 1)
            }
        }
    }

    private fun toggleBuyIn(playerId: Int) {
        val player = player(playerId) ?: return
        val message = if (player.buyIn) {
            feedback.buyInCleared(player.name)
        } else {
            feedback.buyIn(player.name, _uiState.value.money.entryCents)
        }
        record(message) { players, order -> players.replace(player.copy(buyIn = !player.buyIn)) to order }
    }

    private fun purchasePrice(kind: Purchase): Long = with(_uiState.value.money) {
        if (kind == Purchase.REBUY) rebuyCents else addOnCents
    }

    private fun window(kind: Purchase): PurchaseWindow = with(_uiState.value) {
        if (kind == Purchase.REBUY) rebuyWindow else addOnWindow
    }

    /** One more rebuy or add-on at today's price, while the column is open. */
    private fun addPurchase(playerId: Int, kind: Purchase) {
        val player = player(playerId) ?: return
        val price = purchasePrice(kind)
        val prices = player.prices(kind)
        if (price <= 0L || !window(kind).isOpen || prices.size >= MAX_PURCHASE_COUNT) return
        record(feedback.purchase(kind, player.name, price)) { players, order ->
            players.replace(player.withPrices(kind, prices + price)) to order
        }
    }

    private fun openCount(playerId: Int, kind: Purchase) {
        val player = player(playerId) ?: return
        val price = purchasePrice(kind)
        if (price <= 0L && player.prices(kind).isEmpty()) return
        showSheet(BankSheet.Count(playerId, player.name, kind, player.prices(kind), price, window(kind)))
    }

    /**
     * The count sheet's answer. Lowering removes the newest first; raising adds at today's price,
     * and only while the column is open. Out of range is clamped.
     */
    private fun setCount(playerId: Int, kind: Purchase, count: Int) {
        val player = player(playerId) ?: return
        val prices = player.prices(kind)
        val price = purchasePrice(kind)
        val canAdd = window(kind).isOpen && price > 0L
        val target = count.coerceIn(0, if (canAdd) MAX_PURCHASE_COUNT else prices.size)
        val next = if (target <= prices.size) prices.take(target) else prices + List(target - prices.size) { price }
        showSheet(null)
        record(feedback.count(kind, player.name, target)) { players, order ->
            players.replace(player.withPrices(kind, next)) to order
        }
    }

    private fun openKnockout(playerId: Int) {
        val state = _uiState.value
        val player = player(playerId) ?: return
        if (player.out || state.activePlayers <= 1) return
        val knockouts = state.knockoutCounts
        val stillIn = state.players.filter { !it.out && it.id != playerId }
        val out = state.eliminationOrder.reversed().mapNotNull { id -> state.players.firstOrNull { it.id == id } }
        val candidates = (stillIn + out).map {
            KnockoutCandidate(it.id, it.name, knockouts[it.id] ?: 0, it.out, bountyCents = headBounty(it.id))
        }
        showSheet(
            BankSheet.Knockout(
                playerId = playerId,
                name = player.name,
                place = state.activePlayers,
                // Progressive: the bounty on this player's head now, grown by their own knockouts
                bountyCents = if (state.bountyMode == BountyMode.PROGRESSIVE) headBounty(playerId) else state.money.bountyCents,
                candidates = candidates,
                preselectedId = player.eliminatedBy?.takeIf { previous -> candidates.any { it.playerId == previous } },
                mode = state.bountyMode,
                envelopesLeft = settlement?.envelopesLeft?.size ?: 0
            )
        )
    }

    /** The bounty on [playerId]'s head, as the latest settlement has it. */
    private fun headBounty(playerId: Int): Long = settlement?.forPlayer(playerId)?.headBountyCents ?: 0L

    /**
     * Knocks [playerId] out at once, crediting [eliminatorId] (null: nobody). No second question. In
     * a mystery-bounty game whoever is credited draws an envelope now, which the reveal shows.
     */
    private fun knockOut(playerId: Int, eliminatorId: Int?) {
        val state = _uiState.value
        val player = player(playerId) ?: return
        showSheet(null)
        if (player.out || state.activePlayers <= 1) return
        val credit = eliminatorId?.takeIf { id -> id != playerId && state.players.any { it.id == id } }
        val level = state.clock.level.takeIf { state.clock.started }
        val draw = if (credit != null && state.bountyMode == BountyMode.MYSTERY) {
            drawEnvelope(settlement?.envelopesLeft.orEmpty())
        } else {
            null
        }
        val message = feedback.knockout(
            name = player.name,
            place = state.activePlayers,
            eliminator = credit?.let { id -> player(id)?.name },
            pay = knockoutPay(playerId, credit, draw)
        )
        val knockedOut = player.copy(out = true, eliminatedBy = credit, outLevel = level, bountyDrawCents = draw)
        record(message) { players, order -> players.replace(knockedOut) to (order - playerId + playerId) }
        if (credit != null && draw != null) revealEnvelope(credit, player.name, draw)
    }

    /**
     * What knocking [victimId] out pays [eliminatorId], for the snackbar; worked out before the
     * knockout is recorded, so a progressive bounty splits the bounties as they stand now.
     */
    private fun knockoutPay(victimId: Int, eliminatorId: Int?, draw: Long?): KnockoutPay {
        val state = _uiState.value
        return when {
            state.money.bountyCents <= 0L -> KnockoutPay.NoBounty
            eliminatorId == null -> KnockoutPay.Bounty
            state.bountyMode == BountyMode.PROGRESSIVE -> {
                val onHead = headBounty(victimId)
                val split = ProgressiveBounty.split(onHead)
                // An eliminator already out has no bounty left to grow: they take it all
                if (player(eliminatorId)?.out == true) {
                    KnockoutPay.Progressive(cashCents = onHead, newBountyCents = null)
                } else {
                    KnockoutPay.Progressive(split.cashCents, newBountyCents = headBounty(eliminatorId) + split.headCents)
                }
            }
            draw != null -> KnockoutPay.Mystery(draw)
            else -> KnockoutPay.Bounty
        }
    }

    /** Mystery bounties: the envelope [eliminatorId] just drew, and, if that ended the night, the champion's. */
    private fun revealEnvelope(eliminatorId: Int, victimName: String, cents: Long) {
        val after = settlement ?: return
        val names = _uiState.value.players.associate { it.id to it.name }
        val champion = after.championId
        showSheet(
            BankSheet.Envelope(
                eliminatorName = names[eliminatorId].orEmpty(),
                victimName = victimName,
                cents = cents,
                envelopesLeft = after.envelopesLeft.size,
                championName = champion?.let { names[it] },
                championCents = champion?.let { after.forPlayer(it)?.kingsBountyCents } ?: 0L
            )
        )
    }

    private fun bringBack(playerId: Int) {
        val player = player(playerId)?.takeIf { it.out } ?: return
        // Back in: the knockout, its credit and any envelope drawn for it are taken back
        val backIn = player.copy(out = false, eliminatedBy = null, outLevel = null, bountyDrawCents = null)
        record(feedback.backIn(player.name)) { players, order -> players.replace(backIn) to (order - playerId) }
    }

    private fun openPayOut(playerId: Int) {
        val state = _uiState.value
        val player = player(playerId) ?: return
        val owed = settlement?.forPlayer(playerId) ?: return
        val knockedOut = state.eliminationOrder.toSet() - setOfNotNull(state.championId)
        val unclaimed = state.players.count { it.id in knockedOut && state.knockoutCredit(it) == null }
        showSheet(
            BankSheet.PayOut(
                playerId = playerId,
                name = player.name,
                owed = owed,
                isChampion = playerId == state.championId,
                money = state.money,
                rebuys = player.rebuys,
                addOns = player.addons,
                unclaimedKnockouts = if (playerId == state.championId) unclaimed else 0,
                envelopesLeft = state.envelopesLeft.size
            )
        )
    }

    private fun setPaid(playerId: Int, paid: Boolean) {
        val player = player(playerId) ?: return
        showSheet(null)
        if (player.paidOut == paid) return
        val winnings = settlement?.forPlayer(playerId)?.winningsCents ?: 0L
        val message = if (paid) feedback.paid(player.name, winnings) else feedback.unpaid(player.name)
        record(message) { players, order -> players.replace(player.copy(paidOut = paid)) to order }
    }

    /**
     * Ticks a settle-up payment as paid, or takes the tick back. The last tick means everyone has
     * paid what they owed: every buy-in and every payout is recorded as paid, which squares the night
     * (and empties the settle-up), as one action.
     */
    private fun setSettlePaid(transfer: Transfer, paid: Boolean) {
        val state = _uiState.value
        val transfers = state.settleUp?.transfers.orEmpty()
        if (transfer !in transfers || state.isPaid(transfer) == paid) return
        val ticks = if (paid) state.settlePaid + transfer else state.settlePaid - transfer
        if (ticks.containsAll(transfers)) {
            recordSquare()
        } else {
            val from = settleName(transfer.fromId)
            val message = feedback.settlePayment(from, settleName(transfer.toId), transfer.amountCents, paid)
            commit(message, BankSnapshot(state.players, state.eliminationOrder, ticks))
        }
    }

    /** Every buy-in paid and every winner paid out: nobody owes anybody, so no ticks are left to keep. */
    private fun recordSquare() {
        val state = _uiState.value
        val square = state.players.map { player ->
            val winnings = settlement?.forPlayer(player.id)?.winningsCents ?: 0L
            player.copy(buyIn = true, paidOut = player.paidOut || winnings > 0L)
        }
        commit(feedback.square(), BankSnapshot(square, state.eliminationOrder, emptySet()))
    }

    /** A player's name in the settle-up; null for the Bank. */
    private fun settleName(id: Int): String? = if (id == SettleUp.BANK_ID) null else player(id)?.name.orEmpty()

    private fun updatePayoutSettings(settings: PayoutSettings) {
        showSheet(null)
        if (_uiState.value.isTimerRunning) return
        tournamentPreferences.setPayoutSettings(settings)
        updateCalculations()
    }

    private fun resetBankData() {
        showSheet(null)
        forgetHistory()
        // Reset bank preferences (player names and payment states only)
        bankPreferences.resetAllBankData()
        initializePlayers(tournamentPreferences.getPlayerCount())
    }

    private fun showSheet(sheet: BankSheet?) {
        _uiState.update { it.copy(sheet = sheet) }
    }

    // Recording, with Undo -----------------------------------------------------------------------

    private class UndoEntry(val before: BankSnapshot, val message: String)

    /** What an action can change: everything recorded except names, and the settle-up's ticks. */
    private data class BankSnapshot(val players: List<PlayerData>, val eliminationOrder: List<Int>, val settlePaid: Set<Transfer>)

    /**
     * Applies [change] to the players and the elimination order, saves the result, and offers Undo
     * with [message]. Undo restores the state from before the change.
     */
    private fun record(message: String, change: (List<PlayerData>, List<Int>) -> Pair<List<PlayerData>, List<Int>>) {
        val state = _uiState.value
        val (players, order) = change(state.players, state.eliminationOrder)
        commit(message, BankSnapshot(players, order, state.settlePaid))
    }

    /** Saves [after] and offers Undo with [message], unless it changes nothing. */
    private fun commit(message: String, after: BankSnapshot) {
        val state = _uiState.value
        val before = BankSnapshot(state.players, state.eliminationOrder, state.settlePaid)
        if (after == before) return
        val entry = UndoEntry(before, message)
        undoStack.addLast(entry)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        save(after)
        offerUndo(entry)
    }

    /** State first, then preferences: a write bumps the Bank revision, and its reload must find nothing new. */
    private fun save(after: BankSnapshot) {
        val before = _uiState.value.players.associateBy { it.id }
        ownWrites++
        try {
            _uiState.update {
                it.copy(players = after.players, eliminationOrder = after.eliminationOrder, settlePaid = after.settlePaid)
            }
            after.players.forEach { player -> persist(before[player.id], player) }
            val order = after.eliminationOrder
            if (order != bankPreferences.getEliminationOrder()) bankPreferences.saveEliminationOrder(order)
            if (after.settlePaid != bankPreferences.getSettlePaid()) bankPreferences.saveSettlePaid(after.settlePaid)
        } finally {
            ownWrites--
        }
        updateCalculations()
    }

    private fun persist(before: PlayerData?, after: PlayerData) {
        val id = after.id
        if (before?.buyIn != after.buyIn) bankPreferences.savePlayerBuyInStatus(id, after.buyIn)
        if (before?.out != after.out) bankPreferences.savePlayerOutStatus(id, after.out)
        if (before?.paidOut != after.paidOut) bankPreferences.savePlayerPayedOutStatus(id, after.paidOut)
        if (before?.eliminatedBy != after.eliminatedBy) bankPreferences.savePlayerEliminatedBy(id, after.eliminatedBy)
        if (before?.outLevel != after.outLevel) bankPreferences.savePlayerOutLevel(id, after.outLevel)
        if (before?.rebuyPrices != after.rebuyPrices) bankPreferences.savePlayerRebuyPrices(id, after.rebuyPrices)
        if (before?.addOnPrices != after.addOnPrices) bankPreferences.savePlayerAddonPrices(id, after.addOnPrices)
        if (before?.bountyDrawCents != after.bountyDrawCents) bankPreferences.savePlayerBountyDraw(id, after.bountyDrawCents)
    }

    /** One snackbar at a time: a new action replaces the last one's snackbar (it stays in the history). */
    private fun offerUndo(entry: UndoEntry) {
        snackbar?.cancel()
        snackbar = viewModelScope.launch {
            if (feedback.showUndo(entry.message)) undo(expected = entry)
        }
    }

    /**
     * Takes back the newest action. From the snackbar ([expected] set), only if that action is still
     * the newest: the top bar's Undo may have taken it back already.
     */
    private fun undo(expected: UndoEntry?) {
        val entry = undoStack.lastOrNull()?.takeIf { expected == null || it === expected } ?: return
        undoStack.removeLast()
        if (expected == null) {
            snackbar?.cancel()
            snackbar = null
        }
        // Names may have changed since; keep today's.
        val names = _uiState.value.players.associate { it.id to it.name }
        val restored = entry.before.players.map { it.copy(name = names[it.id] ?: it.name) }
        save(entry.before.copy(players = restored))
        // A mystery envelope taken back is back in the pool: its reveal goes too
        if (_uiState.value.sheet is BankSheet.Envelope) showSheet(null)
    }

    private fun forgetHistory() {
        undoStack.clear()
        snackbar?.cancel()
        snackbar = null
        _uiState.update { it.copy(undoLabel = null) }
    }

    // Calculations --------------------------------------------------------------------------------

    private fun updateWindows(clock: ClockStatus, cutoffLevel: Int) {
        _uiState.update {
            it.copy(
                clock = clock,
                rebuyWindow = PurchaseWindow.rebuys(cutoffLevel, clock),
                addOnWindow = PurchaseWindow.addOns(cutoffLevel, clock)
            )
        }
        updateCalculations()
    }

    private fun PlayerData.toBankPlayer() = BankPlayer(
        id = id,
        boughtIn = buyIn,
        paidOut = paidOut,
        eliminatedBy = eliminatedBy,
        rebuyPricesCents = rebuyPrices,
        addOnPricesCents = addOnPrices,
        bountyDrawCents = bountyDrawCents
    )

    /** Recomputes every amount and every row from one settlement of the current state. */
    private fun updateCalculations() {
        val state = _uiState.value
        val config = tournamentPreferences.getCurrentTournamentConfig()
        val bankPlayers = state.players.map { it.toBankPlayer() }
        val result = settleTournament(
            players = bankPlayers,
            eliminationOrder = state.eliminationOrder,
            money = config.money,
            weights = config.payoutWeights,
            rounding = config.payoutRounding
        )
        settlement = result
        val rows = buildBankRows(
            BankRowInput(state.players, state.eliminationOrder, result, config.money, state.rebuyWindow, state.addOnWindow)
        )
        val canReset = !bankPreferences.isInDefaultState(state.players.size)
        val plan = settleUp(result, bankPlayers, config.money)
        val ticks = keptTicks(state.settlePaid, plan)
        _uiState.update {
            it.copy(
                pool = result.pool,
                totalPaidInCents = result.paidInCents,
                totalPaidOutCents = result.paidOutCents,
                money = config.money,
                knockoutCounts = result.players.filter { owed -> owed.knockouts > 0 }
                    .associate { owed -> owed.playerId to owed.knockouts },
                payoutEligiblePlayerIds = result.players.filter { owed -> owed.winningsCents > 0L }
                    .map { owed -> owed.playerId }
                    .toSet(),
                payoutTable = result.payoutTable,
                payoutSettings = tournamentPreferences.getPayoutSettings(),
                placeByPlayer = result.standings.placeByPlayer,
                championId = result.championId,
                rows = rows,
                canReset = canReset,
                undoLabel = undoStack.lastOrNull()?.message,
                envelopesLeft = result.envelopesLeft,
                settleUp = plan?.let { settleUpModel(it, result, state.players, config.money) },
                settlePaid = ticks,
                sheet = it.sheet.takeUnless { sheet -> sheet == BankSheet.SettleUp && plan == null }
            )
        }
    }

    /**
     * The ticks for payments [plan] still lists; the others go (their payment changed, or the night
     * isn't over any more), from storage too. Undo still has them.
     */
    private fun keptTicks(ticks: Set<Transfer>, plan: SettleUp?): Set<Transfer> {
        val kept = plan?.transfers?.let { transfers -> ticks.filterTo(mutableSetOf()) { it in transfers } }.orEmpty()
        if (kept != ticks) bankPreferences.saveSettlePaid(kept)
        return kept
    }

    private fun settleUpModel(plan: SettleUp, result: Settlement, players: List<PlayerData>, money: MoneySettings) =
        SettleUpModel(
            transfers = plan.transfers,
            nights = players.map { player ->
                val owed = result.forPlayer(player.id)
                PlayerNight(player.id, player.name, owed?.costCents ?: money.entryCents, owed?.winningsCents ?: 0L)
            },
            foodCents = result.pool.foodCents
        )

    private companion object {
        /** How many actions Undo can take back. */
        const val MAX_UNDO = 20
    }
}

private fun List<PlayerData>.replace(player: PlayerData): List<PlayerData> = map { if (it.id == player.id) player else it }

private fun PlayerData.withPrices(kind: Purchase, prices: List<Long>): PlayerData =
    if (kind == Purchase.REBUY) copy(rebuyPrices = prices) else copy(addOnPrices = prices)

/** Who [player]'s knockout is credited to, if anyone still at the table counts. */
private fun BankUiState.knockoutCredit(player: PlayerData): Int? =
    player.eliminatedBy?.takeIf { id -> id != player.id && players.any { it.id == id } }
