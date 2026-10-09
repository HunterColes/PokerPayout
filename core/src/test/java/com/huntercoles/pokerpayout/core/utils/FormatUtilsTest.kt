package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.testing.withCurrency
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsTest {

    // ========== formatCents and formatMoney (the dollar; every currency: AppCurrencyTest) ==========

    @Test
    fun `formatCents - whole number keeps its cents`() {
        assertEquals("$100.00", FormatUtils.formatCents(10_000))
    }

    @Test
    fun `formatCents - with cents`() {
        assertEquals("$25.50", FormatUtils.formatCents(2_550))
    }

    @Test
    fun `formatCents - zero and a small amount`() {
        assertEquals("$0.00", FormatUtils.formatCents(0))
        assertEquals("$0.25", FormatUtils.formatCents(25))
    }

    @Test
    fun `formatCents - large amounts with commas`() {
        assertEquals("$1,234.56", FormatUtils.formatCents(123_456))
        assertEquals("$1,000,000.00", FormatUtils.formatCents(100_000_000))
    }

    @Test
    fun `formatMoney - cents only when there are some`() {
        assertEquals("$450", FormatUtils.formatMoney(45_000))
        assertEquals("$95.50", FormatUtils.formatMoney(9_550))
        assertEquals("-$10", FormatUtils.formatMoney(-1_000))
        assertEquals("$0", FormatUtils.formatMoney(0))
    }

    @Test
    fun `formatMoney - in the currency asked for, or the one picked`() {
        assertEquals("450\u00A0€", FormatUtils.formatMoney(45_000, AppCurrency.EURO))
        assertEquals("¥96", FormatUtils.formatMoney(9_550, AppCurrency.YEN))
        withCurrency(AppCurrency.POUND) {
            assertEquals("£95.50", FormatUtils.formatMoney(9_550))
            assertEquals("£95.50", FormatUtils.formatCents(9_550))
        }
        assertEquals("$95.50", FormatUtils.formatMoney(9_550))
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
