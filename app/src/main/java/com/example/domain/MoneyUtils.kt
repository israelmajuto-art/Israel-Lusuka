package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Decimal-safe monetary utilities for TZS financial calculations.
 * Never uses Double or Float for arithmetic.
 */
object MoneyUtils {
    private val HUNDRED = BigDecimal(100)
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

    fun scale(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun parseAmountOrNull(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(",", "")
            .replace("TSh", "", ignoreCase = true)
            .replace("TZS", "", ignoreCase = true)
            .replace("TSH", "", ignoreCase = true)
            .trim()
        if (cleaned.isEmpty()) return null
        return runCatching {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
        }.getOrNull()
    }

    fun toMinorUnits(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .longValueExact()

    fun fromMinorUnits(minorUnits: Long): BigDecimal =
        BigDecimal(minorUnits)
            .divide(HUNDRED, 2, RoundingMode.HALF_UP)

    fun formatCurrency(
        amount: BigDecimal,
        currencyCode: String = "TZS",
        hidden: Boolean = false
    ): String {
        if (hidden) return "$currencyCode ••••••"
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val hasFraction = scaled.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0
        val pattern = if (hasFraction) "#,##0.00" else "#,##0"
        val formatter = DecimalFormat(pattern, symbols)
        return "$currencyCode ${formatter.format(scaled)}"
    }

    fun formatCompactCurrency(
        amount: BigDecimal,
        currencyCode: String = "TZS",
        hidden: Boolean = false
    ): String {
        if (hidden) return "$currencyCode ••••"
        val abs = amount.abs()
        val sign = if (amount < BigDecimal.ZERO) "-" else ""
        val millions = BigDecimal(1_000_000)
        val thousands = BigDecimal(1_000)
        return when {
            abs >= millions -> {
                val v = abs.divide(millions, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                "$sign$currencyCode ${v}M"
            }
            abs >= thousands -> {
                val v = abs.divide(thousands, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                "$sign$currencyCode ${v}K"
            }
            else -> formatCurrency(amount, currencyCode, hidden = false)
        }
    }
}
