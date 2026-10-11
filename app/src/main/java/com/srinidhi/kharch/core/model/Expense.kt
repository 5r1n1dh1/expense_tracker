package com.srinidhi.kharch.core.model

/**
 * A single expense or refund, as the domain layer sees it.
 *
 * This is the clean domain twin of `ExpenseEntity`; the data layer owns persistence concerns
 * such as `syncState`, which is deliberately absent here.
 *
 * @property amount non-negative magnitude — the direction lives in [kind] (D5).
 * @property dateEpochDay `LocalDate.toEpochDay()`, i.e. days since 1970-01-01 in the user's zone.
 * @property linkedExpenseId for a refund, the expense it reverses. Deleting that original does
 *   **not** cascade-delete the refund — the money still came back.
 * @property createdAt epoch millis, UTC.
 * @property updatedAt epoch millis, UTC. Drives last-write-wins (plan §6.4).
 * @property deletedAt epoch millis of the tombstone, or `null` for a live row.
 */
data class Expense(
    val id: String,
    val name: String,
    val amount: MagnitudePaise,
    val kind: ExpenseKind,
    val currencyCode: String = DEFAULT_CURRENCY,
    val categoryId: String,
    val dateEpochDay: Long,
    val note: String? = null,
    val linkedExpenseId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    /** The signed value used for aggregation: `+amount` for a charge, `-amount` for a refund. */
    val money: Money get() = Money.of(amount, kind)

    /** `true` for a tombstone. Tombstoned rows still sync (plan §6.4) but never render. */
    val isDeleted: Boolean get() = deletedAt != null

    /** `true` when this row is a refund. Rendered with a `↩` glyph and the word "refund". */
    val isRefund: Boolean get() = kind == ExpenseKind.REFUND

    companion object {
        /** v1 is single-currency, but the field exists so adding one needs no data migration. */
        const val DEFAULT_CURRENCY: String = "INR"
    }
}
