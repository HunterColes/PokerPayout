package com.huntercoles.pokerpayout.tools.dealers

import kotlin.random.Random

/** How a game is dealt: shared cards in the middle, cards of your own face up and down, or draws. */
enum class GameFamily { Flop, Stud, Draw }

/** Who takes the pot: the best hand, the best low, split between them, or split some other way. */
enum class GameSplit { High, Low, HiLo, Split }

/**
 * The games the app knows, with their rules (in `strings_dealers_choice.xml`). The [id] is saved
 * with the wheel: never rename one. [onWheelAtFirst] games make up the wheel until the host changes it.
 */
enum class BuiltInGame(val id: String, val family: GameFamily, val split: GameSplit, val onWheelAtFirst: Boolean) {
    HoldEm("holdem", GameFamily.Flop, GameSplit.High, true),
    Omaha("omaha", GameFamily.Flop, GameSplit.High, true),
    OmahaHiLo("omaha_hilo", GameFamily.Flop, GameSplit.HiLo, false),
    BigO("big_o", GameFamily.Flop, GameSplit.HiLo, true),
    Stud("stud", GameFamily.Stud, GameSplit.High, true),
    StudHiLo("stud_hilo", GameFamily.Stud, GameSplit.HiLo, false),
    Razz("razz", GameFamily.Stud, GameSplit.Low, true),
    TripleDraw("triple_draw", GameFamily.Draw, GameSplit.Low, true),
    Badugi("badugi", GameFamily.Draw, GameSplit.Low, true),
    Pineapple("pineapple", GameFamily.Flop, GameSplit.High, true),
    CrazyPineapple("crazy_pineapple", GameFamily.Flop, GameSplit.High, true),
    FiveCardDraw("five_card_draw", GameFamily.Draw, GameSplit.High, false),
    ShortDeck("short_deck", GameFamily.Flop, GameSplit.High, false),
    Irish("irish", GameFamily.Flop, GameSplit.High, false),
    Courchevel("courchevel", GameFamily.Flop, GameSplit.High, false),
    FollowTheQueen("follow_the_queen", GameFamily.Stud, GameSplit.High, false),
    HighChicago("high_chicago", GameFamily.Stud, GameSplit.Split, false),
    ;

    companion object {
        fun byId(id: String): BuiltInGame? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One game the host can put on the wheel: one the app knows ([game], with rules) or a house game
 * they typed ([houseName]).
 */
data class GameChoice(val id: String, val game: BuiltInGame?, val houseName: String?, val onWheel: Boolean) {
    companion object {
        private const val HOUSE_PREFIX = "house:"

        fun builtIn(game: BuiltInGame, onWheel: Boolean) = GameChoice(game.id, game, null, onWheel)

        fun house(name: String, onWheel: Boolean) = GameChoice(houseId(name), null, name, onWheel)

        /** A house game's id: its name, the same whatever its capitals ("Kings Wild", "kings wild"). */
        fun houseId(name: String): String = HOUSE_PREFIX + name.trim().lowercase()
    }
}

/** Where the wheel stops: [index] on the wheel, and [landing] (-0.35 to 0.35) where in that slice from its middle. */
data class WheelStop(val index: Int, val landing: Float)

/** The wheel's draw. */
object GameWheel {
    /** Fewer games than this and there's nothing to choose between. */
    const val MIN_GAMES = 2

    /** The wheel never stops this close to the line between two slices, so nobody argues where it landed. */
    private const val MAX_LANDING = 0.35f

    /**
     * A game picked at random from [wheel] with [random], every game as likely, except that it never
     * picks [last] (the game just played) twice running while there is anything else.
     */
    fun spin(wheel: List<GameChoice>, last: String?, random: Random): WheelStop {
        require(wheel.size >= MIN_GAMES) { "The wheel needs at least $MIN_GAMES games" }
        val candidates = wheel.indices.filter { wheel[it].id != last }
        val index = candidates[random.nextInt(candidates.size)]
        val landing = (random.nextFloat() * 2 - 1) * MAX_LANDING
        return WheelStop(index, landing)
    }
}
