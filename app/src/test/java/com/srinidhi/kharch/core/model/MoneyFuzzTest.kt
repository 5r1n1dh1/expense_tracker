package com.srinidhi.kharch.core.model

import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Precision fuzz for the money model (plan §13.2).
 *
 * The generator is seeded, so a failure is always a bug in the code under test rather than in
 * the draw, and CI failures are reproducible down to the exact sample.
 */
class MoneyFuzzTest {
    @Test
    fun `summing ten thousand random magnitudes as Long matches BigDecimal exactly`() {
        val random = Random(FUZZ_SEED)
        val magnitudes = List(SAMPLE_SIZE) { MagnitudePaise(random.nextLong(0L, MAX_SINGLE_PAISE)) }

        val longSum = magnitudes.fold(0L) { acc, magnitude -> acc + magnitude.paise }
        val decimalSum = magnitudes.fold(BigDecimal.ZERO) { acc, magnitude -> acc + magnitude.toRupees() }

        assertEquals(BigDecimal.valueOf(longSum, 2), decimalSum)
    }

    @Test
    fun `netting ten thousand random charges and refunds as Long matches BigDecimal exactly`() {
        val random = Random(FUZZ_SEED + 1)
        var longNet = 0L
        var decimalNet = BigDecimal.ZERO

        repeat(SAMPLE_SIZE) {
            val magnitude = MagnitudePaise(random.nextLong(0L, MAX_SINGLE_PAISE))
            val kind = if (random.nextBoolean()) ExpenseKind.EXPENSE else ExpenseKind.REFUND
            longNet += Money.of(magnitude, kind).paise
            val rupees = magnitude.toRupees()
            decimalNet += if (kind == ExpenseKind.REFUND) rupees.negate() else rupees
        }

        assertEquals(BigDecimal.valueOf(longNet, 2), decimalNet)
    }

    @Test
    fun `a run that nets to negative keeps its sign in Long arithmetic`() {
        val random = Random(FUZZ_SEED + 2)
        var longNet = 0L
        var decimalNet = BigDecimal.ZERO

        repeat(SAMPLE_SIZE) { index ->
            val magnitude = MagnitudePaise(random.nextLong(0L, MAX_SINGLE_PAISE))
            val kind = if (index % 3 == 0) ExpenseKind.EXPENSE else ExpenseKind.REFUND
            longNet += Money.of(magnitude, kind).paise
            val rupees = magnitude.toRupees()
            decimalNet += if (kind == ExpenseKind.REFUND) rupees.negate() else rupees
        }

        assertEquals(BigDecimal.valueOf(longNet, 2), decimalNet)
        assertEquals(decimalNet.signum(), longNet.signum().toInt())
    }

    private companion object {
        const val SAMPLE_SIZE: Int = 10_000
        const val FUZZ_SEED: Int = 20260812
        const val MAX_SINGLE_PAISE: Long = 5_000_000L
    }
}
