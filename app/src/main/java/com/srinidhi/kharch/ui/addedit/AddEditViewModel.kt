package com.srinidhi.kharch.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.domain.expense.AmountBuffer
import com.srinidhi.kharch.domain.expense.CategoryCatalog
import com.srinidhi.kharch.domain.expense.ExpenseDraftError
import com.srinidhi.kharch.domain.expense.ExpenseDraftValidation
import com.srinidhi.kharch.domain.expense.ExpenseDraftRules
import com.srinidhi.kharch.domain.expense.ExpenseRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Something happened that the sheet cannot undo by itself. */
sealed interface AddEditEvent {

    /** The row is stored. [isRefund] lets the caller word its confirmation (D5). */
    data class Saved(val expenseId: String, val isRefund: Boolean) : AddEditEvent

    /** A save was attempted on a draft that does not validate; the messages are now shown. */
    data object ValidationFailed : AddEditEvent

    /** The user asked for the category manager (D4). Phase 8 supplies the destination. */
    data object ManageCategoriesRequested : AddEditEvent
}

/**
 * Everything the Add/Edit sheet renders, as one immutable snapshot.
 *
 * @property loading `true` until the row being edited has been read back.
 * @property isFutureDate `true` when the chosen date is after today. Future dates are allowed
 *   (plan §7.2C) but flagged, so a typo in the year cannot quietly post-dated an expense.
 * @property canSave the draft validates and no save is in flight. The sheet renders this as a
 *   clearly legible disabled control rather than a dimmed one (§7.2C).
 * @property blockingProblem the one sentence that says what is missing, or `null`.
 */
data class AddEditUiState(
    val mode: AddEditMode = AddEditMode.ADD,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val amount: AmountBuffer = AmountBuffer.EMPTY,
    val kind: ExpenseKind = ExpenseKind.EXPENSE,
    val name: String = "",
    val note: String = "",
    val dateEpochDay: Long = 0L,
    val todayEpochDay: Long = 0L,
    val isFutureDate: Boolean = false,
    val quickAdd: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val linkedExpenseId: String? = null,
    val linkedOptions: List<Expense> = emptyList(),
    val validation: ExpenseDraftValidation = ExpenseDraftValidation.Valid,
    val showValidation: Boolean = false,
) {
    /** `true` when the sheet is writing back to an existing row rather than creating one. */
    val isEditing: Boolean get() = mode == AddEditMode.EDIT

    /** `true` while the kind toggle is on Refund (D5). */
    val isRefund: Boolean get() = kind == ExpenseKind.REFUND

    /** `true` when a refund link row is offered at all. */
    val offersRefundLink: Boolean get() = isRefund && selectedCategoryId != null

    /** The draft validates and nothing is in flight. */
    val canSave: Boolean get() = validation.isValid && !saving && !loading

    /** The label on the primary action, which changes with the kind (D5). */
    val saveLabel: String
        get() = when {
            isEditing -> SAVE_CHANGES_LABEL
            isRefund -> SAVE_REFUND_LABEL
            else -> SAVE_EXPENSE_LABEL
        }

    /** The single sentence naming what is missing, or `null` when nothing is. */
    val blockingProblem: String?
        get() = when {
            validation.has(ExpenseDraftError.AMOUNT_REQUIRED) -> "Enter an amount"
            validation.has(ExpenseDraftError.CATEGORY_REQUIRED) -> "Pick a category"
            validation.has(ExpenseDraftError.NAME_REQUIRED) -> "Add a name"
            validation.has(ExpenseDraftError.NAME_TOO_LONG) -> "Shorten the name"
            else -> null
        }

    private companion object {
        const val SAVE_EXPENSE_LABEL: String = "Save expense"
        const val SAVE_REFUND_LABEL: String = "Save refund"
        const val SAVE_CHANGES_LABEL: String = "Save changes"
    }
}

/** The mutable half of the sheet, before categories are folded in. */
private data class AddEditDraft(
    val mode: AddEditMode = AddEditMode.ADD,
    val editingId: String? = null,
    val originalCreatedAt: Long? = null,
    val quickAdd: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val amount: AmountBuffer = AmountBuffer.EMPTY,
    val kind: ExpenseKind = ExpenseKind.EXPENSE,
    val name: String = "",
    val note: String = "",
    val dateEpochDay: Long = 0L,
    val categoryId: String? = null,
    val linkedExpenseId: String? = null,
    val linkedOptions: List<Expense> = emptyList(),
    val showValidation: Boolean = false,
) {
    fun validation(): ExpenseDraftValidation =
        ExpenseDraftRules.validate(
            name = name,
            amount = amount,
            categoryId = categoryId,
            // The quick-add sheet (plan §7.2G) has no name field, so a blank name is not a
            // problem there — the category's name stands in for it on save.
            nameRequired = !quickAdd,
        )
}

