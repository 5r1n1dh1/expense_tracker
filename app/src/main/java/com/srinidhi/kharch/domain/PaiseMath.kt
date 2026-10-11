package com.srinidhi.kharch.domain

import kotlin.math.abs

/**
 * Integer paise arithmetic shared by the aggregation use cases (§9).
 *
 * Every amount in Kharch is a `Long` count of paise, end to end. Sums and averages therefore
 * stay in integer space: a `Double` accumulator would shed paise as soon as a ledger grows,
 * and "no precision loss across 10 000 random amounts" (§13.2) is a property of this file, not
 * of a rounding mode someone remembers to apply at the call site.
 */
object PaiseMath {

    /** Exact sum of [values]; addition of `Long` cannot drift, which is the point. */
    fun sum(values: Iterable<Long>): Long {
        var total = 0L
        for (value in values) total += value
        return total
    }

    /** Exact sum of [values]; a convenience overload for the common call shape. */
    fun sum(vararg values: Long): Long = sum(values.asIterable())

    /**
     * [numerator] ÷ [denominator], rounded half away from zero, without ever leaving
     * integer space. Averages such as "average per active day" must not use `/` (which
     * truncates toward zero and would bias every positive average downwards) and must not
     * use `Double` (which is banned for amounts).
     *
     * A [denominator] of zero — an empty ledger with no tracked days — yields `0`.
     */
    fun roundedDivide(numerator: Long, denominator: Int): Long {
        if (denominator == 0) return 0L
        val quotient = numerator / denominator
        val remainder = numerator % denominator
        val roundsAway = abs(remainder) * 2 >= denominator
        return if (roundsAway) quotient + if (numerator < 0) -1 else 1 else quotient
    }

    /**
     * Largest absolute value among [values], floored at `1`.
     *
     * Used for the shared bar-chart scale (§9). Flooring at 1 means a fully zero year still
     * produces a usable denominator instead of a division by zero in the UI layer.
     */
    fun absMax(vararg values: Long): Long {
        var max = 1L
        for (value in values) {
            val magnitude = abs(value)
            if (magnitude > max) max = magnitude
        }
        return max
    }
}
