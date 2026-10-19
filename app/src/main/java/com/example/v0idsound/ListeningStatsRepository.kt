package com.example.v0idsound

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.listeningStatsDataStore by preferencesDataStore(
    name = "v0idsound_listening_stats"
)

data class ListeningStats(
    val playCount: Long = 0L,
    val totalListeningTimeMs: Long = 0L,
    val lastPlayedAtMs: Long = 0L
)

class ListeningStatsRepository(
    context: Context
) {
    private val dataStore =
        context.applicationContext.listeningStatsDataStore

    /**
     * Keys are full track URI strings, not numeric Track IDs.
     */
    val stats: Flow<Map<String, ListeningStats>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e(
                        TAG,
                        "Could not read listening statistics",
                        exception
                    )
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val counts = mutableMapOf<String, Long>()
                val listeningTimes = mutableMapOf<String, Long>()
                val lastPlayedTimes = mutableMapOf<String, Long>()

                preferences.asMap().forEach { (key, rawValue) ->
                    val value = rawValue as? Long
                        ?: return@forEach

                    when {
                        key.name.startsWith(COUNT_PREFIX) -> {
                            val uri = key.name.removePrefix(COUNT_PREFIX)
                            counts[uri] = value.coerceAtLeast(0L)
                        }

                        key.name.startsWith(TIME_PREFIX) -> {
                            val uri = key.name.removePrefix(TIME_PREFIX)
                            listeningTimes[uri] = value.coerceAtLeast(0L)
                        }

                        key.name.startsWith(LAST_PLAYED_PREFIX) -> {
                            val uri = key.name.removePrefix(
                                LAST_PLAYED_PREFIX
                            )
                            lastPlayedTimes[uri] = value.coerceAtLeast(0L)
                        }
                    }
                }

                val trackUris = counts.keys +
                        listeningTimes.keys +
                        lastPlayedTimes.keys

                trackUris.associateWith { uri ->
                    ListeningStats(
                        playCount = counts[uri] ?: 0L,
                        totalListeningTimeMs =
                            listeningTimes[uri] ?: 0L,
                        lastPlayedAtMs =
                            lastPlayedTimes[uri] ?: 0L
                    )
                }
            }
            .distinctUntilChanged()

    fun statsForTrack(
        trackUri: String
    ): Flow<ListeningStats> {
        return stats
            .map { allStats ->
                allStats[trackUri] ?: ListeningStats()
            }
            .distinctUntilChanged()
    }

    /**
     * Adds a batch of actual listening time.
     *
     * The playback service must measure this time, excluding pauses
     * and seek jumps. Do not pass the player's absolute position.
     *
     * Set qualifiedPlay to true only once per qualifying playback.
     */
    suspend fun recordListening(
        trackUri: String,
        listenedMs: Long,
        qualifiedPlay: Boolean = false,
        playedAtMs: Long = System.currentTimeMillis()
    ) {
        require(trackUri.isNotBlank()) {
            "Track URI must not be blank."
        }

        require(listenedMs >= 0L) {
            "Listening time must not be negative."
        }

        require(playedAtMs >= 0L) {
            "Playback timestamp must not be negative."
        }

        if (listenedMs == 0L && !qualifiedPlay) return

        val countKey = longPreferencesKey(
            COUNT_PREFIX + trackUri
        )
        val timeKey = longPreferencesKey(
            TIME_PREFIX + trackUri
        )
        val lastPlayedKey = longPreferencesKey(
            LAST_PLAYED_PREFIX + trackUri
        )

        dataStore.edit { preferences ->
            if (listenedMs > 0L) {
                val previousTime =
                    (preferences[timeKey] ?: 0L).coerceAtLeast(0L)

                preferences[timeKey] = saturatingAdd(
                    previousTime,
                    listenedMs
                )
            }

            if (qualifiedPlay) {
                val previousCount =
                    (preferences[countKey] ?: 0L).coerceAtLeast(0L)

                preferences[countKey] = saturatingAdd(
                    previousCount,
                    1L
                )
            }

            preferences[lastPlayedKey] = maxOf(
                preferences[lastPlayedKey] ?: 0L,
                playedAtMs
            )
        }
    }

    private fun saturatingAdd(
        current: Long,
        increment: Long
    ): Long {
        return if (current > Long.MAX_VALUE - increment) {
            Long.MAX_VALUE
        } else {
            current + increment
        }
    }

    private companion object {
        const val TAG = "ListeningStats"

        const val COUNT_PREFIX = "play_count:"
        const val TIME_PREFIX = "listening_time_ms:"
        const val LAST_PLAYED_PREFIX = "last_played_ms:"
    }
}