package com.example.v0idsound

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.Player
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.size.Precision
import coil.request.ImageRequest
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object PlayerArtworkLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizeBytes(16 * 1024 * 1024)
                        .weakReferencesEnabled(true)
                        .build()
                }
                .diskCache(null)
                .crossfade(false)
                .build()
                .also { instance = it }
        }
    }
}

private enum class LibraryView(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists")
}

private enum class LibrarySort(val label: String) {
    TITLE("Title: A–Z"),
    RECENTLY_ADDED("Recently added"),
    MOST_LISTENED("Most listened to")
}

private data class LibraryGroup(
    val key: String,
    val title: String,
    val tracks: List<Track>
)

private fun metadataLabel(
    value: String,
    fallback: String
): String {
    val trimmed = value.trim()

    return if (
        trimmed.isEmpty() ||
        trimmed.equals("<unknown>", ignoreCase = true)
    ) {
        fallback
    } else {
        trimmed
    }
}

private fun groupLabel(
    track: Track,
    view: LibraryView
): String {
    return when (view) {
        LibraryView.ALBUMS ->
            metadataLabel(track.album, "Unknown album")

        LibraryView.ARTISTS ->
            metadataLabel(track.artist, "Unknown artist")

        LibraryView.SONGS -> ""
    }
}

private fun groupKey(
    track: Track,
    view: LibraryView
): String {
    return groupLabel(track, view).lowercase(Locale.ROOT)
}

