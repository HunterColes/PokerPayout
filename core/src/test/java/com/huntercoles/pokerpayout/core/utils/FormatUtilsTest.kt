package com.huntercoles.pokerpayout.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsTest {

    // ========== formatCurrency Tests ==========
    
    @Test
    fun `formatCurrency - whole number`() {
        assertEquals("$100.00", FormatUtils.formatCurrency(100.0))
    }
    
    @Test
    fun `formatCurrency - with cents`() {
        assertEquals("$25.50", FormatUtils.formatCurrency(25.50))
    }
    
    @Test
    fun `formatCurrency - zero`() {
        assertEquals("$0.00", FormatUtils.formatCurrency(0.0))
    }
    
    @Test
    fun `formatCurrency - small amount`() {
        assertEquals("$0.25", FormatUtils.formatCurrency(0.25))
    }
    
    @Test
    fun `formatCurrency - large amount with commas`() {
        assertEquals("$1,234.56", FormatUtils.formatCurrency(1234.56))
    }
    
    @Test
    fun `formatCurrency - very large amount`() {
        assertEquals("$1,000,000.00", FormatUtils.formatCurrency(1000000.0))
    }

@Test
    fun `formatCurrency - rounding to two decimals`() {
        assertEquals("$10.67", FormatUtils.formatCurrency(10.666666))
    }

    // ========== formatCurrencyWhole Tests ==========
    
    @Test
    fun `formatCurrencyWhole - whole number`() {
        assertEquals("$100", FormatUtils.formatCurrencyWhole(100.0))
    }
    
    @Test
    fun `formatCurrencyWhole - rounds down`() {
        assertEquals("$25", FormatUtils.formatCurrencyWhole(25.49))
    }
    
    @Test
    fun `formatCurrencyWhole - rounds up`() {
        assertEquals("$26", FormatUtils.formatCurrencyWhole(25.50))
    }
    
    @Test
    fun `formatCurrencyWhole - zero`() {
        assertEquals("$0", FormatUtils.formatCurrencyWhole(0.0))
    }
    
    @Test
    fun `formatCurrencyWhole - large amount with commas`() {
        assertEquals("$1,235", FormatUtils.formatCurrencyWhole(1234.56))
    }

    // ========== formatNegativeCurrency Tests ==========
    
    @Test
    fun `formatNegativeCurrency - positive number`() {
        assertEquals("-$10.00", FormatUtils.formatNegativeCurrency(10.0))
    }
    
    @Test
    fun `formatNegativeCurrency - with cents`() {
        assertEquals("-$25.50", FormatUtils.formatNegativeCurrency(25.50))
    }
    
    @Test
    fun `formatNegativeCurrency - zero`() {
        assertEquals("-$0.00", FormatUtils.formatNegativeCurrency(0.0))
    }
    
    @Test
    fun `formatNegativeCurrency - large amount`() {
        assertEquals("-$1,234.56", FormatUtils.formatNegativeCurrency(1234.56))
    }
    
    @Test
    fun `formatNegativeCurrency - already negative input`() {
        // Should still format correctly even if input is already negative
        assertEquals("-$10.00", FormatUtils.formatNegativeCurrency(-10.0))
    }

    // ========== formatDecimal Tests ==========
    
    @Test
    fun `formatDecimal - whole number`() {
        assertEquals("100", FormatUtils.formatDecimal(100.0))
    }
    
    @Test
    fun `formatDecimal - with one decimal`() {
        assertEquals("10.5", FormatUtils.formatDecimal(10.5))
    }
    
    @Test
    fun `formatDecimal - with two decimals`() {
        assertEquals("10.25", FormatUtils.formatDecimal(10.25))
    }

@Test
    fun `formatDecimal - zero`() {
        assertEquals("0", FormatUtils.formatDecimal(0.0))
    }
    
    @Test
    fun `formatDecimal - very small number`() {
        assertEquals("0.01", FormatUtils.formatDecimal(0.01))
    }
    
    @Test
    fun `formatDecimal - rounds long decimals`() {
        assertEquals("10.67", FormatUtils.formatDecimal(10.666666))
    }

    // ========== formatPercent Tests ==========
    
    @Test
    fun `formatPercent - whole number`() {
        assertEquals("50%", FormatUtils.formatPercent(50.0))
    }
    
    @Test
    fun `formatPercent - with one decimal`() {
        assertEquals("33.33%", FormatUtils.formatPercent(33.333333))
    }
    
    @Test
    fun `formatPercent - with two decimals`() {
        assertEquals("25.25%", FormatUtils.formatPercent(25.25))
    }

@Test
    fun `formatPercent - zero`() {
        assertEquals("0%", FormatUtils.formatPercent(0.0))
    }
    
    @Test
    fun `formatPercent - 100 percent`() {
        assertEquals("100%", FormatUtils.formatPercent(100.0))
    }
    
    @Test
    fun `formatPercent - small percentage`() {
        assertEquals("0.5%", FormatUtils.formatPercent(0.5))
    }

    // ========== formatMultiplier Tests ==========
    
    @Test
    fun `formatMultiplier - whole number`() {
        assertEquals("2x", FormatUtils.formatMultiplier(2.0))
    }
    
    @Test
    fun `formatMultiplier - with decimals`() {
        assertEquals("1.5x", FormatUtils.formatMultiplier(1.5))
    }

@Test
    fun `formatMultiplier - small multiplier`() {
        assertEquals("0.5x", FormatUtils.formatMultiplier(0.5))
    }
    
    @Test
    fun `formatMultiplier - large multiplier`() {
        assertEquals("10.25x", FormatUtils.formatMultiplier(10.25))
    }
}
