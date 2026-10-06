package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.MoneyField
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/**
 * One player's sheet: their name, every buy-in and top-up (one recorded by mistake can go), a top-up
 * at the amount they last bought in for (or any other), and what their chips came to at the end.
 * Each change applies at once with Undo; the sheet stays open for the next.
 */
@Composable
internal fun CashPlayerSheetContent(
    player: CashPlayer,
    state: CashUiState,
    onIntent: (CashIntent) -> Unit,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetTitle(player.name)
        NameField(name = player.name, onRename = { onIntent(CashIntent.Rename(player.id, it)) })
        PokerEyebrow(stringResource(R.string.cash_bought_in, formatMoney(player.inCents)))
        Column {
            val removable = player.buyInsCents.size > 1
            player.buyInsCents.forEachIndexed { index, cents ->
                BuyInLine(
                    label = if (index == 0) {
                        stringResource(R.string.cash_buy_in)
                    } else {
                        stringResource(R.string.cash_top_up_n, index)
                    },
                    cents = cents,
                    onRemove = { onIntent(CashIntent.RemoveBuyIn(player.id, index)) }.takeIf { removable },
                )
            }
        }
        TopUp(player, onTopUp = { onIntent(CashIntent.TopUp(player.id, it)) })
        PokerEyebrow(stringResource(R.string.cash_at_the_end))
        MoneyField(
            valueCents = player.cashOutCents,
            label = stringResource(R.string.cash_cash_out),
            onCommit = { onIntent(CashIntent.SetCashOut(player.id, it)) },
            supportingText = cashOutHelp(state, player),
            description = stringResource(R.string.cash_cash_out_for, player.name),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PokerButton(
                text = stringResource(R.string.cash_remove_player, player.name),
                onClick = { onIntent(CashIntent.RemovePlayer(player.id)) },
                variant = PokerButtonVariant.DestructiveOutline,
                size = PokerButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
            PokerButton(
                text = stringResource(R.string.cash_done),
                onClick = {
                    focusManager.clearFocus()
                    onDone()
                },
                size = PokerButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Under the count: the night's result once counted, or that it can wait. */
@Composable
private fun cashOutHelp(state: CashUiState, player: CashPlayer): String {
    val net = state.netFor(player)
    return when {
        net == null -> stringResource(R.string.cash_cash_out_empty)
        net > 0L -> stringResource(R.string.cash_cash_out_up, formatMoney(net))
        net < 0L -> stringResource(R.string.cash_cash_out_down, formatMoney(-net))
        else -> stringResource(R.string.cash_cash_out_even)
    }
}

@Composable
private fun BuyInLine(label: String, cents: Long, onRemove: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk, modifier = Modifier.weight(1f))
        Text(formatMoney(cents), style = PokerType.NumberM, color = PokerColors.CardWhite, softWrap = false)
        if (onRemove != null) {
            PokerIconButton(
                icon = PokerIcons.Close,
                contentDescription = stringResource(R.string.cash_remove_entry, label, formatMoney(cents)),
                onClick = onRemove,
            )
        } else {
            Spacer(Modifier.width(PokerDimens.MinTouch))
        }
    }
}

/**
 * The top-up amount (the player's last buy-in to start with) and its button. Side by side on
 * phones; stacked on small ones and at large font sizes, so neither gets squeezed.
 */
@Composable
private fun TopUp(player: CashPlayer, onTopUp: (Long) -> Unit) {
    var amount by rememberSaveable(player.id) { mutableStateOf(player.buyInsCents.lastOrNull()) }
    val field = @Composable { modifier: Modifier ->
        MoneyField(
            valueCents = amount,
            label = stringResource(R.string.cash_top_up_amount),
            onValueChange = { amount = it },
            description = stringResource(R.string.cash_top_up_field, player.name),
            modifier = modifier,
        )
    }
    val valid = (amount ?: 0L) > 0L
    val button = @Composable { modifier: Modifier ->
        PokerButton(
            text = amount?.takeIf { valid }?.let { stringResource(R.string.cash_top_up_for, formatMoney(it)) }
                ?: stringResource(R.string.cash_top_up),
            onClick = { amount?.let(onTopUp) },
            variant = PokerButtonVariant.Secondary,
            icon = PokerIcons.Plus,
            enabled = valid,
            modifier = modifier,
        )
    }
    val roomy = LocalWidthClass.current != WidthClass.Small && LocalDensity.current.fontScale <= SIDE_BY_SIDE_FONT_SCALE
    if (roomy) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            field(Modifier.weight(1f))
            button(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.fillMaxWidth())
            button(Modifier.fillMaxWidth())
        }
    }
}

