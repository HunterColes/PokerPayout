package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.Draw
import com.huntercoles.pokerpayout.tools.poker.HandDescription
import com.huntercoles.pokerpayout.tools.poker.MadeHand
import com.huntercoles.pokerpayout.tools.poker.MadeKind
import com.huntercoles.pokerpayout.tools.poker.OutGroup
import com.huntercoles.pokerpayout.tools.poker.rankOf
import com.huntercoles.pokerpayout.tools.poker.suitOf
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * "Q♣", "10♦": how a card is written in running text. Tens are "10", never "T". Each suit symbol
 * carries the text-style variation selector, so phones draw it in the text's colour rather than
 * as a red or grey emoji.
 */
internal fun cardText(card: Int): String = rankText(rankOf(card)) + SUIT_SYMBOLS[suitOf(card)] + TEXT_STYLE

/** "A", "K", "10", "2". */
internal fun rankText(rank: Int): String = if (rank == TEN) "10" else Cards.RANK_CHARS[rank].toString()

/** The core card face's model: rank "A".."2" (tens as "T", which the face shows as "10"), suit letter. */
internal fun Int.toPlayingCard(): PlayingCard {
    val text = Cards.format(this)
    return PlayingCard(rank = text.substring(0, 1), suit = text.substring(1))
}

private const val SUIT_SYMBOLS = "♣♦♥♠"

/** VARIATION SELECTOR-15: show the character before it as text, not emoji. */
private const val TEXT_STYLE = "︎"
private const val TEN = 8

/** Numbers as the odds screens show them: US digits, half-up rounding. */
internal object OddsFormat {
    private val symbols = DecimalFormatSymbols(Locale.US)

    private fun format(pattern: String, value: Number): String =
        DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(value)

    /** "56.1" */
    fun oneDecimal(x: Double): String = format("0.0", x)

    /** "56.06": exact results only; an estimate's second decimal would be noise. */
    fun twoDecimals(x: Double): String = format("0.00", x)

    /** "1,712,304" */
    fun grouped(n: Long): String = format("#,##0", n)
}

/**
 * Turns the engine's structured hand descriptions and outs into English from string resources:
 * "Nut flush draw + gutshot", "Overpair, queens", "9 spades, 3 aces, 3 kings and the Q♣".
 */
internal class OddsLabels(private val res: Resources) {
    private val names = res.getStringArray(R.array.odds_rank_names)
    private val plurals = res.getStringArray(R.array.odds_rank_plurals)
    private val suitPlurals = res.getStringArray(R.array.odds_suit_plurals)

    /** "Player 2" for seat index 1. */
    fun player(seat: Int): String = res.getString(R.string.odds_player, seat + 1)

    /** A rank as TalkBack says it: "Ace", "King", "10", "2". */
    fun rankName(rank: Int): String = SPOKEN_FACES[rank]?.let(res::getString) ?: rankText(rank)

    /** TalkBack's name for a card: "Ace of spades". */
    fun cardName(card: Int): String =
        res.getString(CoreR.string.card_name, rankName(rankOf(card)), res.getString(SUIT_NAMES[suitOf(card)]))

    /** A seat's hand label: "Overpair, queens"; with draws "Top pair, jacks + gutshot"; draws alone over high card. */
    fun hand(description: HandDescription): String {
        val made = description.made
        val draws = description.draws.map { res.getString(DRAW_LABELS.getValue(it)) }
        val weak = made.kind == MadeKind.HIGH_CARD || made.kind == MadeKind.BOARD_PAIR
        val pieces = if (weak && draws.isNotEmpty()) draws else listOf(made(made)) + draws
        return pieces.reduce { a, b -> res.getString(R.string.odds_hand_join, a, b) }.capitalised()
    }

    /** The made hand alone, lower case: "overpair, queens", "ace-king suited". */
    private fun made(made: MadeHand): String {
        val r = made.ranks
        return when (made.kind) {
            MadeKind.UNPAIRED -> res.getString(
                if (made.suited) R.string.odds_hand_suited else R.string.odds_hand_offsuit, names[r[0]], names[r[1]],
            )
            MadeKind.HIGH_CARD, MadeKind.STRAIGHT, MadeKind.FLUSH, MadeKind.STRAIGHT_FLUSH ->
                res.getString(MADE_LABELS.getValue(made.kind), names[r[0]])
            MadeKind.TWO_PAIR, MadeKind.FULL_HOUSE -> res.getString(MADE_LABELS.getValue(made.kind), plurals[r[0]], plurals[r[1]])
            MadeKind.ROYAL_FLUSH -> res.getString(R.string.odds_hand_royal_flush)
            else -> res.getString(MADE_LABELS.getValue(made.kind), plurals[r[0]])
        }
    }

