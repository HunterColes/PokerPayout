package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.utils.FormatUtils

private const val MAX_WEIGHT_VALUE = 999

/** What the editor previews amounts against. */
data class PayoutPreview(val prizePoolCents: Long, val playerCount: Int)

private data class WeightRowState(val position: Int, val weight: Int, val amountCents: Long, val isError: Boolean)

/**
 * Validates that weights are in strictly decreasing order
 */
internal fun isValidWeightChange(weights: List<Int>, index: Int, newWeight: Int): Boolean {
    val belowPrevious = index == 0 || newWeight < weights[index - 1]
    val aboveNext = index == weights.lastIndex || newWeight > weights[index + 1]
    return belowPrevious && aboveNext
}

internal fun detectInvalidWeights(weights: List<Int>): List<Boolean> {
    if (weights.isEmpty()) return emptyList()
    return weights.mapIndexed { index, weight ->
        val violatesPrev = index > 0 && weight >= weights[index - 1]
        val violatesNext = index < weights.lastIndex && weight <= weights[index + 1]
        violatesPrev || violatesNext
    }
}

/**
 * [weights] resized to [places] places: a preset's own table, or for hand-edited weights the last
 * places dropped or default weights appended.
 */
internal fun resizeWeights(weights: List<Int>, preset: PayoutPreset?, places: Int): List<Int> = when {
    preset != null -> preset.weightsFor(places)
    places > weights.size -> weights + (weights.size + 1..places).map {
        TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.getOrElse(it - 1) { 1 }
    }
    else -> weights.take(places)
}

/**
 * The payout structure editor: presets, rounding, how many places to pay (never more than there
 * are players), and the weight of each place with the amount it pays right now.
 */
@Composable
fun WeightsEditorDialog(
    current: PayoutSettings,
    preview: PayoutPreview,
    onSave: (PayoutSettings) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false
) {
    val maxPlaces = PayoutPlaces.maxFor(preview.playerCount)
    val weights = remember(current) {
        mutableStateListOf<Int>().apply { addAll(current.weights.take(maxPlaces)) }
    }
    var preset by remember(current) { mutableStateOf(current.preset) }
    var rounding by remember(current) { mutableStateOf(current.rounding) }
    val invalidPositions by remember(weights) { derivedStateOf { detectInvalidWeights(weights) } }
    val hasErrors = invalidPositions.any { it }
    val table by remember(weights, preview) {
        derivedStateOf {
            CalculatePayoutsUseCase()(preview.prizePoolCents, weights.toList(), preview.playerCount, rounding)
        }
    }

    fun setPlaces(places: Int) {
        val next = resizeWeights(weights.toList(), preset, places.coerceIn(1, maxPlaces))
        weights.clear()
        weights.addAll(next)
    }

    PokerDialog(onDismissRequest = onDismiss) {
        EditorHeader()
        PresetChips(
            selected = preset,
            enabled = !isLocked,
            onSelect = { chosen ->
                preset = chosen
                setPlaces(weights.size)
            }
        )
        RoundingChips(selected = rounding, enabled = !isLocked, onSelect = { rounding = it })
        PlacesStepper(
            places = weights.size,
            maxPlaces = maxPlaces,
            playerCount = preview.playerCount,
            enabled = !isLocked && !hasErrors,
            onChange = { setPlaces(it) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        WeightList(
            weights = weights,
            invalid = invalidPositions,
            table = table,
            isLocked = isLocked,
            onWeightChange = { index, newWeight ->
                if (newWeight > 0 && newWeight != weights[index]) {
                    weights[index] = newWeight
                    preset = null
                }
            }
        )
        EditorButtons(
            canSave = !hasErrors && !isLocked,
            onCancel = onDismiss,
            onSave = {
                onSave(PayoutSettings(weights = weights.toList(), preset = preset, rounding = rounding))
                onDismiss()
            }
        )
    }
}

@Composable
private fun EditorHeader() {
    Text(
        text = "⚖️ Payout Structure",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = PokerColors.PokerGold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Higher weights = larger payouts.",
        fontSize = 14.sp,
        color = PokerColors.CardWhite,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun PlacesStepper(places: Int, maxPlaces: Int, playerCount: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Places paid",
            color = PokerColors.CardWhite,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        StepButton(label = "−", description = "Pay one place fewer", enabled = enabled && places > 1) {
            onChange(places - 1)
        }
        Text(
            text = "$places",
            color = PokerColors.PokerGold,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        StepButton(label = "+", description = "Pay one more place", enabled = enabled && places < maxPlaces) {
            onChange(places + 1)
        }
    }
    Text(
        text = "Tip: pay about a third of the field, ${PayoutPlaces.recommended(playerCount)} " +
            "of $playerCount players. At most $maxPlaces.",
        color = PokerColors.CardWhite.copy(alpha = 0.7f),
        fontSize = 12.sp
    )
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, if (enabled) PokerColors.PokerGold else PokerColors.CardWhite.copy(alpha = 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PokerColors.PokerGold),
        modifier = Modifier
            .width(56.dp)
            .semantics { contentDescription = description }
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeightList(
    weights: List<Int>,
    invalid: List<Boolean>,
    table: PayoutTable,
    isLocked: Boolean,
    onWeightChange: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(weights) { index, weight ->
            WeightRow(
                row = WeightRowState(
                    position = index + 1,
                    weight = weight,
                    amountCents = table.amountFor(index + 1),
                    isError = invalid.getOrElse(index) { false }
                ),
                isLocked = isLocked,
                onWeightChange = { onWeightChange(index, it) }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Prize pool", color = PokerColors.CardWhite, fontWeight = FontWeight.Bold)
        Text(FormatUtils.formatCents(table.prizePoolCents), color = PokerColors.PokerGold, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun EditorButtons(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PokerColors.CardWhite)
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = PokerColors.AccentGreen,
                contentColor = PokerColors.DarkGreen,
                disabledContainerColor = PokerColors.CardWhite.copy(alpha = 0.3f),
                disabledContentColor = PokerColors.CardWhite.copy(alpha = 0.5f)
            )
        ) {
            Text("Save", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeightRow(row: WeightRowState, isLocked: Boolean, onWeightChange: (Int) -> Unit) {
    val position = row.position
    val trophy = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        THIRD_PLACE -> "🥉"
        else -> "🏅"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (position <= THIRD_PLACE) {
                PokerColors.AccentGreen.copy(alpha = 0.2f)
            } else {
                PokerColors.SurfaceSecondary
            }
        ),
        border = if (row.isError) BorderStroke(1.dp, PokerColors.ErrorRed) else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = trophy, fontSize = 18.sp)
            Text(
                text = ordinalOf(position),
                color = PokerColors.CardWhite,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = FormatUtils.formatCents(row.amountCents),
                color = PokerColors.PokerGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            PokerNumberField(
                value = row.weight,
                onValueChange = onWeightChange,
                label = "",
                minValue = 1,
                maxValue = MAX_WEIGHT_VALUE,
                isLocked = isLocked,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

private const val THIRD_PLACE = 3