private const val SIDE_BY_SIDE_FONT_SCALE = 1.3f

/** Names are words: the body face, not the number face. */
private val NameStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)

/**
 * A name, saved when the field is left (focus moves, Done, Enter, or the sheet closes), not on every
 * key. A blank name isn't saved.
 */
@Composable
private fun NameField(name: String, onRename: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(name) { mutableStateOf(name) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val latestName by rememberUpdatedState(name)
    val latestOnRename by rememberUpdatedState(onRename)
    // Typed since the last save: what makes leaving the field a save, once.
    var edited by remember { mutableStateOf(false) }

    fun commit() {
        if (!edited) return
        edited = false
        if (text.isNotBlank() && text.trim() != latestName) latestOnRename(text)
    }

    LaunchedEffect(focused) {
        if (!focused) commit()
    }
    DisposableEffect(Unit) {
        onDispose { commit() }
    }
    val label = stringResource(R.string.cash_name)
    PokerField(
        value = text,
        onValueChange = {
            text = it
            edited = true
        },
        label = label,
        keyboardType = KeyboardType.Text,
        textStyle = NameStyle,
        interactionSource = interactions,
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        fieldModifier = Modifier
            .leaveOnHardwareEnter {
                commit()
                focusManager.clearFocus(force = true)
            }
            .semantics { contentDescription = label },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = PokerType.Title.copy(fontSize = 30.sp, lineHeight = 34.sp),
        color = PokerColors.CardWhite,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * The next player: a name (empty gives "Player N") and their buy-in, which starts at what the last
 * player bought in for.
 */
@Composable
internal fun CashAddPlayerSheetContent(state: CashUiState, onIntent: (CashIntent) -> Unit, onDismiss: () -> Unit) {
    val defaultName = stringResource(R.string.cash_default_name, state.players.size + 1)
    var name by rememberSaveable { mutableStateOf("") }
    var buyIn by rememberSaveable { mutableStateOf(state.suggestedBuyInCents) }
    val focusManager = LocalFocusManager.current
    val nameLabel = stringResource(R.string.cash_name)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetTitle(stringResource(R.string.cash_add_title))
        PokerField(
            value = name,
            onValueChange = { name = it },
            label = nameLabel,
            supportingText = stringResource(R.string.cash_add_name_help, defaultName),
            keyboardType = KeyboardType.Text,
            textStyle = NameStyle,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            fieldModifier = Modifier
                .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
                .semantics { contentDescription = nameLabel },
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyField(
            valueCents = buyIn,
            label = stringResource(R.string.cash_buy_in),
            onValueChange = { buyIn = it },
            modifier = Modifier.fillMaxWidth(),
        )
        SheetButtons(
            dismissLabel = stringResource(R.string.cash_cancel),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.cash_add_confirm, name.trim().ifEmpty { defaultName }),
            onConfirm = {
                focusManager.clearFocus()
                buyIn?.let { onIntent(CashIntent.AddPlayer(name, it)) }
            },
            confirmEnabled = (buyIn ?: 0L) > 0L,
            confirmIcon = PokerIcons.Plus,
        )
    }
}
