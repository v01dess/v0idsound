package com.example.v0idsound

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private data class StatisticsTrack(
    val track: Track,
    val stats: ListeningStats
)

@Composable
fun StatisticsScreen(
    tracks: List<Track>,
    listeningStats: Map<String, ListeningStats>,
    onTrackClick: (Track) -> Unit
) {
    val totalListeningMs = remember(listeningStats) {
        listeningStats.values.sumOf {
            it.totalListeningTimeMs
        }
    }

    val totalPlays = remember(listeningStats) {
        listeningStats.values.sumOf { it.playCount }
    }

    val tracksListened = remember(listeningStats) {
        listeningStats.values.count {
            it.totalListeningTimeMs > 0L || it.playCount > 0L
        }
    }

    val availableHistory = remember(tracks, listeningStats) {
        tracks.distinctBy { it.uri.toString() }
            .mapNotNull { track ->
                val stats = listeningStats[track.uri.toString()]

                if (
                    stats != null &&
                    (
                            stats.totalListeningTimeMs > 0L ||
                                    stats.playCount > 0L
                            )
                ) {
                    StatisticsTrack(track, stats)
                } else {
                    null
                }
            }
    }

    val mostPlayed = remember(availableHistory) {
        availableHistory
            .filter { it.stats.playCount > 0L }
            .sortedWith(
                compareByDescending<StatisticsTrack> {
                    it.stats.playCount
                }.thenBy(String.CASE_INSENSITIVE_ORDER) {
                    it.track.title
                }
            )
            .take(20)
    }

    val recentlyPlayed = remember(availableHistory) {
        availableHistory
            .filter { it.stats.lastPlayedAtMs > 0L }
            .sortedByDescending { it.stats.lastPlayedAtMs }
            .take(20)
    }

    val unavailableCount = remember(
        listeningStats,
        availableHistory
    ) {
        (tracksListened - availableHistory.size).coerceAtLeast(0)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Your listening",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Saved local statistics across all recorded sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            StatisticsSummaryCard(
                label = "Listening time",
                value = formatListeningDuration(totalListeningMs)
            )
        }

        item {
            StatisticsSummaryCard(
                label = "Qualified plays",
                value = totalPlays.toString()
            )
        }

        item {
            StatisticsSummaryCard(
                label = "Tracks listened to",
                value = tracksListened.toString()
            )
        }

        item {
            Text(
                text = "Listening time is saved in batches, usually after " +
                        "15 seconds of playback or when playback pauses. " +
                        "A qualified play requires a track longer than " +
                        "30 seconds and listening for half its duration " +
                        "or four minutes, whichever comes first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tracksListened == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No listening history yet",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Play a song for at least 15 seconds, " +
                                    "pause it, then check this page.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (unavailableCount > 0) {
            item {
                Text(
                    text = "$unavailableCount recorded track(s) are not " +
                            "in the currently loaded library. Their statistics " +
                            "remain included in the totals above.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            HorizontalDivider()
            Text(
                text = "Most played",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (mostPlayed.isEmpty()) {
            item {
                Text(
                    text = "No qualified plays for available tracks yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(
            items = mostPlayed,
            key = { "most:${it.track.uri}" }
        ) { entry ->
            StatisticsSongCard(
                entry = entry,
                showLastPlayed = false,
                onClick = { onTrackClick(entry.track) }
            )
        }

        item {
            HorizontalDivider()
            Text(
                text = "Recently played",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (recentlyPlayed.isEmpty()) {
            item {
                Text(
                    text = "No recent listening for available tracks yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(
            items = recentlyPlayed,
            key = { "recent:${it.track.uri}" }
        ) { entry ->
            StatisticsSongCard(
                entry = entry,
                showLastPlayed = true,
                onClick = { onTrackClick(entry.track) }
            )
        }
    }
}

@Composable
private fun StatisticsSummaryCard(
    label: String,
    value: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun StatisticsSongCard(
    entry: StatisticsTrack,
    showLastPlayed: Boolean,
    onClick: () -> Unit
) {
    val lastPlayedText = remember(entry.stats.lastPlayedAtMs) {
        if (entry.stats.lastPlayedAtMs > 0L) {
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
            ).format(Date(entry.stats.lastPlayedAtMs))
        } else {
            ""
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${entry.stats.playCount} qualified plays • " +
                        formatListeningDuration(
                            entry.stats.totalListeningTimeMs
                        ),
                style = MaterialTheme.typography.bodySmall
            )

            if (showLastPlayed && lastPlayedText.isNotEmpty()) {
                Text(
                    text = "Last recorded: $lastPlayedText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatListeningDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L

    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}