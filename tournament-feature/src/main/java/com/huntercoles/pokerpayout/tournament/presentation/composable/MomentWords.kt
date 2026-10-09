package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.presentation.MomentBanner

// PP-111: what each big moment says on the clock, and its icon.

@Composable
internal fun momentTitle(moment: BigMoment): String = stringResource(
    when (moment) {
        BigMoment.BUBBLE -> R.string.moment_bubble_title
        BigMoment.FINAL_TABLE -> R.string.moment_final_table_title
        BigMoment.IN_THE_MONEY -> R.string.moment_in_the_money_title
        BigMoment.HEADS_UP -> R.string.moment_heads_up_title
        BigMoment.CHAMPION -> R.string.winner_eyebrow
    },
)

@Composable
internal fun momentLine(banner: MomentBanner): String = when (banner.moment) {
    BigMoment.BUBBLE -> stringResource(R.string.moment_bubble_line)
    BigMoment.FINAL_TABLE -> pluralStringResource(R.plurals.moment_final_table_line, banner.playersLeft, banner.playersLeft)
    BigMoment.IN_THE_MONEY -> stringResource(R.string.moment_in_the_money_line, money(banner.lowestPrizeCents))
    BigMoment.HEADS_UP -> {
        val first = banner.names.getOrNull(0)
        val second = banner.names.getOrNull(1)
        if (first != null && second != null) {
            stringResource(R.string.moment_heads_up_line, first, second)
        } else {
            stringResource(R.string.moment_heads_up_line_unnamed)
        }
    }
    BigMoment.CHAMPION -> banner.names.firstOrNull()?.let { stringResource(R.string.champion_card, it) }.orEmpty()
}

internal fun momentIcon(moment: BigMoment): ImageVector = when (moment) {
    BigMoment.BUBBLE -> PokerIcons.Skull
    BigMoment.FINAL_TABLE -> PokerIcons.Seat
    BigMoment.IN_THE_MONEY -> PokerIcons.Wallet
    BigMoment.HEADS_UP -> PokerIcons.Cards
    BigMoment.CHAMPION -> PokerIcons.Crown
}
