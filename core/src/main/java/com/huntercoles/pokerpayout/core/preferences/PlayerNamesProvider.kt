package com.huntercoles.pokerpayout.core.preferences

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tonight's players by name, for features outside `bank-feature` (the seat draw, PP-036), so they
 * never import it.
 */
fun interface PlayerNamesProvider {
    /**
     * One name per player, in the Bank's order: the name typed in the Bank, or "Player N" where
     * none was. Empty when there are no players.
     */
    fun currentNames(): List<String>
}

/**
 * The Bank's names: it has one row per Tournament player, so the Tournament's player count says
 * how many there are, and [BankPreferences] holds what each was called.
 */
@Singleton
class BankPlayerNamesProvider @Inject constructor(
    private val tournament: TournamentPreferences,
    private val bank: BankPreferences,
) : PlayerNamesProvider {
    override fun currentNames(): List<String> =
        (1..tournament.getPlayerCount()).map { id -> bank.getPlayerName(id).trim().ifEmpty { "Player $id" } }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerNamesModule {
    @Binds
    abstract fun bindPlayerNames(provider: BankPlayerNamesProvider): PlayerNamesProvider
}
