package com.huntercoles.fatline.settingsfeature.presentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BlindConfiguration(
    val smallestChip: Int = 25,
    val startingChips: Int = 5000,
    val roundLengthMinutes: Int = 20
) : Parcelable