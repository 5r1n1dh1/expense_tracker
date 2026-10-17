package com.srinidhi.kharch.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.ui.components.CategoryIconChip
import com.srinidhi.kharch.ui.components.GlassButton
import com.srinidhi.kharch.ui.components.GlassCard
import com.srinidhi.kharch.ui.components.GlassTextField
import com.srinidhi.kharch.ui.glass.FrostedBackdrop
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/** A category row as rendered by the manager, with its live usage count. */
data class CategoryManagerItem(
    val category: Category,
    val usageCount: Int = 0,
)

/** Everything the category manager renders. */
data class CategoryManagerUiState(
    val items: List<CategoryManagerItem> = emptyList(),
    val editingId: String? = null,
    val editingName: String = "",
    val nameError: String? = null,
    val showAddSheet: Boolean = false,
    val addName: String = "",
    val addIconKey: String = "more_horiz",
    val addColorToken: String = "category.other",
    val addNameError: String? = null,
    val isBusy: Boolean = false,
)

/** Icon keys offered in the "Add category" sheet. */
val CATEGORY_MANAGER_ICON_KEYS: List<String> = listOf(
    "restaurant", "directions_bus", "shopping_bag", "shopping", "receipt_long",
    "home", "favorite", "school", "movie", "flight", "local_grocery_store",
    "payments", "more_horiz",
)

/** Colour tokens offered in the "Add category" sheet. */
val CATEGORY_MANAGER_COLOR_TOKENS: List<String> = listOf(
    "category.food", "category.transport", "category.shopping", "category.bills",
    "category.rent", "category.health", "category.education",
    "category.entertainment", "category.travel", "category.groceries",
    "category.gifts", "category.cash", "category.other",
)

/**
 * Category management screen (plan §7.2E, decision D4).
 *
 * Stateless: every value comes from [state] and every action leaves through a callback, so
 * the screen can be driven directly from a test with no Hilt, database or ViewModel.
 */
@Suppress("LongParameterList")
@Composable
fun CategoryManagerScreen(
    state: CategoryManagerUiState = CategoryManagerUiState(),
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    onStartRename: (String) -> Unit = {},
    onRenameChange: (String) -> Unit = {},
    onRenameCommit: () -> Unit = {},
    onRenameCancel: () -> Unit = {},
    onHideToggle: (String, Boolean) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onAddNameChange: (String) -> Unit = {},
    onAddIconSelected: (String) -> Unit = {},
    onAddColorSelected: (String) -> Unit = {},
    onAddConfirm: () -> Unit = {},
    onAddDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()

    FrostedBackdrop(
        modifier = modifier.fillMaxSize(),
        overlay = glass.colors.backdropOverlay,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.colors.onPrimary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { item -> item.category.id }) { item ->
                    CategoryRow(
                        item = item,
                        isEditing = state.editingId == item.category.id,
                        editingName = state.editingName,
                        nameError = if (state.editingId == item.category.id) {
                            state.nameError
                        } else {
                            null
                        },
                        onMoveUp = { onMoveUp(item.category.id) },
                        onMoveDown = { onMoveDown(item.category.id) },
                        onStartRename = { onStartRename(item.category.id) },
                        onRenameChange = onRenameChange,
                        onRenameCommit = onRenameCommit,
                        onRenameCancel = onRenameCancel,
                        onHideToggle = { hidden -> onHideToggle(item.category.id, hidden) },
                        onDelete = { onDelete(item.category.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GlassButton(
                text = "Add category",
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.showAddSheet) {
            AddCategorySheet(
                state = state,
                onNameChange = onAddNameChange,
                onIconSelected = onAddIconSelected,
                onColorSelected = onAddColorSelected,
                onConfirm = onAddConfirm,
                onDismiss = onAddDismiss,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    item: CategoryManagerItem,
    isEditing: Boolean,
    editingName: String,
    nameError: String?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStartRename: () -> Unit,
    onRenameChange: (String) -> Unit,
    onRenameCommit: () -> Unit,
    onRenameCancel: () -> Unit,
    onHideToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val glass = currentGlassTheme()
    val category = item.category

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "\u2261",
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.colors.onSecondary,
                )
                CategoryIconChip(
                    iconKey = category.iconKey,
                    colorToken = category.colorToken,
                )
                if (isEditing) {
                    GlassTextField(
                        value = editingName,
                        onValueChange = onRenameChange,
                        label = "Name",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.colors.onPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = usageLabel(item.usageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.colors.onSecondary,
                        )
                    }
                }
                Switch(
                    checked = category.isHidden,
                    onCheckedChange = onHideToggle,
                )
            }

            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Save",
                        onClick = onRenameCommit,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Cancel",
                        onClick = onRenameCancel,
                        variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (nameError != null) {
                    Text(
                        text = nameError,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.colors.onSecondary,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Rename",
                        onClick = onStartRename,
                        variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Up",
                        onClick = onMoveUp,
                        variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Down",
                        onClick = onMoveDown,
                        variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.usageCount == 0) {
                        GlassButton(
                            text = "Delete",
                            onClick = onDelete,
                            variant = com.srinidhi.kharch.ui.components.GlassButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(
    state: CategoryManagerUiState,
    onNameChange: (String) -> Unit,
    onIconSelected: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = currentGlassTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add category",
                style = MaterialTheme.typography.titleLarge,
                color = glass.colors.onPrimary,
            )
            GlassTextField(
                value = state.addName,
                onValueChange = onNameChange,
                label = "Name",
            )
            if (state.addNameError != null) {
                Text(
                    text = state.addNameError,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.colors.onSecondary,
                )
            }

            Text(
                text = "Icon",
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.onPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Keep the grid compact: render keys in a wrapping row via chunked columns.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_MANAGER_ICON_KEYS.chunked(6).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunk.forEach { key ->
                                val selected = state.addIconKey == key
                                GlassCard(
                                    modifier = Modifier.size(44.dp),
                                    tier = if (selected) {
                                        com.srinidhi.kharch.ui.theme.GlassTier.Exaggerated
                                    } else {
                                        com.srinidhi.kharch.ui.theme.GlassTier.Inset
                                    },
                                    onClick = { onIconSelected(key) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CategoryIconChip(
                                            iconKey = key,
                                            colorToken = state.addColorToken,
                                            chipSize = 32,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Colour preview",
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.onPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORY_MANAGER_COLOR_TOKENS.forEach { token ->
                    val selected = state.addColorToken == token
                    GlassCard(
                        modifier = Modifier.size(32.dp),
                        tier = if (selected) {
                            com.srinidhi.kharch.ui.theme.GlassTier.Exaggerated
                        } else {
                            com.srinidhi.kharch.ui.theme.GlassTier.Inset
                        },
                        onClick = { onColorSelected(token) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        ) {
                            // Preview chip.
                            CategoryIconChip(
                                iconKey = state.addIconKey,
                                colorToken = token,
                                chipSize = 24,
                            )
                        }
                    }
                }
            }

            GlassButton(
                text = "Add",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun usageLabel(count: Int): String = if (count == 1) {
    "1 expense"
} else {
    "$count expenses"
}
