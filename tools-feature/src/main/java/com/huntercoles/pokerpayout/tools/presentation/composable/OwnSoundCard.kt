package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsIntent
import com.huntercoles.pokerpayout.tools.presentation.CustomSoundUi
import kotlin.math.roundToLong

/**
 * Cue sounds (S18), the host's own sound for the changes (#22): on or off (on with no file opens the
 * picker), then, with it on, the file's length, the peak and end, and the preview with its bar;
 * Choose another file and Stop using it whenever a file is kept. A file gone since the last cue, or
 * a pick that can't be played, says so in one line.
 */
@Composable
internal fun OwnSoundCard(
    custom: CustomSoundUi,
    packName: String,
    onIntent: (CueSoundsIntent) -> Unit,
    onPickFile: () -> Unit,
) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val sound = custom.sound
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PokerColors.FeltGreen)
            .then(if (custom.on) Modifier.border(1.dp, PokerColors.DarkGold, shape) else Modifier)
            .padding(PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
    ) {
        SwitchRow(
            title = stringResource(R.string.cue_custom_title),
            description = stringResource(
                if (custom.on) R.string.cue_custom_on_description else R.string.cue_custom_off_description,
                packName,
            ),
            checked = custom.on,
            onChange = { on -> if (on && sound == null) onPickFile() else onIntent(CueSoundsIntent.SetCustomOn(on)) },
        )
        if (custom.missing && sound != null) Notice(stringResource(R.string.cue_custom_missing, packName))
        if (custom.refused) Notice(stringResource(R.string.cue_custom_refused))
        if (custom.on && sound != null) OwnSoundDetails(sound = sound, custom = custom, onIntent = onIntent)
        if (sound != null || custom.refused) {
            FileActions(hasFile = sound != null, onPickFile = onPickFile, onStop = { onIntent(CueSoundsIntent.StopCustom) })
        }
    }
}

/** The kept file's length, its peak, its end (or to the end), and the preview bar with its button. */
@Composable
private fun OwnSoundDetails(sound: CustomCueSound, custom: CustomSoundUi, onIntent: (CueSoundsIntent) -> Unit) {
    HorizontalDivider(color = PokerColors.FeltLine)
    Text(
        text = stringResource(R.string.cue_custom_length, seconds(sound.lengthMillis)),
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
    )
    SecondsSlider(
        label = stringResource(R.string.cue_custom_peak),
        hint = stringResource(R.string.cue_custom_peak_hint),
        millis = sound.peakMillis,
        range = 0L..sound.lengthMillis,
        onMillis = { onIntent(CueSoundsIntent.SetCustomPeak(it)) },
    )
    SwitchRow(
        title = stringResource(R.string.cue_custom_to_end),
        description = stringResource(R.string.cue_custom_to_end_hint),
        checked = sound.endMillis == 0L,
        onChange = { toEnd -> onIntent(CueSoundsIntent.SetCustomEnd(if (toEnd) 0 else sound.lengthMillis)) },
    )
    if (sound.endMillis > 0) {
        SecondsSlider(
            label = stringResource(R.string.cue_custom_end),
            hint = stringResource(R.string.cue_custom_end_hint),
            millis = sound.endMillis,
            range = sound.peakMillis..sound.lengthMillis,
            onMillis = { onIntent(CueSoundsIntent.SetCustomEnd(it)) },
        )
    }
    HorizontalDivider(color = PokerColors.FeltLine)
    PreviewBar(barMillis = custom.barMillis, markMillis = custom.markMillis, run = custom.previewRun)
    PokerButton(
        text = stringResource(R.string.cue_custom_preview),
        onClick = { onIntent(CueSoundsIntent.PreviewCustom) },
        icon = PokerIcons.Play,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Choose another file, and (with one kept) Stop using it; they wrap at phone width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileActions(hasFile: Boolean, onPickFile: () -> Unit, onStop: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
    ) {
        PokerButton(
            text = stringResource(R.string.cue_custom_choose),
            onClick = onPickFile,
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.FolderOpen,
        )
        if (hasFile) {
            PokerButton(
                text = stringResource(R.string.cue_custom_stop),
                onClick = onStop,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
        }
    }
}

/** One plain line the host should see: an info mark and the words. */
@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
    ) {
        Icon(PokerIcons.Info, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A point in the file, in 0.1 s steps: its name, what it does and its value ("Peak 8.0 s") over a
 * slider like the volume's. TalkBack hears the name and the value in seconds.
 */
@Composable
private fun SecondsSlider(label: String, hint: String, millis: Long, range: LongRange, onMillis: (Long) -> Unit) {
    val spoken = stringResource(R.string.cue_custom_seconds_spoken, millis / MILLIS_PER_SECOND)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = ToolTitle, color = PokerColors.CardWhite)
                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
            }
            Text(
                text = seconds(millis),
                style = PokerType.NumberM,
                color = PokerColors.PokerGold,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
        val first = range.first.toFloat()
        val last = range.last.toFloat().coerceAtLeast(first + 1f)
        Slider(
            value = millis.toFloat().coerceIn(first, last),
            onValueChange = { value -> onMillis((value / STEP_MILLIS).roundToLong() * STEP_MILLIS.toLong()) },
            valueRange = first..last,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.MinTouch)
                .semantics {
                    contentDescription = label
                    stateDescription = spoken
                },
            thumb = { Box(Modifier.size(22.dp).background(PokerColors.PokerGold, CircleShape)) },
            track = { sliderState -> VolumeTrack(sliderState, PokerColors.PokerGold) },
        )
    }
}

