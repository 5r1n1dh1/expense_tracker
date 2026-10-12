package com.srinidhi.kharch.domain.expense

import com.srinidhi.kharch.core.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * The category read model the expense surfaces need.
 *
 * Two lists, because they answer two different questions. [observeVisible] is what the Add
 * sheet and the filter chips offer: a hidden category is not offered, even though its history
 * keeps rendering (D4). [observeAll] is what a historical row needs, so an expense filed
 * under a category the user later hid still shows its real name and icon.
 */
interface CategoryCatalog {

    /** Every category, hidden ones included, in display order. */
    fun observeAll(): Flow<List<Category>>

    /** Only the categories offered in the Add sheet and the filter chips, in display order. */
    fun observeVisible(): Flow<List<Category>>
}
