package com.srinidhi.kharch.core.model

import java.math.BigDecimal
import kotlin.math.abs

/**
 * A non-negative amount of money, in **paise** (1 rupee = 100 paise).
 *
 * This is the only shape an amount is ever stored in (D5). The negative case is represented by
 * [ExpenseKind.REFUND] plus this magnitude, never by a negative number.
 *
 * @property paise amount in paise, always `>= 0`.
 */
@JvmInline
value class MagnitudePaise(val paise: Long) {
    init {
        require(paise >= 0) { "MagnitudePaise must not be negative, was $paise" }
    }

    /** Exact conversion to rupees. Never routes through `Double`, so no precision is lost. */
    fun toRupees(): BigDecimal = BigDecimal.valueOf(paise, 2)
}

/**
 * Signed money in paise, used **only** for aggregation and display.
 *
 * `paise > 0` is a charge, `paise < 0` is a refund (a credit), `paise == 0` is neutral.
 * A month can therefore net to a negative number, which is exactly what D5 requires.
 *
 * @property paise signed amount in paise.
 */
@JvmInline
value class Money(val paise: Long) {
    /** `true` when this value is money coming **back** into the ledger (a refund). */
    val isCredit: Boolean get() = paise < 0

    /**
     * The unsigned amount, safe to render as a magnitude.
     *
     * Note: `Long.MIN_VALUE` has no positive `Long` counterpart and is deliberately rejected by
     * [MagnitudePaise]. It is ~92 billion trillion rupees, so it is not a representable amount.
     */
    val magnitude: MagnitudePaise get() = MagnitudePaise(abs(paise))

    companion object {
        /** Applies [kind] as a sign to [m]: `+paise` for a charge, `-paise` for a refund. */
        fun of(m: MagnitudePaise, kind: ExpenseKind): Money =
            Money(if (kind == ExpenseKind.REFUND) -m.paise else m.paise)
    }
}