/**
 * The preview: a bar the length of what plays, with a "change" mark at the peak. Each new [run]
 * fills it from the start in real time, so the host sees the peak land on the mark.
 */
@Composable
private fun PreviewBar(barMillis: Long, markMillis: Long, run: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(run, barMillis) {
        if (run > 0 && barMillis > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = barMillis.toInt(), easing = LinearEasing))
        }
    }
    val mark = if (barMillis > 0) (markMillis.toFloat() / barMillis).coerceIn(0f, 1f) else 0f
    val markText = seconds(markMillis)
    val description = stringResource(R.string.cue_custom_bar, markText)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.value, 0f..1f)
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(MarkHeight)) {
            val barHeight = BarHeight.toPx()
            val barTop = (size.height - barHeight) / 2
            val radius = CornerRadius(barHeight / 2)
            drawRoundRect(PokerColors.FeltDeep, Offset(0f, barTop), Size(size.width, barHeight), radius)
            drawRoundRect(PokerColors.PokerGold, Offset(0f, barTop), Size(size.width * progress.value, barHeight), radius)
            val half = MarkWidth.toPx() / 2
            val x = (size.width * mark).coerceIn(half, size.width - half)
            drawRect(PokerColors.CardWhite, Offset(x - half, 0f), Size(MarkWidth.toPx(), size.height))
        }
        MarkLabel(fraction = mark, text = stringResource(R.string.cue_custom_change, markText))
    }
}

/** The mark's words, centred under it and kept inside the bar's ends. */
@Composable
private fun MarkLabel(fraction: Float, text: String) {
    Layout(
        content = { Text(text = text, style = MaterialTheme.typography.labelMedium, color = PokerColors.CardWhite) },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val label = measurables.first().measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, label.height) {
            val room = (constraints.maxWidth - label.width).coerceAtLeast(0)
            label.place((constraints.maxWidth * fraction - label.width / 2f).toInt().coerceIn(0, room), 0)
        }
    }
}

/** Seconds with one decimal ("8.0 s"). */
@Composable
private fun seconds(millis: Long): String = stringResource(R.string.cue_custom_seconds, millis / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1000f
private const val STEP_MILLIS = 100f
private val BarHeight = 8.dp
private val MarkHeight = 20.dp
private val MarkWidth = 3.dp
