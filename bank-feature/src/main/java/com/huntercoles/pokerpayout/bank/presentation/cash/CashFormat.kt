package com.huntercoles.pokerpayout.bank.presentation.cash

import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import kotlin.math.absoluteValue

/** The minus sign, not a hyphen: "−20". */
private const val MINUS = "−"

/**
 * An amount in the ledger table, whose header already says it is money: "40", "112.50", "1,040";
 * with [signed], a result with its sign ("+72", "−20", "0"), so colour is never the only signal.
 */
internal fun ledgerAmount(cents: Long, signed: Boolean = false): String {
    val digits = formatMoney(cents.absoluteValue).removePrefix("$")
    return when {
        signed && cents > 0L -> "+$digits"
        cents < 0L -> MINUS + digits
        else -> digits
    }
}
