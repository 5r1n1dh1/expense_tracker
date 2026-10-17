package com.srinidhi.kharch.ui.navigation

import com.srinidhi.kharch.ui.addedit.AddEditMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Route serializability (plan §7.1).
 */
class RoutesTest {

    private val json = Json

    @Test
    fun `dashboard route round trips`() {
        assertEquals(Routes.Dashboard, json.decodeFromString<Routes.Dashboard>(json.encodeToString(Routes.Dashboard)))
    }

    @Test
    fun `expenses route round trips`() {
        assertEquals(Routes.Expenses, json.decodeFromString<Routes.Expenses>(json.encodeToString(Routes.Expenses)))
    }

    @Test
    fun `settings route round trips`() {
        assertEquals(Routes.Settings, json.decodeFromString<Routes.Settings>(json.encodeToString(Routes.Settings)))
    }

    @Test
    fun `accounts route round trips`() {
        assertEquals(Routes.Accounts, json.decodeFromString<Routes.Accounts>(json.encodeToString(Routes.Accounts)))
    }

    @Test
    fun `categories route round trips`() {
        val encoded = json.encodeToString(Routes.Categories)
        assertEquals(Routes.Categories, json.decodeFromString<Routes.Categories>(encoded))
    }

    @Test
    fun `auth route round trips`() {
        assertEquals(Routes.Auth, json.decodeFromString<Routes.Auth>(json.encodeToString(Routes.Auth)))
    }

    @Test
    fun `add edit route round trips with defaults`() {
        val route = Routes.AddEdit()
        assertEquals(route, json.decodeFromString<Routes.AddEdit>(json.encodeToString(route)))
    }

    @Test
    fun `add edit route round trips with values`() {
        val route = Routes.AddEdit(
            mode = AddEditMode.EDIT,
            expenseId = "abc",
            dateEpochDay = 123L,
            quickAdd = true,
        )
        assertEquals(route, json.decodeFromString<Routes.AddEdit>(json.encodeToString(route)))
    }
}
