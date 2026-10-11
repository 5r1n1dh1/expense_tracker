package com.srinidhi.kharch.core.money

import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.model.Money
import java.math.BigInteger

/**
 * Formats integer paise as Indian-grouped rupee strings.
 *
 * The grouping is done **manually** rather than through `DecimalFormat`/`DecimalFormatSymbols`.
 * That is a deliberate choice: `DecimalFormat` resolves its grouping size and symbols from the
 * platform's locale data, which varies between JDK vendors, Android's ICU build and the JVM's
 * default locale. Grouping the digits ourselves means `₹1,23,456.78` is produced on every
 * machine, in every locale, forever — and it can still be unit-tested.
 *
 * Rules, applied consistently:
 *  - Indian grouping: the last three digits form one group, every earlier group has two.
 *    `123456.78` → `1,23,456.78`, never `123,456.78`.
 *  - A negative amount renders as `-₹640`, i.e. the minus sign goes **before** the currency
 *    symbol. The symbol always leads the digits.
 *  - Decimals appear only when the amount is not a whole rupee, so `₹640` and `₹0` stay short.
 *    [formatWithDecimals] forces `2` decimals regardless.
 *  - No floating point anywhere: paise are split with integer arithmetic on a [BigInteger].
 *
 * Every method is a pure function of its argument, so tests call the formatter directly with no
 * Android context, no locale set-up and no injection ceremony.
 *
 * @property symbol the currency symbol placed before the digits. Defaults to `₹`.
 * @property alwaysShowDecimals when `true`, whole-rupee amounts render as `₹640.00`.
 */
class InrFormatter(
    private val symbol: String = RUPEE_SIGN,
    private val alwaysShowDecimals: Boolean = false,
) {
    /** Formats signed paise, e.g. `-₹1,23,456.78`. */
    fun format(paise: Long): String = signed(paise) + symbol + body(paise, alwaysShowDecimals)

    /** Formats signed money; the sign comes from the `Money`, which came from the `kind` (D5). */
    fun format(money: Money): String = format(money.paise)

    /** Formats an unsigned magnitude; the result is never negative. */
    fun format(magnitude: MagnitudePaise): String = format(magnitude.paise)

    /** Like [format] but always emits exactly two decimals — for ledgers and exports. */
    fun formatWithDecimals(paise: Long): String = signed(paise) + symbol + body(paise, true)

    /** Like [format] but without the currency symbol — for inputs and table columns. */
    fun formatPlain(paise: Long): String = signed(paise) + body(paise, alwaysShowDecimals)

    private fun signed(paise: Long): String = if (paise < 0L) MINUS_SIGN else EMPTY

    private fun body(paise: Long, forceDecimals: Boolean): String {
        val magnitude = BigInteger.valueOf(paise).abs()
        val rupees = groupIndianDigits(magnitude.divide(HUNDRED).toString())
        val paiseRemainder = magnitude.remainder(HUNDRED).toInt()
        if (paiseRemainder == 0 && !forceDecimals) {
            return rupees
        }
        return rupees + DECIMAL_SEPARATOR + paiseRemainder.toString().padStart(DECIMAL_DIGITS, '0')
    }

    companion object {
        /** The Indian rupee sign, U+20B9. */
        const val RUPEE_SIGN: String = "₹"

        private const val DECIMAL_DIGITS: Int = 2
        private const val GROUPING_SEPARATOR: String = ","
        private const val DECIMAL_SEPARATOR: String = "."
        private const val MINUS_SIGN: String = "-"
        private const val EMPTY: String = ""
        private const val TAIL_GROUP_SIZE: Int = 3
        private const val HEAD_GROUP_SIZE: Int = 2
        private val HUNDRED: BigInteger = BigInteger.valueOf(100L)

        /** The formatter the app uses everywhere unless a test or a column needs a variant. */
        val Default: InrFormatter = InrFormatter()

        /**
         * Inserts Indian grouping separators into a run of plain digits.
         *
         * `"1234567"` becomes `"12,34,567"`: the final three digits are one group and every
         * group before them holds two. Inputs shorter than four digits are returned unchanged.
         */
        fun groupIndianDigits(digits: String): String {
            if (digits.length <= TAIL_GROUP_SIZE) {
                return digits
            }
            val tailStart = digits.length - TAIL_GROUP_SIZE
            val headGroups = ArrayList<String>()
            var cursor = tailStart
            while (cursor > 0) {
                val groupStart = maxOf(0, cursor - HEAD_GROUP_SIZE)
                headGroups.add(digits.substring(groupStart, cursor))
                cursor = groupStart
            }
            val head = headGroups.reversed().joinToString(GROUPING_SEPARATOR)
            val tail = digits.substring(tailStart)
            return head + GROUPING_SEPARATOR + tail
        }
    }
}
