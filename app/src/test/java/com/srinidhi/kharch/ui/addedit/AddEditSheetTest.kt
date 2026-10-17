package com.srinidhi.kharch.ui.addedit

import app.cash.turbine.test
import com.srinidhi.kharch.domain.expense.AmountBuffer
import com.srinidhi.kharch.core.model.ExpenseKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State and buffer behaviour behind the Add/Edit sheet (plan §7.2C).
 *
 * The sheet itself is stateless: every value comes from [AddEditUiState] and every action
 * goes out through a callback, so the rules it depends on are pinned here without a Compose
 * tree, a ViewModel or a database.
 */
class AddEditSheetTest {

    @Test
    fun `a fresh state is loading and cannot be saved`() {
        val state = AddEditUiState()

        assertTrue(state.loading)
        assertFalse(state.canSave)
        assertEquals(AddEditMode.ADD, state.mode)
        assertFalse(state.isEditing)
        assertFalse(state.isRefund)
    }

    @Test
    fun `canSave requires validity loading completion and no save in flight`() {
        val base = AddEditUiState(loading = false, saving = false)

        assertFalse(base.canSave)

        val valid = base.copy(
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation.Valid,
        )
        // Still no category, so not valid; force validity by constructing a valid validation.
        val ready = AddEditUiState(
            loading = false,
            saving = false,
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation.Valid,
        )
        assertTrue(ready.canSave)

        assertFalse(ready.copy(saving = true).canSave)
        assertFalse(ready.copy(loading = true).canSave)
    }

    @Test
    fun `isRefund follows the kind`() {
        assertTrue(AddEditUiState(kind = ExpenseKind.REFUND).isRefund)
        assertFalse(AddEditUiState(kind = ExpenseKind.EXPENSE).isRefund)
    }

    @Test
    fun `save label reflects mode and kind`() {
        assertEquals(
            "Save expense",
            AddEditUiState(mode = AddEditMode.ADD, kind = ExpenseKind.EXPENSE).saveLabel,
        )
        assertEquals(
            "Save refund",
            AddEditUiState(mode = AddEditMode.ADD, kind = ExpenseKind.REFUND).saveLabel,
        )
        assertEquals(
            "Save changes",
            AddEditUiState(mode = AddEditMode.EDIT, kind = ExpenseKind.EXPENSE).saveLabel,
        )
    }

    @Test
    fun `isEditing is true only in edit mode`() {
        assertTrue(AddEditUiState(mode = AddEditMode.EDIT).isEditing)
        assertFalse(AddEditUiState(mode = AddEditMode.DUPLICATE).isEditing)
        assertFalse(AddEditUiState(mode = AddEditMode.REFUND).isEditing)
        assertFalse(AddEditUiState(mode = AddEditMode.ADD).isEditing)
    }

    @Test
    fun `offersRefundLink only for a refund with a category`() {
        val refundWithCategory = AddEditUiState(
            kind = ExpenseKind.REFUND,
            selectedCategoryId = "sys_food",
        )
        assertTrue(refundWithCategory.offersRefundLink)

        val refundWithoutCategory = AddEditUiState(kind = ExpenseKind.REFUND)
        assertFalse(refundWithoutCategory.offersRefundLink)

        val expenseWithCategory = AddEditUiState(
            kind = ExpenseKind.EXPENSE,
            selectedCategoryId = "sys_food",
        )
        assertFalse(expenseWithCategory.offersRefundLink)
    }

    @Test
    fun `blocking problem names the first missing field`() {
        val amountProblem = AddEditUiState(
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation(
                setOf(com.srinidhi.kharch.domain.expense.ExpenseDraftError.AMOUNT_REQUIRED),
            ),
        )
        assertEquals("Enter an amount", amountProblem.blockingProblem)

        val categoryProblem = AddEditUiState(
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation(
                setOf(com.srinidhi.kharch.domain.expense.ExpenseDraftError.CATEGORY_REQUIRED),
            ),
        )
        assertEquals("Pick a category", categoryProblem.blockingProblem)

        val nameProblem = AddEditUiState(
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation(
                setOf(com.srinidhi.kharch.domain.expense.ExpenseDraftError.NAME_REQUIRED),
            ),
        )
        assertEquals("Add a name", nameProblem.blockingProblem)

        val none = AddEditUiState(
            validation = com.srinidhi.kharch.domain.expense.ExpenseDraftValidation.Valid,
        )
        assertEquals(null, none.blockingProblem)
    }

    @Test
    fun `amount buffer enforces the two decimal place cap`() {
        var buffer = AmountBuffer.EMPTY
        for (digit in listOf(1, 2, 3, 4)) buffer = buffer.digit(digit)
        buffer = buffer.decimalPoint()
        for (digit in listOf(5, 6, 7)) buffer = buffer.digit(digit)

        assertEquals("1234.56", buffer.raw)
        assertEquals(123456L, buffer.paise())
    }

    @Test
    fun `amount buffer drops a dangling decimal point on backspace`() {
        val buffer = AmountBuffer.EMPTY
            .digit(1)
            .digit(2)
            .decimalPoint()
            .backspace()

        assertEquals("12", buffer.raw)
    }

    @Test
    fun `long press clear empties the buffer`() {
        val buffer = AmountBuffer.EMPTY.digit(9).digit(9).clear()

        assertEquals("", buffer.raw)
        assertTrue(buffer.isZero)
    }

    @Test
    fun `leading zero is replaced so double zero is unreachable`() {
        val buffer = AmountBuffer.EMPTY.digit(0).digit(5)

        assertEquals("5", buffer.raw)
    }

    @Test
    fun `decimal on empty buffer becomes zero point`() {
        val buffer = AmountBuffer.EMPTY.decimalPoint()

        assertEquals("0.", buffer.raw)
    }

    @Test
    fun `ofPaise round trips a fractional amount`() {
        val buffer = AmountBuffer.ofPaise(12_345L)

        assertEquals("123.45", buffer.raw)
        assertEquals(12_345L, buffer.paise())
    }

    @Test
    fun `ofPaise keeps whole rupees short`() {
        val buffer = AmountBuffer.ofPaise(50_000L)

        assertEquals("500", buffer.raw)
    }

    @Test
    fun `state emissions are observable with turbine`() = runTest {
        val flow = MutableStateFlow(AddEditUiState(loading = true))
        flow.test {
            assertEquals(true, awaitItem().loading)
            flow.value = AddEditUiState(loading = false)
            assertEquals(false, awaitItem().loading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
EOF
