package com.huntercoles.pokerpayout.core.testing

import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import org.junit.rules.ExternalResource

/**
 * Runs [block] with every amount shown in [currency] (PP-114), then puts back the currency there was.
 * Tests run in the dollar unless they ask for another, so a test that switches must switch back.
 */
fun <T> withCurrency(currency: AppCurrency, block: () -> T): T {
    val before = MoneyFormat.current
    MoneyFormat.current = currency
    return try {
        block()
    } finally {
        MoneyFormat.current = before
    }
}

/** A JUnit 4 rule: the test runs with every amount shown in [currency], and the dollar comes back after. */
class CurrencyRule(private val currency: AppCurrency) : ExternalResource() {
    private var before = AppCurrency.DEFAULT

    override fun before() {
        before = MoneyFormat.current
        MoneyFormat.current = currency
    }

    override fun after() {
        MoneyFormat.current = before
    }
}
