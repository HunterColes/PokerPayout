package com.huntercoles.fatline.basicfeature.presentation

sealed class CalculatorIntent {
    data class PlayerCountChanged(val count: Int) : CalculatorIntent()
    data class BuyInChanged(val amount: Double) : CalculatorIntent()
    data class FoodPoolChanged(val amount: Double) : CalculatorIntent()
    data class BountyPoolChanged(val amount: Double) : CalculatorIntent()
}