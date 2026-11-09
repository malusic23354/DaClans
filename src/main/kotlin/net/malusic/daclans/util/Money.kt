package net.malusic.daclans.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {

    fun parse(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun format(cents: Long): String =
        BigDecimal.valueOf(cents).movePointLeft(2).setScale(2).toPlainString()
}
