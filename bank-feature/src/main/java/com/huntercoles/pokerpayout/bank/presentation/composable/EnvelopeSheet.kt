package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** The mystery-bounty reveal (PP-035) as a modal bottom sheet. */
@Composable
internal fun EnvelopeSheet(sheet: BankSheet.Envelope, onDismiss: () -> Unit) {
    PokerSheet(onDismissRequest = onDismiss) {
        EnvelopeSheetContent(sheet, onDismiss)
    }
}

/**
 * The envelope [BankSheet.Envelope.eliminatorName] drew, opened: the amount grows in on a gold card
 * (under Reduce motion it is simply there), then how many envelopes are left and, once the night is
 * over, what the champion takes. The knockout is already recorded; Undo on the snackbar puts the
 * envelope back in the pool.
 */
@Composable
internal fun EnvelopeSheetContent(sheet: BankSheet.Envelope, onDismiss: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    var opened by remember(sheet) { mutableStateOf(reducedMotion) }
    LaunchedEffect(sheet) { opened = true }
    val scale by animateFloatAsState(
        targetValue = if (opened) 1f else CLOSED_SCALE,
        animationSpec = tween(durationMillis = REVEAL_MILLIS),
        label = "envelope scale",
    )
    val fade by animateFloatAsState(
        targetValue = if (opened) 1f else 0f,
        animationSpec = tween(durationMillis = REVEAL_MILLIS),
        label = "envelope alpha",
    )
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(title = stringResource(R.string.bank_envelope_title, sheet.eliminatorName)) {
            PokerPill(stringResource(R.string.bank_envelope_pill), tone = PokerPillTone.Gold)
        }
        EnvelopeAmount(cents = sheet.cents, scale = scale, alpha = fade)
        Text(
            text = pluralStringResource(R.plurals.bank_envelope_from, sheet.envelopesLeft, sheet.victimName, sheet.envelopesLeft),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        sheet.championName?.let { champion ->
            Text(
                text = pluralStringResource(
                    R.plurals.bank_envelope_champion,
                    sheet.envelopesLeft,
                    champion,
                    sheet.envelopesLeft,
                    formatMoney(sheet.championCents),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
        }
        PokerButton(
            text = stringResource(R.string.bank_close),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The amount on the opened envelope's gold card, drawn at [scale] and [alpha] as it grows in. */
@Composable
private fun EnvelopeAmount(cents: Long, scale: Float, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CardHeight)
            .background(PokerColors.GoldWash, CardShape)
            .border(2.dp, PokerColors.PokerGold, CardShape)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatMoney(cents),
            style = PokerType.DisplayM,
            color = PokerColors.PokerGold,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** How small the amount starts before it grows in, and how long that takes. */
private const val CLOSED_SCALE = 0.6f
private const val REVEAL_MILLIS = 450
private val CardHeight = 112.dp
private val CardShape = RoundedCornerShape(PokerDimens.CornerCard)
