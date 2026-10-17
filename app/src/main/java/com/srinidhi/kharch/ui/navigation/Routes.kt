package com.srinidhi.kharch.ui.navigation

import com.srinidhi.kharch.ui.addedit.AddEditMode
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data object Dashboard

    @Serializable
    data object Expenses

    @Serializable
    data class AddEdit(
        val mode: AddEditMode = AddEditMode.ADD,
        val expenseId: String? = null,
        val dateEpochDay: Long? = null,
        val quickAdd: Boolean = false,
    )

    @Serializable
    data object Settings

    @Serializable
    data object Accounts

    @Serializable
    data object Categories

    @Serializable
    data object Auth
}