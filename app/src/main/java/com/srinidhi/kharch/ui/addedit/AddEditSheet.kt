package com.srinidhi.kharch.ui.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.expense.ExpenseDraftError
import com.srinidhi.kharch.domain.expense.ExpenseDraftValidation
import com.srinidhi.kharch.ui.expenses.CategoryChip
import com.srinidhi.kharch.ui.glass.GlassShape
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.glass.GlassTier
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import com.srinidhi.kharch.ui.theme.glassTextFieldColors

/*
 * The Add / Edit sheet (plan §7.2C) — the app's only manual entry path (C6).
 *
 * Layout order is deliberate, not decorative:
 *
 *   amount → name → category → date → note → KIND TOGGLE → keypad
 *
 * The kind toggle sits immediately above the keypad and *below* everything it changes, because
 * it is the control that decides whether the amount adds to the month or subtracts from it (D5).
 * Putting it above the amount would invite someone to type ₹2,000, flip to Refund, and get a
 * ₹2,000 credit they did not intend. Putting it next to Save would hide it entirely.
 *
 * There is no system IME anywhere near the amount: [AmountKeypad] is the only way an amount is
 * typed. The name and note fields are Material 3 `TextField`s inside a `GlassSurface`, which is
 * the documented escape hatch for fields that need `isError` and `supportingText` — the stock
 * `GlassTextField` exposes neither, by design.
 */

/** Gap between the sheet's sections. */
private val SECTION_GAP = 16.dp

/** Inset between the sheet edge and its content. */
private val SHEET_PADDING = PaddingValues(horizontal = 20.dp, vertical = 20.dp)

/** Scroll affordance so the sheet never clips at 200 % font scale (§8.4.8). */
private const val SCROLL_TAG = "add-edit-scroll"

/**
 * The sheet body.
 *
 * Stateless on purpose: every value comes from [state] and every action goes out through a
 * callback, so the sheet can be driven directly from a test with no Hilt, no database and no
 * ViewModel in the picture.
 *
 * @param onManageCategories opens the category manager (D4). The Add sheet's "Manage" chip is
 *   the documented entry point; the destination belongs to Phase 8.
 */