/**
 * Drives the Add/Edit sheet (plan §7.2C).
 *
 * The form is one immutable [AddEditDraft] folded together with the visible categories, so the
 * rendered state is a pure function of what the user typed plus what exists. Nothing here reads
 * the machine clock directly: every timestamp comes from the injected [Clock], which is what
 * makes the amount prefill and the future-date flag testable without a device.
 */
@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val categories: CategoryCatalog,
    private val clock: Clock,
) : ViewModel() {

    private val draft = MutableStateFlow(AddEditDraft())

    private val eventChannel = Channel<AddEditEvent>(Channel.BUFFERED)

    /** One-shot events. Collected by the sheet, never by the list. */
    val events = eventChannel.receiveAsFlow()

    private val todayEpochDay: Long get() = clock.today().toEpochDay()

    /** The renderable state. Re-emits on every keystroke and every category change. */
    val state: StateFlow<AddEditUiState> =
        combine(draft, categories.observeVisible()) { form, visible ->
            AddEditUiState(
                mode = form.mode,
                loading = form.loading,
                saving = form.saving,
                amount = form.amount,
                kind = form.kind,
                name = form.name,
                note = form.note,
                dateEpochDay = form.dateEpochDay,
                todayEpochDay = todayEpochDay,
                isFutureDate = form.dateEpochDay > todayEpochDay,
                quickAdd = form.quickAdd,
                categories = visible,
                selectedCategoryId = form.categoryId,
                linkedExpenseId = form.linkedExpenseId,
                linkedOptions = form.linkedOptions,
                validation = form.validation(),
                showValidation = form.showValidation,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AddEditUiState(),
        )

    /**
     * Loads the sheet for [mode].
     *
     * [dateEpochDay] only matters for [AddEditMode.ADD], where it defaults to today.
     * [quickAdd] is the widget variant (plan §7.2G): no name, no note, no date chip, and the
     * date is always today.
     */
    fun open(mode: AddEditMode, expenseId: String?, dateEpochDay: Long?, quickAdd: Boolean = false) {
        viewModelScope.launch {
            val today = todayEpochDay
            when (mode) {
                AddEditMode.ADD -> {
                    draft.value = AddEditDraft(
                        mode = mode,
                        loading = false,
                        quickAdd = quickAdd,
                        dateEpochDay = dateEpochDay ?: today,
                    )
                }

                AddEditMode.EDIT, AddEditMode.DUPLICATE, AddEditMode.REFUND -> {
                    val original = expenseId?.let { id -> expenses.findById(id) }
                    if (original == null) {
                        // The row vanished underneath us — a sync pull deleted it, most likely.
                        draft.value = AddEditDraft(mode = mode, loading = false, dateEpochDay = today)
                        return@launch
                    }
                    val refund = mode == AddEditMode.REFUND
                    val loaded = AddEditDraft(
                        mode = mode,
                        editingId = if (mode == AddEditMode.EDIT) original.id else null,
                        originalCreatedAt = original.createdAt,
                        loading = false,
                        amount = AmountBuffer.ofPaise(original.amount.paise),
                        kind = if (refund) ExpenseKind.REFUND else original.kind,
                        name = original.name,
                        note = original.note.orEmpty(),
                        // A duplicate is "log this again", so it lands on today; an edit and a
                        // refund both keep the date the money actually moved.
                        dateEpochDay = if (mode == AddEditMode.DUPLICATE) today else original.dateEpochDay,
                        categoryId = original.categoryId,
                        linkedExpenseId = if (refund) original.id else original.linkedExpenseId,
                    )
                    draft.value = loaded
                    if (refund) {
                        loadLinkedOptions(original.categoryId, excluding = original.id)
                    }
                }
            }
        }
    }

    /** A digit key. See [AmountBuffer.digit] for what gets refused and why. */
    fun onDigit(value: Int) = updateAmount { buffer -> buffer.digit(value) }

    /** The decimal-point key. See [AmountBuffer.decimalPoint]. */
    fun onDecimalPoint() = updateAmount { buffer -> buffer.decimalPoint() }

    /** The backspace key, single tap. */
    fun onBackspace() = updateAmount { buffer -> buffer.backspace() }

    /** The backspace key, long press: clear the whole buffer (plan §7.2C). */
    fun onClearAmount() = updateAmount { buffer -> buffer.clear() }

    /** The Expense / Refund segmented control (D5). */
    fun onKindChange(kind: ExpenseKind) {
        draft.update { form ->
            if (form.kind == kind) {
                form
            } else {
                form.copy(
                    kind = kind,
                    // A link only means anything for a refund; switching back to an expense
                    // drops it rather than storing a pointer nothing will read.
                    linkedExpenseId = if (kind == ExpenseKind.REFUND) form.linkedExpenseId else null,
                )
            }
        }
        if (kind == ExpenseKind.REFUND) {
            loadLinkedOptions(draft.value.categoryId, excluding = draft.value.editingId)
        }
    }

    fun onNameChange(value: String) = draft.update { form -> form.copy(name = value) }

    fun onNoteChange(value: String) = draft.update { form -> form.copy(note = value) }

    /** A category chip. Single-select by construction, so the previous choice is replaced. */
    fun onCategorySelected(categoryId: String) {
        draft.update { form ->
            if (form.categoryId == categoryId) {
                form
            } else {
                form.copy(
                    categoryId = categoryId,
                    // The link points at a row of the *same* category (plan §5.1), so changing
                    // category invalidates it and the candidate list has to be re-read.
                    linkedExpenseId = null,
                    linkedOptions = emptyList(),
                )
            }
        }
        if (draft.value.kind == ExpenseKind.REFUND) {
            loadLinkedOptions(categoryId, excluding = draft.value.editingId)
        }
    }

    /** The date chip. Future dates are accepted here and flagged in the rendered state. */
    fun onDateSelected(epochDay: Long) = draft.update { form -> form.copy(dateEpochDay = epochDay) }

    /** Tapping a row in the "refund for…" list, or the same row again to clear it. */
    fun onLinkedExpenseSelected(expenseId: String?) {
        draft.update { form ->
            val next = if (form.linkedExpenseId == expenseId) null else expenseId
            form.copy(linkedExpenseId = next)
        }
    }

    /** The user asked for the category manager from the trailing "Manage" chip (D4). */
    fun onManageCategories() {
        viewModelScope.launch { eventChannel.send(AddEditEvent.ManageCategoriesRequested) }
    }

    /**
     * The Done key and the primary button.
     *
     * An invalid draft is not silently dropped: the messages are switched on and a
     * [AddEditEvent.ValidationFailed] goes out so the sheet can raise the rejection haptic.
     */
    fun onSave() {
        val form = draft.value
        if (!form.validation().isValid) {
            draft.update { it.copy(showValidation = true) }
            viewModelScope.launch { eventChannel.send(AddEditEvent.ValidationFailed) }
            return
        }
        viewModelScope.launch {
            draft.update { it.copy(saving = true) }
            val now = clock.nowMillis()
            val isNewRow = form.mode != AddEditMode.EDIT
            val storedName = resolveStoredName(form)
            val expense = Expense(
                id = form.editingId ?: UUID.randomUUID().toString(),
                name = storedName,
                amount = MagnitudePaise(form.amount.paise()),
                kind = form.kind,
                currencyCode = Expense.DEFAULT_CURRENCY,
                categoryId = requireNotNull(form.categoryId),
                dateEpochDay = form.dateEpochDay,
                note = form.note.trim().ifEmpty { null },
                // The link is only ever stored on a refund; a charge keeps `null` (D5).
                linkedExpenseId = if (form.kind == ExpenseKind.REFUND) form.linkedExpenseId else null,
                createdAt = if (isNewRow) now else form.originalCreatedAt ?: now,
                updatedAt = now,
            )
            expenses.save(expense)
            draft.update { it.copy(saving = false) }
            eventChannel.send(AddEditEvent.Saved(expense.id, expense.isRefund))
        }
    }

    /**
     * The name that will actually be stored.
     *
     * The quick-add sheet has no name field, so a blank name there is filed under the category's
     * own name rather than as an empty string: an expense called "" is invisible in every list,
     * search and export, and "Groceries" is at least something a person can recognise later.
     */
    private suspend fun resolveStoredName(form: AddEditDraft): String {
        val typed = form.name.trim()
        if (typed.isNotEmpty() || !form.quickAdd) return typed
        val categoryId = form.categoryId ?: return UNNAMED_FALLBACK
        val visible = categories.observeVisible().first()
        return visible.firstOrNull { category -> category.id == categoryId }?.name ?: UNNAMED_FALLBACK
    }

    private fun updateAmount(transform: (AmountBuffer) -> AmountBuffer) {
        draft.update { form -> form.copy(amount = transform(form.amount)) }
    }

    /** Re-reads the candidate originals for the "refund for…" row. */
    private fun loadLinkedOptions(categoryId: String?, excluding: String?) {
        if (categoryId == null) {
            draft.update { form -> form.copy(linkedOptions = emptyList()) }
            return
        }
        viewModelScope.launch {
            val options = expenses.recentInCategory(categoryId, LINKED_OPTION_LIMIT)
                .filter { option -> option.id != excluding }
            draft.update { form -> form.copy(linkedOptions = options) }
        }
    }

    private companion object {
        /** How many candidate originals the "refund for…" row offers. */
        const val LINKED_OPTION_LIMIT: Int = 6

        /** Last resort when a quick-add row has neither a name nor a resolvable category. */
        const val UNNAMED_FALLBACK: String = "Expense"

        /** Keeps the category flow alive across a configuration change, but not forever. */
        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
