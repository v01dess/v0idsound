package com.example.v0idsound

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "v0idsound_settings"
)

enum class AccentColor(
    val color: Color
) {
    VOID_PURPLE(Color(0xFF7C4DFF)),
    CYBER_BLUE(Color(0xFF00B0FF)),
    TOXIC_GREEN(Color(0xFF00E676)),
    HOT_PINK(Color(0xFFFF4081)),
    SUNSET_ORANGE(Color(0xFFFF6D00)),
    BLOOD_RED(Color(0xFFFF1744))
}

data class IndexerSettings(
    val scanFolders: Set<String> = emptySet(),
    val maxFolderDepth: Int = 5,
    val useMediaStore: Boolean = true,
    val minimumDurationSeconds: Int = 0,
    val onlyMusic: Boolean = false,
    val hideRecordings: Boolean = false,
    val sortByName: Boolean = true,
    val rescanOnStartup: Boolean = true,
    val broadScan: Boolean = true,
    val useDarkTheme: Boolean = true,

    /*
     * Kept for backward compatibility with the preset circles.
     * The actual active theme color is accentArgb.
     */
    val accentColor: AccentColor = AccentColor.VOID_PURPLE,

    /*
     * Stored as an Android ARGB Int.
     * Example: #FF7C4DFF is -8638977 when represented as Int.
     */
    val accentArgb: Int = AccentColor.VOID_PURPLE.color.toArgb(),

    val marqueeLongTitles: Boolean = true
) {
    val accent: Color
        get() = Color(accentArgb)
}

class SettingsRepository(
    private val context: Context
) {

    private object Keys {
        val MIN_DURATION =
            intPreferencesKey("minimum_duration_seconds")

        val ONLY_MUSIC =
            booleanPreferencesKey("only_music")

        val HIDE_RECORDINGS =
            booleanPreferencesKey("hide_recordings")

        val SORT_BY_NAME =
            booleanPreferencesKey("sort_by_name")

        val RESCAN_ON_STARTUP =
            booleanPreferencesKey("rescan_on_startup")

        val BROAD_SCAN =
            booleanPreferencesKey("broad_scan")

        val SCAN_FOLDERS =
            stringSetPreferencesKey("scan_folders")

        val MAX_FOLDER_DEPTH =
            intPreferencesKey("max_folder_depth")

        val USE_MEDIA_STORE =
            booleanPreferencesKey("use_media_store")

        val USE_DARK_THEME =
            booleanPreferencesKey("use_dark_theme")

        /*
         * Existing key. Kept so older installations retain their selected
         * preset while we move to the more flexible ARGB color preference.
         */
        val ACCENT_COLOR =
            stringPreferencesKey("accent_color")

        val ACCENT_ARGB =
            intPreferencesKey("accent_argb")

        val MARQUEE_LONG_TITLES =
            booleanPreferencesKey("marquee_long_titles")
    }

    val settings: Flow<IndexerSettings> =
        context.dataStore.data.map { prefs ->
            val presetAccent = runCatching {
                AccentColor.valueOf(
                    prefs[Keys.ACCENT_COLOR]
                        ?: AccentColor.VOID_PURPLE.name
                )
            }.getOrDefault(
                AccentColor.VOID_PURPLE
            )

            val activeArgb = prefs[Keys.ACCENT_ARGB]
                ?: presetAccent.color.toArgb()

            IndexerSettings(
                scanFolders = prefs[Keys.SCAN_FOLDERS] ?: emptySet(),
                maxFolderDepth = prefs[Keys.MAX_FOLDER_DEPTH] ?: 5,
                useMediaStore = prefs[Keys.USE_MEDIA_STORE] ?: true,
                minimumDurationSeconds = prefs[Keys.MIN_DURATION] ?: 0,
                onlyMusic = prefs[Keys.ONLY_MUSIC] ?: false,
                hideRecordings = prefs[Keys.HIDE_RECORDINGS] ?: false,
                sortByName = prefs[Keys.SORT_BY_NAME] ?: true,
                rescanOnStartup = prefs[Keys.RESCAN_ON_STARTUP] ?: true,
                broadScan = prefs[Keys.BROAD_SCAN] ?: true,
                useDarkTheme = prefs[Keys.USE_DARK_THEME] ?: true,
                accentColor = presetAccent,
                accentArgb = activeArgb,
                marqueeLongTitles =
                    prefs[Keys.MARQUEE_LONG_TITLES] ?: true
            )
        }

    suspend fun setMinimumDuration(
        seconds: Int
    ) = context.dataStore.edit {
        it[Keys.MIN_DURATION] = seconds
    }

    suspend fun setOnlyMusic(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.ONLY_MUSIC] = value
    }

    suspend fun setHideRecordings(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.HIDE_RECORDINGS] = value
    }

    suspend fun setSortByName(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.SORT_BY_NAME] = value
    }

    suspend fun setRescanOnStartup(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.RESCAN_ON_STARTUP] = value
    }

    suspend fun setBroadScan(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.BROAD_SCAN] = value
    }

    suspend fun addScanFolder(
        uri: String
    ) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.SCAN_FOLDERS] ?: emptySet()

        prefs[Keys.SCAN_FOLDERS] = current + uri
    }

    suspend fun removeScanFolder(
        uri: String
    ) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.SCAN_FOLDERS] ?: emptySet()

        prefs[Keys.SCAN_FOLDERS] = current - uri
    }

    suspend fun setMaxFolderDepth(
        depth: Int
    ) = context.dataStore.edit {
        it[Keys.MAX_FOLDER_DEPTH] = depth
    }

    suspend fun setUseMediaStore(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.USE_MEDIA_STORE] = value
    }

    suspend fun setDarkTheme(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.USE_DARK_THEME] = value
    }

    /*
     * Used by the existing preset-color circles.
     * It saves both the old preset name and the actual ARGB theme color.
     */
    suspend fun setAccentColor(
        value: AccentColor
    ) = context.dataStore.edit {
        it[Keys.ACCENT_COLOR] = value.name
        it[Keys.ACCENT_ARGB] = value.color.toArgb()
    }

    /*
     * Used by the custom hex color field and later visual picker.
     */
    suspend fun setAccentArgb(
        value: Int
    ) = context.dataStore.edit {
        it[Keys.ACCENT_ARGB] = value
    }

    suspend fun setMarqueeLongTitles(
        value: Boolean
    ) = context.dataStore.edit {
        it[Keys.MARQUEE_LONG_TITLES] = value
    }
}