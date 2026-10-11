package com.example.v0idsound

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun PlayerScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    marqueeLongTitles: Boolean,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrackClick: (Track) -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }

    var isRolling by remember { mutableStateOf(false) }
    var rolledTrack by remember { mutableStateOf<Track?>(null) }

    var isPlayerPanelOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val displayTrack = rolledTrack ?: currentTrack

    val visibleTracks = remember(tracks, searchQuery) {
        val query = searchQuery.trim()

        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                        track.artist.contains(query, ignoreCase = true) ||
                        track.album.contains(query, ignoreCase = true)
            }
        }
    }

    val sliderValue = if (isSeeking) {
        seekFraction
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = if (searchQuery.isBlank()) {
                            "Tracks found: ${tracks.size}"
                        } else {
                            "Showing ${visibleTracks.size} of ${tracks.size} tracks"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true,
                        label = {
                            Text("Search library")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        }
                    )
                }

                items(
                    items = visibleTracks,
                    key = { track -> track.id }
                ) { track ->
                    val isSelected = track.id == currentTrack?.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onTrackClick(track)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (visibleTracks.isEmpty()) {
                    item {
                        Text(
                            text = "No tracks match \"$searchQuery\".",
                            modifier = Modifier.padding(vertical = 24.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (currentTrack != null) {
            FloatingActionButton(
                onClick = {
                    isPlayerPanelOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = "Open currently playing"
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            AnimatedVisibility(
                visible = isPlayerPanelOpen && currentTrack != null,
                enter = slideInHorizontally(
                    initialOffsetX = { it }
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it }
                )
            ) {
                CurrentlyPlayingCard(
                    track = displayTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    repeatMode = repeatMode,
                    sliderValue = sliderValue,
                    isRolling = isRolling,
                    marqueeLongTitles = marqueeLongTitles,
                    onSliderValueChange = { value ->
                        isSeeking = true
                        seekFraction = value
                    },
                    onSeekFinished = {
                        onSeek(seekFraction)
                        isSeeking = false
                    },
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onRepeatClick = onRepeatClick,
                    onRoll = {
                        if (!isRolling && tracks.isNotEmpty()) {
                            isRolling = true

                            scope.launch {
                                val winner = tracks.random()

                                repeat(14) {
                                    rolledTrack = tracks.random(
                                        Random(System.nanoTime())
                                    )

                                    delay(65L)
                                }

                                rolledTrack = winner
                                delay(300L)

                                onTrackClick(winner)

                                isRolling = false
                                rolledTrack = null
                            }
                        }
                    },
                    onClose = {
                        isPlayerPanelOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrentlyPlayingCard(
    track: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    sliderValue: Float,
    isRolling: Boolean,
    marqueeLongTitles: Boolean,
    onSliderValueChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeatClick: () -> Unit,
    onRoll: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(610.dp)
            .padding(
                top = 16.dp,
                start = 28.dp,
                end = 12.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close currently playing",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (track?.artworkUri != null) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = "Album artwork for ${track.title}",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No album artwork",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track?.title ?: "No track selected",
                    modifier = if (marqueeLongTitles) {
                        Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = if (marqueeLongTitles) {
                        TextOverflow.Clip
                    } else {
                        TextOverflow.Ellipsis
                    },
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track?.artist ?: "Tap a track to start",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track?.album.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Slider(
                value = sliderValue,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSeekFinished,
                enabled = durationMs > 0
            )

            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    12.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track"
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) {
                            "Pause"
                        } else {
                            "Play"
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track"
                    )
                }

                IconButton(onClick = onRepeatClick) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Button(
                onClick = onRoll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                enabled = !isRolling && track != null
            ) {
                Text(
                    text = if (isRolling) {
                        "Rolling..."
                    } else {
                        "Roll random track"
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRolling) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )

                        Spacer(
                            modifier = Modifier.size(8.dp)
                        )

                        Text("Choosing a track...")
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}