package com.srinidhi.kharch.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

/**
 * The integer arithmetic every aggregate is built on (§13.2 "Money & formatting").
 *
 * The fuzz case is the one that matters: ten thousand random amounts must sum identically in
 * `Long` paise and in `BigDecimal`, because the dashboard, the widget and the Firestore export
 * must never disagree by a single paise.
 */
class PaiseMathTest {

    @Test
    fun `rounded division rounds half away from zero`() {
        assertEquals(4L, PaiseMath.roundedDivide(7L, 2))
        assertEquals(-4L, PaiseMath.roundedDivide(-7L, 2))
        assertEquals(3L, PaiseMath.roundedDivide(5L, 2))
        assertEquals(-3L, PaiseMath.roundedDivide(-5L, 2))
        assertEquals(3L, PaiseMath.roundedDivide(10L, 3))
    }

    @Test
    fun `rounded division never biases a positive average downwards`() {
        assertEquals(100L, PaiseMath.roundedDivide(299L, 3))
        assertEquals(0L, PaiseMath.roundedDivide(1L, 3))
        assertEquals(0L, PaiseMath.roundedDivide(-1L, 3))
    }

    @Test
    fun `dividing by zero tracked days yields zero`() {
        assertEquals(0L, PaiseMath.roundedDivide(500L, 0))
        assertEquals(0L, PaiseMath.roundedDivide(-500L, 0))
    }

    @Test
    fun `abs max finds the largest magnitude and never returns zero`() {
        assertEquals(900L, PaiseMath.absMax(100L, -900L, 300L))
        assertEquals(900L, PaiseMath.absMax(-900L))
        assertEquals(1L, PaiseMath.absMax())
        assertEquals(1L, PaiseMath.absMax(0L, 0L))
    }

    @Test
    fun `ten thousand random amounts sum identically in long and big decimal`() {
        val random = Random(seed = 20260615L)
        val amounts = LongArray(10_000) { index ->
            random.nextLong(-99_999_999L, 99_999_999L)
        }

        val longSum = PaiseMath.sum(amounts.asIterable())
        val bigDecimalSum = amounts.fold(BigDecimal.ZERO) { acc, paise -> acc.add(BigDecimal.valueOf(paise)) }

        assertEquals(bigDecimalSum.longValueExact(), longSum)
    }

    @Test
    fun `a ledger at the edge of long precision still sums exactly`() {
        val amounts = listOf(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4, -Long.MAX_VALUE / 8, 7L)

        val decimalTotal = amounts.fold(BigDecimal.ZERO) { acc, paise ->
            acc.add(BigDecimal.valueOf(paise))
        }
        assertEquals(PaiseMath.sum(amounts), decimalTotal.longValueExact())
    }
}