@Composable
fun AddEditSheet(
    state: AddEditUiState,
    onDigit: (Int) -> Unit,
    onDecimalPoint: () -> Unit,
    onBackspace: () -> Unit,
    onClearAmount: () -> Unit,
    onKindChange: (ExpenseKind) -> Unit,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onDateSelected: (Long) -> Unit,
    onLinkedExpenseSelected: (String?) -> Unit,
    onSave: () -> Unit,
    onManageCategories: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    var showDatePicker by remember { mutableStateOf(false) }
    val accent = if (state.isRefund) glass.colors.credit else glass.colors.expenseAccent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SHEET_PADDING)
            .testTag(SCROLL_TAG),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        SheetHandle(title = sheetTitle(state), onDismiss = onDismiss)

        AmountHero(buffer = state.amount, accent = accent)

        if (!state.quickAdd) {
            ValidationTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = "Name",
                error = if (state.showValidation) nameError(state.validation) else null,
                testTag = AddEditTestTags.NAME_FIELD,
            )
        }

        CategoryChipRow(
            categories = state.categories,
            selectedCategoryId = state.selectedCategoryId,
            onCategorySelected = onCategorySelected,
            onManageCategories = onManageCategories,
        )

        if (!state.quickAdd) {
            DateChip(
                epochDay = state.dateEpochDay,
                todayEpochDay = state.todayEpochDay,
                isFuture = state.isFutureDate,
                onClick = { showDatePicker = true },
            )
        }

        if (state.isRefund) {
            RefundLinkRow(
                options = state.linkedOptions,
                linkedExpenseId = state.linkedExpenseId,
                onLinkedExpenseSelected = onLinkedExpenseSelected,
            )
        }

        if (!state.quickAdd) {
            ValidationTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = "Note (optional)",
                error = null,
                testTag = AddEditTestTags.NOTE_FIELD,
            )
        }

        ExpenseKindToggle(kind = state.kind, onKindChange = onKindChange)

        AmountKeypad(
            buffer = state.amount,
            onDigit = onDigit,
            onDecimalPoint = onDecimalPoint,
            onBackspace = onBackspace,
            onClearAmount = onClearAmount,
            onDone = onSave,
            doneLabel = state.saveLabel,
            doneEnabled = state.canSave,
            accent = if (state.canSave) glass.colors.accentFill else Color.Unspecified,
        )

        // The disabled state has to be *read*, not guessed at (§7.2C), so the reason is named
        // in a caption at full contrast instead of being implied by a dimmed button.
        val problem = state.blockingProblem
        if (problem != null) {
            Text(
                modifier = Modifier.testTag(AddEditTestTags.SAVE_PROBLEM),
                text = problem,
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
        }
    }

    if (showDatePicker) {
        ModalDatePicker(
            initialEpochDay = state.dateEpochDay,
            todayEpochDay = state.todayEpochDay,
            onDateSelected = onDateSelected,
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * The Hilt-wired entry point.
 *
 * `open` is called from a [androidx.compose.runtime.LaunchedEffect] rather than from
 * `SavedStateHandle`, so the route stays a plain serialisable data class and the sheet can be
 * driven by hand from a test.
 */
@Composable
fun AddEditRoute(
    mode: AddEditMode,
    expenseId: String?,
    dateEpochDay: Long?,
    quickAdd: Boolean = false,
    onDismiss: () -> Unit,
    onManageCategories: () -> Unit = {},
    viewModel: AddEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(mode, expenseId, dateEpochDay, quickAdd) {
        viewModel.open(mode, expenseId, dateEpochDay, quickAdd)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddEditEvent.Saved -> onDismiss()
                AddEditEvent.ValidationFailed -> Unit
                AddEditEvent.ManageCategoriesRequested -> onManageCategories()
            }
        }
    }
    AddEditSheet(
        state = state,
        onDigit = viewModel::onDigit,
        onDecimalPoint = viewModel::onDecimalPoint,
        onBackspace = viewModel::onBackspace,
        onClearAmount = viewModel::onClearAmount,
        onKindChange = viewModel::onKindChange,
        onNameChange = viewModel::onNameChange,
        onNoteChange = viewModel::onNoteChange,
        onCategorySelected = viewModel::onCategorySelected,
        onDateSelected = viewModel::onDateSelected,
        onLinkedExpenseSelected = viewModel::onLinkedExpenseSelected,
        onSave = viewModel::onSave,
        onManageCategories = viewModel::onManageCategories,
        onDismiss = onDismiss,
    )
}

/** The sheet's title, which changes with the mode. */
private fun sheetTitle(state: AddEditUiState): String = when {
    state.quickAdd -> "Quick add"
    state.mode == AddEditMode.EDIT -> "Edit expense"
    state.isRefund -> "Add refund"
    else -> "Add expense"
}

/** The name field's error, or `null` when there is nothing to say. */
private fun nameError(validation: ExpenseDraftValidation): String? =
    when {
        validation.has(ExpenseDraftError.NAME_REQUIRED) -> "A name is required"
        validation.has(ExpenseDraftError.NAME_TOO_LONG) -> "Keep the name under 60 characters"
        else -> null
    }

/** The sheet's grab handle and title. */
@Composable
private fun SheetHandle(title: String, onDismiss: () -> Unit) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = glass.colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            modifier = Modifier
                .testTag(AddEditTestTags.DISMISS_BUTTON)
                .selectable(role = Role.Button) {
                    haptics.perform(KharchHaptic.Selection)
                    onDismiss()
                }
                .padding(12.dp),
            text = "Close",
            style = MaterialTheme.typography.labelLarge,
            color = glass.colors.onSecondary,
        )
    }
}

/**
 * The category chips, horizontally scrolling, single-select, with a trailing "Manage" chip.
 *
 * The chips are in a [LazyRow] rather than a wrapped flow so the row never grows vertically:
 * a ledger with thirty categories must not push the keypad off the bottom of the sheet (§8.4.8).
 */
@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    onManageCategories: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(items = categories, key = { category -> category.id }) { category ->
            com.srinidhi.kharch.ui.expenses.CategoryChip(
                category = category,
                selected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) },
                testTag = categoryChipTag(category.id),
            )
        }
        item(key = MANAGE_CHIP_KEY) {
            ManageChip(onClick = onManageCategories)
        }
    }
}

