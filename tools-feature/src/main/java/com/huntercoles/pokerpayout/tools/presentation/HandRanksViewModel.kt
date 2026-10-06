package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hand ranks (S12) only reads the four-colour deck setting, which Odds owns and changes. */
@HiltViewModel
class HandRanksViewModel @Inject constructor(oddsPreferences: OddsCalculatorPreferences) : ViewModel() {
    val fourColourDeck: Boolean = oddsPreferences.fourColourDeck
}
