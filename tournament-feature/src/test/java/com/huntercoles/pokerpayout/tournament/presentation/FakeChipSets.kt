package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.core.utils.ChipSetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A chip set a test sets by hand ([ChipSetProvider]): none until [chips] says otherwise. */
internal class FakeChipSets(initial: ChipSetChips? = null) : ChipSetProvider {
    val chips = MutableStateFlow(initial)

    override fun current(): ChipSetChips? = chips.value

    override val chipSet: Flow<ChipSetChips?> = chips
}
