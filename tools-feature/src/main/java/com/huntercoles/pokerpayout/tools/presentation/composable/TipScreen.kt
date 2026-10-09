package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.TipIntent
import com.huntercoles.pokerpayout.tools.presentation.TipUiState
import com.huntercoles.pokerpayout.tools.presentation.TipViewModel
import com.huntercoles.pokerpayout.tools.tip.TipCoin
import com.huntercoles.pokerpayout.tools.tip.TipLink

/**
 * The Tip the dealer route (Tools, and the card on the Payouts tab): [TipContent], with the copying
 * (the clipboard) and the opening (the browser, through an intent: the app has no internet
 * permission) done here.
 */
@Composable
fun TipRoute(onBack: () -> Unit, viewModel: TipViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TipContent(
        state = state,
        onBack = onBack,
        onCopy = { coin ->
            copyAddress(context, context.getString(coin.label), coin.address)
            viewModel.acceptIntent(TipIntent.Copied(coin))
        },
        onOpen = { link -> viewModel.acceptIntent(TipIntent.Opened(link, openInBrowser(context, link.url))) },
    )
}

/** Puts [address] on the clipboard, named [label] (Android 13 and up shows it was copied). */
private fun copyAddress(context: Context, label: String, address: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, address))
}

/** Hands [url] to the browser; false when no app on the phone opens web pages. */
private fun openInBrowser(context: Context, url: String): Boolean = try {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(view)
    true
} catch (ignored: ActivityNotFoundException) {
    false
}

/**
 * Tip the dealer (S25, PP-112), stateless: why a tip helps (and that nothing changes without one),
 * the donation page, each address with its QR code and a Copy button, how to check an address, and
 * the free ways to help.
 */
@Composable
fun TipContent(
    state: TipUiState,
    onBack: () -> Unit,
    onCopy: (TipCoin) -> Unit,
    onOpen: (TipLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.tip_title),
            subtitle = stringResource(R.string.tip_subtitle),
            onBack = onBack,
        )
        HistoryPane {
            Text(
                text = stringResource(R.string.tip_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = PokerColors.CardWhite,
            )
            TipCard(
                icon = PokerIcons.OpenInNew,
                heading = stringResource(R.string.tip_page_heading),
                body = stringResource(R.string.tip_page_body),
            ) {
                PokerButton(
                    text = stringResource(R.string.tip_page_open),
                    onClick = { onOpen(TipLink.DONATION_PAGE) },
                    icon = PokerIcons.OpenInNew,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.noBrowser == TipLink.DONATION_PAGE) NoBrowserNote(stringResource(R.string.tip_no_browser_addresses))
            }
            TipCoin.entries.forEach { coin -> CoinCard(coin, copied = state.copied == coin, onCopy = { onCopy(coin) }) }
            Text(
                text = stringResource(R.string.tip_check),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
            FreeWaysToHelp(state.noBrowser.takeUnless { it == TipLink.DONATION_PAGE }, onOpen)
            Text(
                text = stringResource(R.string.tip_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
        }
    }
}

/** A felt card with an icon tile and a heading, as on Backup. */
@Composable
private fun TipCard(icon: ImageVector, heading: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(PokerDimens.MinTouch)
                    .background(PokerColors.DarkGreen, RoundedCornerShape(PokerDimens.CornerControl)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PokerColors.PokerGold)
            }
            Text(
                text = heading,
                style = ToolTitle,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        content()
    }
}

/**
 * One coin: its name, the repository's QR code on white (a wallet scans it straight off the
 * screen), the address in groups of four, and Copy, which says when it has copied.
 */
@Composable
private fun CoinCard(coin: TipCoin, copied: Boolean, onCopy: () -> Unit) {
    val name = stringResource(coin.coinName)
    val copiedDescription = stringResource(R.string.tip_copied_description, name)
    val copyDescription = stringResource(R.string.tip_copy_description, name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = stringResource(coin.label),
            style = ToolTitle,
            color = PokerColors.CardWhite,
            modifier = Modifier.semantics { heading() },
        )
        val code = ImageBitmap.imageResource(coin.qr)
        Image(
            bitmap = code,
            contentDescription = stringResource(R.string.tip_qr_description, name),
            // One pixel a module in the file, drawn without smoothing at a whole number of screen
            // pixels a module: every module the same size, sharp, as a wallet's camera wants it
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(wholePixelSide(code.width))
                .clip(RoundedCornerShape(PokerDimens.CornerControl)),
        )
        Text(
            text = coin.grouped,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = PokerColors.CardWhite,
        )
        PokerButton(
            text = stringResource(if (copied) R.string.tip_copied else R.string.tip_copy),
            onClick = onCopy,
            variant = PokerButtonVariant.Secondary,
            icon = if (copied) PokerIcons.Check else PokerIcons.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = if (copied) copiedDescription else copyDescription },
        )
    }
}

/** Telling a friend, a star, an idea: help that costs nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeWaysToHelp(noBrowser: TipLink?, onOpen: (TipLink) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall)) {
        PokerEyebrow(stringResource(R.string.tip_free_heading), modifier = Modifier.semantics { heading() })
        Text(
            text = stringResource(R.string.tip_free_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PokerButton(
                text = stringResource(R.string.tip_star),
                onClick = { onOpen(TipLink.REPOSITORY) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.OpenInNew,
            )
            PokerButton(
                text = stringResource(R.string.tip_idea),
                onClick = { onOpen(TipLink.IDEAS) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.OpenInNew,
            )
        }
        if (noBrowser != null) NoBrowserNote(stringResource(R.string.tip_no_browser))
    }
}

/** Why a page didn't open. */
@Composable
private fun NoBrowserNote(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(PokerIcons.Info, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The largest side up to [QrSize] that gives each of a code's [pixels] a whole number of screen pixels. */
@Composable
private fun wholePixelSide(pixels: Int): Dp = with(LocalDensity.current) {
    ((QrSize.toPx() / pixels).toInt().coerceAtLeast(1) * pixels).toDp()
}

/** Big enough for any wallet to scan from arm's length; fits the smallest phone at the largest font. */
private val QrSize = 184.dp