private fun sortTracks(
    tracks: List<Track>,
    sort: LibrarySort,
    stats: Map<String, ListeningStats>
): List<Track> {
    return when (sort) {
        LibrarySort.TITLE -> tracks.sortedWith(
            compareBy<Track, String>(
                String.CASE_INSENSITIVE_ORDER
            ) { it.title }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
                .thenBy { it.uri.toString() }
        )

        LibrarySort.RECENTLY_ADDED -> tracks.sortedWith(
            compareByDescending<Track> { it.dateAddedMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy { it.uri.toString() }
        )

        LibrarySort.MOST_LISTENED -> tracks.sortedWith(
            compareByDescending<Track> {
                stats[it.uri.toString()]?.playCount ?: 0L
            }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy { it.uri.toString() }
        )
    }
}

@Composable
fun PlayerScreen(
    tracks: List<Track>,
    listeningStats: Map<String, ListeningStats>,
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    marqueeLongTitles: Boolean,
    showAlbumTileArtwork: Boolean,
    albumTileStyle: String,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackSwipeLeft: (Track) -> Unit,
    onTrackSwipeLeftUndo: (Track) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isSeeking by remember(currentTrack?.uri) {
        mutableStateOf(false)
    }
    var seekFraction by remember(currentTrack?.uri) {
        mutableStateOf(0f)
    }

    var isRolling by remember { mutableStateOf(false) }
    var rolledTrack by remember { mutableStateOf<Track?>(null) }

    var isPlayerPanelOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }
    var viewName by rememberSaveable {
        mutableStateOf(LibraryView.SONGS.name)
    }
    var sortName by rememberSaveable {
        mutableStateOf(LibrarySort.TITLE.name)
    }
    var selectedGroupKey by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var isMenuOpen by remember { mutableStateOf(false) }

    val selectedView = LibraryView.entries.firstOrNull {
        it.name == viewName
    } ?: LibraryView.SONGS

    val selectedSort = LibrarySort.entries.firstOrNull {
        it.name == sortName
    } ?: LibrarySort.TITLE

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val displayTrack = rolledTrack ?: currentTrack

    val groups = remember(tracks, selectedView) {
        if (selectedView == LibraryView.SONGS) {
            emptyList<LibraryGroup>()
        } else {
            tracks.groupBy { groupKey(it, selectedView) }
                .map { (key, members) ->
                    LibraryGroup(
                        key = key,
                        title = groupLabel(
                            members.first(),
                            selectedView
                        ),
                        tracks = sortTracks(
                            members,
                            LibrarySort.TITLE,
                            emptyMap()
                        )
                    )
                }
                .sortedWith(
                    compareBy<LibraryGroup, String>(
                        String.CASE_INSENSITIVE_ORDER
                    ) { it.title }
                )
        }
    }

    val activeGroup = groups.firstOrNull {
        it.key == selectedGroupKey
    }

    val isGroupOpen =
        selectedView != LibraryView.SONGS &&
                selectedGroupKey != null

    val showGrid =
        selectedView != LibraryView.SONGS &&
                !isGroupOpen

    val query = searchQuery.trim()

    val visibleGroups = remember(groups, query) {
        if (query.isEmpty()) {
            groups
        } else {
            groups.filter { group ->
                group.title.contains(query, ignoreCase = true) ||
                        group.tracks.any { track ->
                            track.title.contains(query, ignoreCase = true) ||
                                    track.artist.contains(query, ignoreCase = true) ||
                                    track.album.contains(query, ignoreCase = true)
                        }
            }
        }
    }

    val sourceTracks = if (isGroupOpen) {
        activeGroup?.tracks.orEmpty()
    } else {
        tracks
    }

    val visibleTracks = remember(
        sourceTracks,
        query,
        selectedSort,
        listeningStats
    ) {
        val matchingTracks = if (query.isEmpty()) {
            sourceTracks
        } else {
            sourceTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                        track.artist.contains(query, ignoreCase = true) ||
                        track.album.contains(query, ignoreCase = true)
            }
        }

        sortTracks(
            matchingTracks,
            selectedSort,
            listeningStats
        )
    }

    val nextTrack: Track? = remember(visibleTracks, currentTrack) {
        val idx = visibleTracks.indexOfFirst { it.uri == currentTrack?.uri }
        if (idx >= 0 && idx < visibleTracks.size - 1) {
            visibleTracks[idx + 1]
        } else if (idx >= 0) {
            visibleTracks.firstOrNull() // wrap around
        } else {
            null
        }
    }

    val prevTrack: Track? = remember(visibleTracks, currentTrack) {
        val idx = visibleTracks.indexOfFirst { it.uri == currentTrack?.uri }
        if (idx > 0) {
            visibleTracks[idx - 1]
        } else if (idx == 0 && visibleTracks.isNotEmpty()) {
            visibleTracks.last() // wrap around
        } else {
            null
        }
    }

    val sliderValue = when {
        isSeeking -> seekFraction
        durationMs > 0L ->
            (positionMs.toFloat() / durationMs.toFloat())
                .coerceIn(0f, 1f)
        else -> 0f
    }

    BackHandler(
        enabled = isPlayerPanelOpen || isGroupOpen
    ) {
        if (isPlayerPanelOpen) {
            isPlayerPanelOpen = false
        } else {
            selectedGroupKey = null
            searchQuery = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGroupOpen) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                selectedGroupKey = null
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription =
                                    "Back to ${selectedView.label}"
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGroupOpen) {
                                activeGroup?.title ?: "Unavailable group"
                            } else {
                                selectedView.label
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (showGrid) {
                                "${visibleGroups.size} " +
                                        selectedView.label.lowercase(Locale.ROOT) +
                                        " • ${tracks.size} tracks"
                            } else if (query.isEmpty()) {
                                "${sourceTracks.size} tracks"
                            } else {
                                "Showing ${visibleTracks.size} " +
                                        "of ${sourceTracks.size} tracks"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Box {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                isMenuOpen = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("View / Sort")
                        }

                        DropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false }
                        ) {
                            Text(
                                text = "View", // ts the view on the sorting dropdown
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )

                            LibraryView.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option == selectedView) {
                                                "✓ ${option.label}" // ts the fucking check mark on the sorting options
                                            } else {
                                                option.label
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewName = option.name
                                        selectedGroupKey = null
                                        searchQuery = ""
                                        isMenuOpen = false
                                    }
                                )
                            }

                            HorizontalDivider()

                            Text(
                                text = "Sort songs", // the sort songs button lmao
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )

                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option == selectedSort) {
                                                "✓ ${option.label}"
                                            } else {
                                                option.label
                                            }
                                        )
                                    },
                                    onClick = {
                                        sortName = option.name
                                        isMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField( // this shit the search bar
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            if (isGroupOpen) {
                                "Search these songs"
                            } else {
                                "Search library"
                            }
                        )
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
                                onClick = { searchQuery = "" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Text(
                    text = if (showGrid) {
                        "${selectedView.label}: A–Z"
                    } else {
                        "Sort: ${selectedSort.label}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (showGrid) {
                    if (visibleGroups.isEmpty()) {
                        EmptyLibraryMessage(
                            message = if (query.isEmpty()) {
                                "No ${selectedView.label.lowercase(Locale.ROOT)} " +
                                        "found. Check your indexing settings."
                            } else {
                                "No matches for \"$searchQuery\"."
                            }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp
                            ),
                            horizontalArrangement =
                                Arrangement.spacedBy(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(16.dp)
                        ) {
                            gridItems(
                                items = visibleGroups,
                                key = { it.key }
                            ) { group ->
                                LibraryGroupTile(
                                    group = group,
                                    showArtwork =
                                        selectedView == LibraryView.ALBUMS &&
                                                showAlbumTileArtwork,
                                    artworkStyle = albumTileStyle,
                                    onClick = {
                                        focusManager.clearFocus()
                                        selectedGroupKey = group.key
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = visibleTracks,
                            key = { it.uri.toString() }
                        ) { track ->
                            LibraryTrackRow(
                                track = track,
                                selected = track.uri == currentTrack?.uri,
                                playCount = if (
                                    selectedSort == LibrarySort.MOST_LISTENED
                                ) {
                                    listeningStats[track.uri.toString()]?.playCount ?: 0L
                                } else {
                                    null
                                },
                                onClick = {
                                    focusManager.clearFocus()
                                    onTrackClick(track)
                                },
                                onSwipeLeft = {
                                    onTrackSwipeLeft(track)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Hidden: ${track.title}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onTrackSwipeLeftUndo(track)
                                        }
                                    }
                                }
                            )
                        }

                        if (visibleTracks.isEmpty()) {
                            item {
                                EmptyLibraryMessage(
                                    message = if (query.isEmpty()) {
                                        "No tracks found."
                                    } else {
                                        "No tracks match \"$searchQuery\"."
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (currentTrack != null) {
            FloatingActionButton(
                onClick = {
                    focusManager.clearFocus()
                    isPlayerPanelOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Open currently playing"
                )
            }
        }

        AnimatedVisibility(
            visible = isPlayerPanelOpen && currentTrack != null,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
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
                onSliderValueChange = {
                    isSeeking = true
                    seekFraction = it
                },
                onSeekFinished = {
                    onSeek(seekFraction)
                    isSeeking = false
                },
                onPlayPause = onPlayPause,
                onPrevious = { prevTrack?.let { onTrackClick(it) } },
                onNext = { nextTrack?.let { onTrackClick(it) } },
                onRepeatClick = onRepeatClick,
                onRoll = {
                    if (!isRolling && tracks.isNotEmpty()) {
                        val candidates = tracks.toList()
                        isRolling = true

                        scope.launch {
                            try {
                                val winner = candidates.random()

                                repeat(14) {
                                    rolledTrack = candidates.random()
                                    delay(65L)
                                }

                                rolledTrack = winner
                                delay(300L)
                                onTrackClick(winner)
                            } finally {
                                isRolling = false
                                rolledTrack = null
                            }
                        }
                    }
                },
                onClose = { isPlayerPanelOpen = false }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun LibraryGroupTile(
    group: LibraryGroup,
    showArtwork: Boolean,
    artworkStyle: String,
    onClick: () -> Unit
) {
    val artworkUris = remember(group.tracks) {
        group.tracks
            .mapNotNull { it.artworkUri }
            .distinctBy { it.toString() }
            .take(4)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (showArtwork) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (artworkStyle == "FOUR") {
                        repeat(2) { rowIndex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement =
                                    Arrangement.spacedBy(2.dp)
                            ) {
                                repeat(2) { columnIndex ->
                                    val index =
                                        rowIndex * 2 + columnIndex

                                    AlbumArtworkCell(
                                        artworkUri =
                                            artworkUris.getOrNull(index),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                    )
                                }
                            }
                        }
                    } else {
                        AlbumArtworkCell(
                            artworkUri = artworkUris.firstOrNull(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    group.tracks.take(4).forEachIndexed { index, track ->
                        Text(
                            text = "${index + 1}. ${track.title}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = if (group.tracks.size == 1) {
                    "1 track"
                } else {
                    "${group.tracks.size} tracks"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumArtworkCell(
    artworkUri: android.net.Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context.applicationContext) {
        PlayerArtworkLoader.get(context.applicationContext)
    }

    var failed by remember(artworkUri) {
        mutableStateOf(false)
    }

    val request = remember(context, artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .size(128, 128)
            .precision(Precision.EXACT)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.primaryContainer
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )

        if (artworkUri != null && !failed) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    failed = true
                }
            )
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    selected: Boolean,
    playCount: Long?,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .pointerInput(track.uri) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, amount ->
                        totalDrag += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (totalDrag < -100f) onSwipeLeft()
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
            .clickable(onClick = onClick)
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

        if (playCount != null) {
            Text(
                text = if (playCount == 1L) {
                    "1 qualified play"
                } else {
                    "$playCount qualified plays"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLibraryMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium
    )
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
    val context = LocalContext.current
    val imageLoader = remember(context.applicationContext) {
        PlayerArtworkLoader.get(context.applicationContext)
    }
    val artworkSizePx = with(LocalDensity.current) {
        180.dp.roundToPx().coerceIn(1, 512)
    }
    val artworkRequest = remember(
        context.applicationContext,
        track?.artworkUri,
        artworkSizePx
    ) {
        ImageRequest.Builder(context.applicationContext)
            .data(track?.artworkUri)
            .size(artworkSizePx, artworkSizePx)
            .precision(Precision.EXACT)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .heightIn(max = 865.dp)
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.77f)
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
                    model = artworkRequest,
                    imageLoader = imageLoader,
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
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(20.dp)
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
                    modifier = if (marqueeLongTitles && (track?.title?.length ?: 0)> 24) {
                        Modifier.fillMaxWidth().basicMarquee()
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

            Spacer(Modifier.height(2.dp))

            Slider(
                value = sliderValue,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSeekFinished,
                enabled = durationMs > 0L
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
                        contentDescription = if (isPlaying) "Pause" else "Play",
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
                        imageVector = if (
                            repeatMode == Player.REPEAT_MODE_ONE
                        ) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (
                            repeatMode == Player.REPEAT_MODE_ONE
                        ) {
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
                    if (isRolling) "Rolling..." else "Roll random track"
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L

    return "%d:%02d".format(
        totalSeconds / 60L,
        totalSeconds % 60L
    )
}