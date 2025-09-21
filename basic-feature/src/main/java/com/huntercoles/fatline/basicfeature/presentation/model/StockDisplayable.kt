package com.huntercoles.fatline.basicfeature.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StockDisplayable(
    val symbol: String,
    val name: String,
    val price: String, // Formatted price like "$150.25"
    val change: String, // Formatted change like "+2.50"
    val changePercent: String, // Formatted percent like "+1.67%"
    val marketCapFormatted: String?, // Like "1.5T" or "50B"
    val volumeFormatted: String?, // Like "1.2M"
    val exchange: String,
    val isPositive: Boolean, // For UI coloring (green/red)
) : Parcelable