/** The trailing chip that opens the category manager (D4). */
@Composable
private fun ManageChip(onClick: () -> Unit) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    GlassSurface(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .selectable(role = Role.Button) {
                haptics.perform(KharchHaptic.Selection)
                onClick()
            }
            .testTag(AddEditTestTags.MANAGE_CATEGORIES_CHIP),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Text(
            text = "Manage",
            style = MaterialTheme.typography.labelLarge,
            color = glass.colors.onSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** The date chip. Future dates are allowed but flagged, never blocked (plan §7.2C). */
@Composable
private fun DateChip(
    epochDay: Long,
    todayEpochDay: Long,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    val label = dateChipLabel(epochDay, todayEpochDay)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(role = Role.Button) {
                haptics.perform(KharchHaptic.Selection)
                onClick()
            }
            .testTag(AddEditTestTags.DATE_CHIP),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = glass.colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isFuture) {
                Text(
                    modifier = Modifier.testTag(AddEditTestTags.DATE_FUTURE_FLAG),
                    text = "Future date",
                    style = KharchType.caption,
                    color = glass.colors.expenseAccent,
                )
            }
        }
    }
}

/**
 * The "refund for…" row (D5).
 *
 * Only shown when the kind is Refund, because only a refund has an original to link to. Tapping
 * a row selects it; tapping the selected row clears the link, because a refund with no original
 * is a legitimate thing to record and there must be a way back to that state.
 */
@Composable
private fun RefundLinkRow(
    options: List<Expense>,
    linkedExpenseId: String?,
    onLinkedExpenseSelected: (String?) -> Unit,
) {
    val glass = currentGlassTheme()
    Column(
        modifier = Modifier.fillMaxWidth().testTag(AddEditTestTags.REFUND_LINK_ROW),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Refund for…",
            style = MaterialTheme.typography.titleSmall,
            color = glass.colors.onSecondary,
        )
        if (options.isEmpty()) {
            Text(
                text = "No other expenses in this category yet",
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
            return@Column
        }
        for (option in options) {
            RefundCandidateRow(
                expense = option,
                selected = option.id == linkedExpenseId,
                onClick = { onLinkedExpenseSelected(option.id) },
            )
        }
    }
}

/** One candidate original in the "refund for…" list. */
@Composable
private fun RefundCandidateRow(
    expense: Expense,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton) {
                haptics.perform(KharchHaptic.Selection)
                onClick()
            },
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = expense.name,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = InrFormatter.format(expense.amount),
                style = KharchType.amountRow,
                color = if (selected) glass.colors.credit else glass.colors.onSecondary,
            )
        }
    }
}

/**
 * The Material 3 text field on glass — the documented escape hatch.
 *
 * `GlassTextField` deliberately exposes neither `isError` nor `supportingText`: a component that
 * hides its error slot pushes every caller into inventing one. So a field that must report a
 * validation message drops to `TextField` inside `GlassSurface(tier = Inset, shape =
 * GlassShape.field)` with [glassTextFieldColors], exactly as its KDoc prescribes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ValidationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        tier = GlassTier.Inset,
        shape = GlassShape.field,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            singleLine = true,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.colors.onSecondary,
                )
            },
            isError = error != null,
            supportingText = error?.let { message ->
                {
                    Text(
                        modifier = Modifier.testTag(AddEditTestTags.FIELD_ERROR_PREFIX + testTag),
                        text = message,
                        style = KharchType.caption,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glass.colors.onPrimary),
            colors = glassTextFieldColors(isError = error != null),
            shape = GlassShape.field,
            keyboardOptions = KeyboardOptions.Default,
        )
    }
}

/** The date-picker modal. Its own scrim, so it reads as a sheet above the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalDatePicker(
    initialEpochDay: Long,
    todayEpochDay: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = GlassShape.sheet,
        containerColor = Color.Transparent,
    ) {
        DatePickerSheet(
            initialEpochDay = initialEpochDay,
            todayEpochDay = todayEpochDay,
            onDateSelected = onDateSelected,
            onDismiss = onDismiss,
            modifier = Modifier.padding(SHEET_PADDING),
        )
    }
}

/** Stable key for the trailing chip, so `LazyRow` does not reuse a category's slot. */
private const val MANAGE_CHIP_KEY = "manage"
