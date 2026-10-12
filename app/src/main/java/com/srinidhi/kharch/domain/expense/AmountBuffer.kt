package com.srinidhi.kharch.domain.expense

/**
 * The running text of the Add sheet's amount keypad, as an immutable value.
 *
 * The keypad never opens the system IME (plan §7.2C), so this buffer is the single source of
 * truth for what the user has typed. It is a pure function of its own `raw` text: every
 * operation returns a new buffer and leaves the receiver alone, which is what makes the 2 dp
 * cap, the single decimal point and the no-leading-zero rule testable without a Compose tree.
 *
 * `raw` is always one of:
 *
 *  - `""`                      — nothing typed yet,
 *  - `"1234"`                  — whole rupees, no leading zero unless it is exactly `"0"`,
 *  - `"12.5"` / `"12."`        — a decimal point was typed, with 0..2 digits after it.
 *
 * A trailing `.` is kept while typing (it is what the user just pressed) and removed by
 * [backspace], so `"12.5"` backspaces to `"12"` rather than to the half-finished `"12."`.
 */
@JvmInline
value class AmountBuffer(val raw: String) {

    /** `true` once at least one digit has been typed. */
    val hasDigits: Boolean get() = raw.isNotEmpty()

    /** `true` when the buffer renders as a magnitude of zero — nothing usable has been typed. */
    val isZero: Boolean get() = paise() == 0L

    /** `true` when the decimal key has already been used. */
    val hasDecimalPoint: Boolean get() = raw.indexOf(DECIMAL_POINT) >= 0

    /** The digits before the decimal point; empty before the first digit is typed. */
    val integerDigits: String get() = raw.substringBefore(DECIMAL_POINT)

    /** The digits after the decimal point; `""` when there is no point yet. */
    val fractionDigits: String
        get() = if (hasDecimalPoint) raw.substringAfter(DECIMAL_POINT) else ""

    /**
     * Appends [value] to the buffer, or returns `this` when the key cannot be accepted.
     *
     * Rejections are silent rather than an error: a keypad key that refuses input the way a
     * physical keyboard refuses a sixth digit in a five-digit field is not a failure, it is the
     * cap working. The rules, in order:
     *
     *  - the fraction never grows past [MAX_FRACTION_DIGITS] (the 2 dp cap, D5);
     *  - the integer part never grows past [MAX_INTEGER_DIGITS], which also keeps [paise] far
     *    inside `Long` range;
     *  - `"0"` followed by a digit becomes that digit, so `05` is unreachable;
     *  - `0` typed on an empty buffer is a no-op, because `₹0` is what is already displayed.
     */
    fun digit(value: Int): AmountBuffer {
        require(value in 0..9) { "a keypad digit must be 0..9, was $value" }
        if (hasDecimalPoint) {
            val cannotGrowFraction = fractionDigits.length >= MAX_FRACTION_DIGITS
            return if (cannotGrowFraction) this else AmountBuffer(raw + value)
        }
        val head = integerDigits
        val startsAtZero = head.isEmpty() || head == SINGLE_ZERO
        if (startsAtZero) {
            // Leading zero is replaced by the digit, so the buffer never shows "05".
            return if (value == 0) this else AmountBuffer(value.toString())
        }
        val cannotGrowInteger = head.length >= MAX_INTEGER_DIGITS
        return if (cannotGrowInteger) this else AmountBuffer(raw + value)
    }

    /**
     * Types the decimal point, at most once, and only after at least one digit.
     *
     * Pressing `.` on an empty buffer yields `"0."`, which is what every INR calculator does;
     * there is no other sensible reading of "I want 50 paise".
     */
    fun decimalPoint(): AmountBuffer = when {
        hasDecimalPoint -> this
        raw.isEmpty() -> AmountBuffer("0.")
        else -> AmountBuffer(raw + DECIMAL_POINT)
    }

    /**
     * Removes the last typed character.
     *
     * A trailing decimal point goes with the digit before it, so backspacing `"12.5"` lands on
     * `"12"` and never leaves the buffer in the `"12."` half-state.
     */
    fun backspace(): AmountBuffer {
        if (raw.isEmpty()) return this
        val shortened = raw.dropLast(1)
        val danglingPoint = shortened.endsWith(DECIMAL_POINT.toString())
        return if (danglingPoint) AmountBuffer(shortened.dropLast(1)) else AmountBuffer(shortened)
    }

    /** Empties the buffer. The long-press target of the backspace key (plan §7.2C). */
    fun clear(): AmountBuffer = EMPTY

    /**
     * The buffer as a non-negative magnitude in paise.
     *
     * Returns `0` for an empty buffer rather than throwing: the keypad is allowed to hold a
     * half-typed amount at any moment, and the Add sheet decides separately whether zero is
     * saveable (it is not — see [ExpenseDraftRules]).
     */
    fun paise(): Long {
        if (raw.isEmpty()) return 0L
        val rupees = (integerDigits.ifEmpty { SINGLE_ZERO }).toLongOrNull() ?: return 0L
        val paise = rupees * PAISE_PER_RUPEE
        val fraction = parseFraction(fractionDigits)
        return paise + fraction
    }

    /** The digits as typed, for the hero display. `""` renders as a bare rupee symbol. */
    fun displayText(): String = raw

    private fun parseFraction(digits: String): Long {
        if (digits.isEmpty()) return 0L
        val padded = (digits + "00").take(MAX_FRACTION_DIGITS)
        return padded.toLongOrNull() ?: 0L
    }

    companion object {
        /** Nothing typed yet. Renders as `₹0`. */
        val EMPTY: AmountBuffer = AmountBuffer("")

        /** Integer digits. Twelve keeps [paise] roughly 10^5 inside `Long` range. */
        const val MAX_INTEGER_DIGITS: Int = 12

        /** Rupees have exactly two decimal places (D5); a third is not a number here. */
        const val MAX_FRACTION_DIGITS: Int = 2

        private const val DECIMAL_POINT: Char = '.'

        private const val SINGLE_ZERO: String = "0"

        private const val PAISE_PER_RUPEE: Long = 100L

        /**
         * The buffer for an amount already stored as paise — the edit-mode prefill.
         *
         * A whole-rupee amount keeps its short form (`₹640`), and a fractional one is padded to
         * exactly two places (`₹640.05`), so the prefill reads exactly the way it was typed.
         */
        fun ofPaise(paise: Long): AmountBuffer {
            require(paise >= 0) { "an amount magnitude cannot be negative, was $paise" }
            val rupees = paise / PAISE_PER_RUPEE
            val fraction = (paise % PAISE_PER_RUPEE).toInt()
            if (fraction == 0) {
                return AmountBuffer(rupees.toString())
            }
            val padded = fraction.toString().padStart(MAX_FRACTION_DIGITS, '0')
            return AmountBuffer("$rupees.$padded")
        }
    }
}
