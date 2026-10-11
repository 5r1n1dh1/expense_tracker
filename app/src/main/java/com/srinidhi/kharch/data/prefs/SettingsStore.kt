package com.srinidhi.kharch.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.enums.enumEntries

/** Theme choice. `SYSTEM` follows the OS light/dark setting (decision D8). */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Glass intensity (plan §8.3).
 *
 * Named distinctly from the UI's `GlassIntensity` because the two must not be confused: this
 * is the persisted *setting*, stored as a name so a future reordering cannot reinterpret it.
 *
 * [EXAGGERATED] is the default and the token baseline (decision D10). There is deliberately no
 * tier above it: past that point legibility degrades faster than the glass effect improves.
 */
enum class GlassIntensityPreference {
    SUBTLE,
    NORMAL,
    EXAGGERATED,
}

/**
 * Everything the settings screen owns, as one immutable snapshot.
 *
 * @property activeAuthUid which Google identity is signed in. Stored purely for UI labelling
 *   ("Synced as …"). It does **not** filter any query — members are authorisation, not
 *   partitioning (D1).
 * @property workspaceId pointer to the active ledger, for the rare multi-ledger case (§6.2).
 */
data class Settings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val glassIntensity: GlassIntensityPreference = GlassIntensityPreference.EXAGGERATED,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val activeAuthUid: String? = null,
    val workspaceId: String? = null,
)

private object Keys {
    val THEME: Preferences.Key<String> = stringPreferencesKey("theme_mode")
    val GLASS_INTENSITY: Preferences.Key<String> = stringPreferencesKey("glass_intensity")
    val HIGH_CONTRAST: Preferences.Key<Boolean> = booleanPreferencesKey("high_contrast")
    val REDUCE_MOTION: Preferences.Key<Boolean> = booleanPreferencesKey("reduce_motion")
    val ACTIVE_AUTH_UID: Preferences.Key<String> = stringPreferencesKey("active_auth_uid")
    val WORKSPACE_ID: Preferences.Key<String> = stringPreferencesKey("workspace_id")
}

private const val DATA_STORE_NAME: String = "kharch_settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

/**
 * Typed DataStore wrapper for appearance settings and the active-identity pointer.
 *
 * DataStore rather than SharedPreferences: reads are a [Flow], writes are transactional, and
 * corruption is surfaced instead of silently resetting. A missing key falls back to the
 * default from [Settings], so a partially written file still yields a usable snapshot.
 *
 * The `DataStore` is a constructor parameter, so tests hand in a throwaway instance and never
 * touch the app's real file.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    /**
     * The current settings, re-emitting on every write.
     *
     * A read [IOException] (a transient disk problem) degrades to defaults rather than taking
     * the whole app down with it. Anything else — a `CorruptionException` in particular — is
     * rethrown: silently showing "no settings" would quietly reset the user's accessibility
     * choices, which is worse than a visible failure.
     */
    val settings: Flow<Settings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { preferences -> preferences.toSettings() }

    /** Which Google identity is currently signed in, for display only. */
    val activeAuthUid: Flow<String?> = dataStore.data.map { preferences -> preferences[Keys.ACTIVE_AUTH_UID] }

    /** The active ledger id, for the "Switch ledger" control (§6.2). */
    val activeWorkspaceId: Flow<String?> = dataStore.data.map { preferences -> preferences[Keys.WORKSPACE_ID] }

    suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { preferences -> preferences[Keys.THEME] = theme.name }
    }

    suspend fun setGlassIntensity(intensity: GlassIntensityPreference) {
        dataStore.edit { preferences -> preferences[Keys.GLASS_INTENSITY] = intensity.name }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.HIGH_CONTRAST] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.REDUCE_MOTION] = enabled }
    }

    /** Records the signed-in identity and the ledger it resolves to. */
    suspend fun setActiveAuth(uid: String, workspaceId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.ACTIVE_AUTH_UID] = uid
            preferences[Keys.WORKSPACE_ID] = workspaceId
        }
    }

    suspend fun setWorkspaceId(workspaceId: String) {
        dataStore.edit { preferences -> preferences[Keys.WORKSPACE_ID] = workspaceId }
    }

    /** Signing out clears the pointer. The ledger and every expense survive (§6.2). */
    suspend fun clearActiveAuth() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.ACTIVE_AUTH_UID)
            preferences.remove(Keys.WORKSPACE_ID)
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        theme = this[Keys.THEME].toPreference(ThemePreference.SYSTEM),
        glassIntensity = this[Keys.GLASS_INTENSITY].toPreference(GlassIntensityPreference.EXAGGERATED),
        highContrast = this[Keys.HIGH_CONTRAST] ?: false,
        reduceMotion = this[Keys.REDUCE_MOTION] ?: false,
        activeAuthUid = this[Keys.ACTIVE_AUTH_UID],
        workspaceId = this[Keys.WORKSPACE_ID],
    )

    /**
     * Reads a stored enum by name, falling back to [fallback] for anything unrecognised.
     *
     * Storing the name rather than the ordinal means the stored value survives an enum being
     * reordered. A value from a newer build degrades to the documented default instead of
     * throwing, because a cosmetic preference must never be able to stop the app opening.
     */
    private inline fun <reified T : Enum<T>> String?.toPreference(fallback: T): T =
        this?.let { stored -> enumEntries<T>().firstOrNull { it.name == stored } } ?: fallback

    companion object {

        /** The process-wide instance backed by the app's private storage. */
        fun create(context: Context): SettingsStore = SettingsStore(context.applicationContext.settingsDataStore)
    }
}
