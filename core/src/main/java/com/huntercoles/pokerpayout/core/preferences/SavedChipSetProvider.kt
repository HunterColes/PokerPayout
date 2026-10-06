package com.huntercoles.pokerpayout.core.preferences

import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.core.utils.ChipSetProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The chip set as other tabs see it (the clock's break screen, PP-091 #9), read from what the chip
 * set saved ([ChipCalculatorPreferences]). Read-only: it never writes a setting.
 */
class SavedChipSetProvider @Inject constructor(
    private val preferences: ChipCalculatorPreferences
) : ChipSetProvider {

    override fun current(): ChipSetChips? = ChipSetChips.of(preferences.current())

    override val chipSet: Flow<ChipSetChips?> = preferences.settings.map { ChipSetChips.of(it) }.distinctUntilChanged()
}

/** The chip set for other features, through core's [ChipSetProvider]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChipSetModule {

    @Binds
    abstract fun bindChipSetProvider(provider: SavedChipSetProvider): ChipSetProvider
}
