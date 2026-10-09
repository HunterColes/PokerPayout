package com.huntercoles.pokerpayout.tools.table

/** What one player put into the pot this hand, in chips, and whether they have folded. */
data class Contribution(val chips: Long, val folded: Boolean = false)

/**
 * One pot: every player's chips above [from] and up to [upTo], and who can win it.
 *
 * @property players everyone with chips in it, folded or not, by their index in the contributions.
 * @property eligible the players still in who can win it, by index; never empty.
 */
data class Pot(val chips: Long, val from: Long, val upTo: Long, val players: List<Int>, val eligible: List<Int>)

/** Chips one player bet that nobody matched: they go back to [player]. */
data class Uncalled(val player: Int, val chips: Long)

/** How the chips in a hand split into pots. */
sealed interface PotSplit {
    /** Nobody has put anything in yet. */
    data object Empty : PotSplit

    /** Everyone with chips in has folded, so nobody can win them. */
    data object AllFolded : PotSplit

    /**
     * The main pot first, then the side pots in order. Their chips plus [uncalled] add up to
     * [total], everything put in.
     */
    data class Pots(val pots: List<Pot>, val uncalled: Uncalled?, val total: Long) : PotSplit
}

/**
 * Main and side pots, in exact whole chips.
 *
 * Every amount a player put in is a level. Between one level and the next, everyone who reached
 * the higher one puts in the difference, and the players among them who haven't folded can win it.
 * Then:
 * - the top layer, when only one player reached it, is a bet nobody matched: it goes back;
 * - a layer that nobody still in reached (only folded players put chips there) joins the pot below;
 * - layers that the same players can win are one pot.
 *
 * Players who put nothing in aren't in the hand.
 */
object SidePots {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 10

    fun split(contributions: List<Contribution>): PotSplit {
        val inHand = contributions.indices.filter { contributions[it].chips > 0 }
        if (inHand.isEmpty()) return PotSplit.Empty
        if (inHand.all { contributions[it].folded }) return PotSplit.AllFolded
        val layers = layers(contributions, inHand)
        val top = layers.last()
        val uncalled = top.players.singleOrNull()?.let { Uncalled(it, top.chips) }
        val contested = if (uncalled != null) layers.dropLast(1) else layers
        return PotSplit.Pots(pots = merge(contested), uncalled = uncalled, total = inHand.sumOf { contributions[it].chips })
    }

    /** One layer per level, lowest first; the lowest takes in everyone in the hand. */
    private fun layers(contributions: List<Contribution>, inHand: List<Int>): List<Pot> {
        var below = 0L
        return inHand.map { contributions[it].chips }.distinct().sorted().map { level ->
            val players = inHand.filter { contributions[it].chips >= level }
            val layer = Pot(
                chips = (level - below) * players.size,
                from = below,
                upTo = level,
                players = players,
                eligible = players.filterNot { contributions[it].folded },
            )
            below = level
            layer
        }
    }

    /**
     * Layers into pots: one nobody still in reached joins the pot below; one the same players can
     * win as the pot below extends it. The lowest layer always has someone still in (not everyone
     * folded), so there is always a pot below to join.
     */
    private fun merge(layers: List<Pot>): List<Pot> {
        val pots = mutableListOf<Pot>()
        layers.forEach { layer ->
            val below = pots.lastOrNull()
            if (below != null && (layer.eligible.isEmpty() || layer.eligible == below.eligible)) {
                pots[pots.lastIndex] = below.copy(chips = below.chips + layer.chips, upTo = layer.upTo)
            } else {
                pots += layer
            }
        }
        return pots
    }
}
