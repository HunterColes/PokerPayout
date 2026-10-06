package com.huntercoles.pokerpayout.core.presentation

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Draws the app behind transparent status and navigation bars with light icons: the app is dark
 * only. (The old theme painted the status bar green with dark icons on it, B13.)
 */
fun ComponentActivity.drawBehindDarkSystemBars() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
