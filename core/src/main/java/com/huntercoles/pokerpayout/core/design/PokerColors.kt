package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.graphics.Color

/**
 * Poker-themed color palette for consistent UI across the app
 */
object PokerColors {
    // Poker green shades
    val FeltGreen = Color(0xFF0A3D2E)        // Deep felt green (darkest)
    val DarkGreen = Color(0xFF0D4F3C)        // Dark poker table green
    val MediumGreen = Color(0xFF1B5E20)      // Medium green
    val LightGreen = Color(0xFF2E7D32)       // Light green
    val AccentGreen = Color(0xFF4CAF50)      // Bright green accent
    
    // Gold accent colors
    val PokerGold = Color(0xFFFFD700)        // Primary gold
    val DarkGold = Color(0xFFB8860B)         // Darker gold for hover states
    val LightGold = Color(0xFFFFF8DC)        // Light gold for subtle accents
    
    // Card and text colors
    val CardWhite = Color(0xFFF5F5F5)        // Card white
    val TextSecondary = Color(0xFFE0E0E0)    // Secondary text
    val ErrorRed = Color(0xFFDC143C)         // Error/eliminated color
    val SuccessGreen = Color(0xFF32CD32)     // Success color
    
    // Background variants
    val BackgroundPrimary = FeltGreen        // Main background
    val BackgroundSecondary = DarkGreen      // Cards and sections
    val BackgroundTertiary = MediumGreen     // Elevated elements
    
    // Surface variants for cards
    val SurfacePrimary = DarkGreen
    val SurfaceSecondary = LightGreen
    val SurfaceTertiary = MediumGreen
}