package com.srinidhi.kharch.core.model

/**
 * Direction of an expense record (decision D5).
 *
 * Every expense stores a **non-negative magnitude**; this enum carries the direction. Keeping
 * the two apart makes storage and Firestore validation trivial (`amountPaise is int && >= 0`),
 * removes the ambiguity of "a negative number means refund", and lets a refund of a refund
 * round-trip correctly.
 *
 * Net spend is therefore `charges - refunds`, and a month can legitimately be net **negative**
 * (plan §9).
 */
enum class ExpenseKind {
    /** Money leaving the ledger. Adds `+magnitude` to the net. */
    EXPENSE,

    /** Money coming back into the ledger. Adds `-magnitude` to the net. */
    REFUND,
}
