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
    fun `formatCurrency - negative number`() {
        // Note: formatCurrency doesn't add negative sign, use formatNegativeCurrency for that
        assertEquals("$-10.50", FormatUtils.formatCurrency(-10.50))
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
    fun `formatDecimal - trims trailing zeros`() {
        assertEquals("10.5", FormatUtils.formatDecimal(10.50))
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
    fun `formatPercent - trims trailing zeros`() {
        assertEquals("50%", FormatUtils.formatPercent(50.0))
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
    fun `formatMultiplier - trims trailing zeros`() {
        assertEquals("3x", FormatUtils.formatMultiplier(3.0))
    }
    
    @Test
    fun `formatMultiplier - small multiplier`() {
        assertEquals("0.5x", FormatUtils.formatMultiplier(0.5))
    }
    
    @Test
    fun `formatMultiplier - large multiplier`() {
        assertEquals("10.25x", FormatUtils.formatMultiplier(10.25))
    }

    // ========== Edge Cases and Special Scenarios ==========
    
    @Test
    fun `formatCurrency - consistent dollar sign placement`() {
        val result = FormatUtils.formatCurrency(100.0)
        assertEquals(1, result.count { it == '$' }, "Should have exactly one dollar sign")
        assertEquals('$', result.first(), "Dollar sign should be at the start")
    }
    
    @Test
    fun `formatPercent - consistent percent sign placement`() {
        val result = FormatUtils.formatPercent(50.0)
        assertEquals(1, result.count { it == '%' }, "Should have exactly one percent sign")
        assertEquals('%', result.last(), "Percent sign should be at the end")
    }
    
    @Test
    fun `formatMultiplier - consistent x placement`() {
        val result = FormatUtils.formatMultiplier(2.0)
        assertEquals(1, result.count { it == 'x' }, "Should have exactly one x")
        assertEquals('x', result.last(), "x should be at the end")
    }
    
    @Test
    fun `formatDecimal - no symbols added`() {
        val result = FormatUtils.formatDecimal(100.5)
        assertEquals(false, result.contains('$'), "Should not contain dollar sign")
        assertEquals(false, result.contains('%'), "Should not contain percent sign")
        assertEquals(false, result.contains('x'), "Should not contain x")
    }
    
    @Test
    fun `formatCurrency - consistent format for same value`() {
        val value = 123.45
        val result1 = FormatUtils.formatCurrency(value)
        val result2 = FormatUtils.formatCurrency(value)
        assertEquals(result1, result2, "Same value should produce identical formatted output")
    }
    
    @Test
    fun `all formats - handle typical poker amounts correctly`() {
        // Common poker tournament amounts
        assertEquals("$20.00", FormatUtils.formatCurrency(20.0), "Buy-in")
        assertEquals("$5.00", FormatUtils.formatCurrency(5.0), "Food pool")
        assertEquals("$2.00", FormatUtils.formatCurrency(2.0), "Bounty")
        assertEquals("$100.00", FormatUtils.formatCurrency(100.0), "Prize pool")
        assertEquals("$1,000.00", FormatUtils.formatCurrency(1000.0), "Large prize")
    }
    
    @Test
    fun `percentage formats - handle common payout percentages`() {
        assertEquals("50%", FormatUtils.formatPercent(50.0), "First place 50%")
        assertEquals("30%", FormatUtils.formatPercent(30.0), "Second place 30%")
        assertEquals("20%", FormatUtils.formatPercent(20.0), "Third place 20%")
        assertEquals("10%", FormatUtils.formatPercent(10.0), "Fourth place 10%")
    }
}
