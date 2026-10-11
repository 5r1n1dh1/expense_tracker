package com.srinidhi.kharch.data.prefs

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Appearance settings and the active-identity pointer.
 *
 * Defaults are asserted individually because they are product decisions, not implementation
 * details: glass intensity defaults to Exaggerated (decision D10) and theme follows the system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsStoreTest {
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsStore: SettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "settings-test.preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        settingsStore = SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a fresh install follows the system theme and the exaggerated glass default`() = runBlocking {
        val settings = settingsStore.settings.first()

        assertEquals(ThemePreference.SYSTEM, settings.theme)
        assertEquals(GlassIntensityPreference.EXAGGERATED, settings.glassIntensity)
    }

    @Test
    fun `accessibility toggles default to off`() = runBlocking {
        val settings = settingsStore.settings.first()

        assertFalse(settings.highContrast)
        assertFalse(settings.reduceMotion)
    }

    @Test
    fun `a fresh install has no active identity`() = runBlocking {
        assertNull(settingsStore.settings.first().activeAuthUid)
        assertNull(settingsStore.activeAuthUid.first())
        assertNull(settingsStore.activeWorkspaceId.first())
    }

    @Test
    fun `the theme choice persists`() = runBlocking {
        settingsStore.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, settingsStore.settings.first().theme)
    }

    @Test
    fun `the glass intensity choice persists`() = runBlocking {
        settingsStore.setGlassIntensity(GlassIntensityPreference.SUBTLE)

        assertEquals(GlassIntensityPreference.SUBTLE, settingsStore.settings.first().glassIntensity)
    }

    @Test
    fun `high contrast persists`() = runBlocking {
        settingsStore.setHighContrast(true)

        assertTrue(settingsStore.settings.first().highContrast)
    }

    @Test
    fun `reduce motion persists`() = runBlocking {
        settingsStore.setReduceMotion(true)

        assertTrue(settingsStore.settings.first().reduceMotion)
    }

    @Test
    fun `signing in records both the identity and the ledger it resolves to`() = runBlocking {
        settingsStore.setActiveAuth(uid = "uid-a", workspaceId = "ws-1")

        val settings = settingsStore.settings.first()
        assertEquals("uid-a", settings.activeAuthUid)
        assertEquals("ws-1", settings.workspaceId)
        assertEquals("uid-a", settingsStore.activeAuthUid.first())
        assertEquals("ws-1", settingsStore.activeWorkspaceId.first())
    }

    @Test
    fun `switching ledgers moves the pointer without losing the identity`() = runBlocking {
        settingsStore.setActiveAuth(uid = "uid-a", workspaceId = "ws-1")

        settingsStore.setWorkspaceId("ws-2")

        val settings = settingsStore.settings.first()
        assertEquals("uid-a", settings.activeAuthUid)
        assertEquals("ws-2", settings.workspaceId)
    }

    @Test
    fun `signing out clears the pointer`() = runBlocking {
        settingsStore.setActiveAuth(uid = "uid-a", workspaceId = "ws-1")

        settingsStore.clearActiveAuth()

        val settings = settingsStore.settings.first()
        assertNull(settings.activeAuthUid)
        assertNull(settings.workspaceId)
    }

    @Test
    fun `writing one setting leaves the others alone`() = runBlocking {
        settingsStore.setTheme(ThemePreference.LIGHT)
        settingsStore.setHighContrast(true)
        settingsStore.setActiveAuth(uid = "uid-a", workspaceId = "ws-1")

        settingsStore.setReduceMotion(true)

        val settings = settingsStore.settings.first()
        assertEquals(ThemePreference.LIGHT, settings.theme)
        assertTrue(settings.highContrast)
        assertTrue(settings.reduceMotion)
        assertEquals("uid-a", settings.activeAuthUid)
        assertEquals("ws-1", settings.workspaceId)
    }

    @Test
    fun `an unrecognised stored value falls back to the documented default`() = runBlocking {
        dataStore.edit { preferences -> preferences[stringPreferencesKey("theme_mode")] = "NEON" }

        assertEquals(ThemePreference.SYSTEM, settingsStore.settings.first().theme)
    }

    @Test
    fun `a store built for a second file keeps its own values`() = runBlocking {
        val other = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(ApplicationProvider.getApplicationContext<Context>().filesDir, "second.preferences_pb")
            },
        )
        settingsStore.setTheme(ThemePreference.LIGHT)
        other.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.LIGHT, settingsStore.settings.first().theme)
        assertEquals(ThemePreference.DARK, other.settings.first().theme)
    }
}