    /** The made hand as a phrase inside a sentence: "ace-high", "a pair of queens", "a flush". */
    fun phrase(made: MadeHand): String {
        val r = made.ranks
        return when (made.kind) {
            MadeKind.HIGH_CARD, MadeKind.UNPAIRED -> res.getString(R.string.odds_phrase_high_card, names[r[0]])
            MadeKind.TWO_PAIR -> res.getString(R.string.odds_phrase_two_pair, plurals[r[0]], plurals[r[1]])
            MadeKind.SET -> res.getString(R.string.odds_phrase_set, plurals[r[0]])
            MadeKind.TRIPS, MadeKind.BOARD_TRIPS -> res.getString(R.string.odds_phrase_trips, plurals[r[0]])
            MadeKind.QUADS -> res.getString(R.string.odds_phrase_quads, plurals[r[0]])
            in PHRASES -> res.getString(PHRASES.getValue(made.kind))
            else -> res.getString(R.string.odds_phrase_pair, plurals[r[0]]) // every pair kind
        }
    }

    /** "9 spades, 3 aces, 3 kings and the Q♣". */
    fun outs(groups: List<OutGroup>): String = list(
        groups.map { g ->
            when (g) {
                is OutGroup.Suit -> res.getString(R.string.odds_group_suit, g.count, suitPlurals[g.suit])
                is OutGroup.Rank -> res.getString(R.string.odds_group_rank, g.count, plurals[g.rank])
                is OutGroup.Single -> res.getString(R.string.odds_group_card, cardText(g.card))
            }
        },
    )

    /** "a, b and c". */
    private fun list(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> res.getString(
            R.string.odds_list_pair,
            items.dropLast(1).reduce { a, b -> res.getString(R.string.odds_list_more, a, b) },
            items.last(),
        )
    }

    private fun String.capitalised(): String = replaceFirstChar { it.titlecase(Locale.US) }

    private companion object {
        val SUIT_NAMES = listOf(
            CoreR.string.card_suit_clubs,
            CoreR.string.card_suit_diamonds,
            CoreR.string.card_suit_hearts,
            CoreR.string.card_suit_spades,
        )

        /** The ranks read as words; the rest are read as numbers. */
        val SPOKEN_FACES = mapOf(
            Cards.ACE to CoreR.string.card_rank_ace,
            Cards.RANK_CHARS.indexOf('K') to CoreR.string.card_rank_king,
            Cards.RANK_CHARS.indexOf('Q') to CoreR.string.card_rank_queen,
            Cards.RANK_CHARS.indexOf('J') to CoreR.string.card_rank_jack,
        )
        val DRAW_LABELS = mapOf(
            Draw.NUT_FLUSH_DRAW to R.string.odds_draw_nut_flush,
            Draw.FLUSH_DRAW to R.string.odds_draw_flush,
            Draw.OPEN_ENDED to R.string.odds_draw_open_ended,
            Draw.DOUBLE_GUTSHOT to R.string.odds_draw_double_gutshot,
            Draw.GUTSHOT to R.string.odds_draw_gutshot,
        )
        val MADE_LABELS = mapOf(
            MadeKind.POCKET_PAIR to R.string.odds_hand_pocket_pair,
            MadeKind.HIGH_CARD to R.string.odds_hand_high_card,
            MadeKind.BOARD_PAIR to R.string.odds_hand_board_pair,
            MadeKind.OVERPAIR to R.string.odds_hand_overpair,
            MadeKind.TOP_PAIR to R.string.odds_hand_top_pair,
            MadeKind.SECOND_PAIR to R.string.odds_hand_second_pair,
            MadeKind.MIDDLE_PAIR to R.string.odds_hand_middle_pair,
            MadeKind.BOTTOM_PAIR to R.string.odds_hand_bottom_pair,
            MadeKind.TWO_PAIR to R.string.odds_hand_two_pair,
            MadeKind.SET to R.string.odds_hand_set,
            MadeKind.TRIPS to R.string.odds_hand_trips,
            MadeKind.BOARD_TRIPS to R.string.odds_hand_board_trips,
            MadeKind.STRAIGHT to R.string.odds_hand_straight,
            MadeKind.FLUSH to R.string.odds_hand_flush,
            MadeKind.FULL_HOUSE to R.string.odds_hand_full_house,
            MadeKind.QUADS to R.string.odds_hand_quads,
            MadeKind.STRAIGHT_FLUSH to R.string.odds_hand_straight_flush,
        )
        val PHRASES = mapOf(
            MadeKind.STRAIGHT to R.string.odds_phrase_straight,
            MadeKind.FLUSH to R.string.odds_phrase_flush,
            MadeKind.FULL_HOUSE to R.string.odds_phrase_full_house,
            MadeKind.STRAIGHT_FLUSH to R.string.odds_phrase_straight_flush,
            MadeKind.ROYAL_FLUSH to R.string.odds_hand_royal_flush,
        )
    }
}

/** The labels for the current configuration (they follow a locale change). */
@Composable
internal fun rememberOddsLabels(): OddsLabels {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { OddsLabels(resources) }
}
