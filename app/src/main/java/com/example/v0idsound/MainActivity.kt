package com.example.v0idsound

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeNeutral
import androidx.compose.ui.graphics.Color

private fun blend(a: Int, b: Int, t: Float): Int {
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val br = (b shr 16) and 0xFF
    val bg = (b shr 8) and 0xFF
    val bb = b and 0xFF

    val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
    val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
    val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val musicRepository by lazy { MusicRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val listeningStatsRepository by lazy {
        ListeningStatsRepository(applicationContext)
    }
    private val folderScanner by lazy { FolderScanner(applicationContext) }

    private var currentScreen by mutableStateOf(AppScreen.PLAYER)
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var currentTrack by mutableStateOf<Track?>(null)

    private var hiddenTracks by mutableStateOf<List<Track>>(emptyList())
    private var isPlaying by mutableStateOf(false)
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var repeatMode by mutableStateOf(Player.REPEAT_MODE_ALL)
    private var shuffleEnabled by mutableStateOf(false)
    private var hasAudioPermissionState by mutableStateOf(false)

    private var tracksByUri: Map<String, Track> = emptyMap()
    private var indicesByUri: Map<String, Int> = emptyMap()
    private var libraryMediaItems: List<MediaItem> = emptyList()

    private data class LoadedLibrary(
        val tracks: List<Track>,
        val byUri: Map<String, Track>,
        val indices: Map<String, Int>,
        val mediaItems: List<MediaItem>
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncPlayerState(player)
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermissionState = granted
    }

    private val requestAllFilesAccess = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAudioPermissionState = hasAudioPermission()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissionIfNeeded() {
        if (hasAudioPermission()) return
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestAudioPermission.launch(permission)
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestAllFilesAccessIfNeeded() {
        if (hasAllFilesAccess()) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            requestAllFilesAccess.launch(intent)
        } catch (exception: Exception) {
            requestAllFilesAccess.launch(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAudioPermissionState = hasAudioPermission()
        val launchUpdateViewModel = ViewModelProvider(this)[
            LaunchUpdateViewModel::class.java
        ]

        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = IndexerSettings()
            )
            val listeningStats by listeningStatsRepository.stats.collectAsState(
                initial = emptyMap<String, ListeningStats>()
            )
            val colorScheme = remember(settings.useDarkTheme, settings.accentArgb, settings.accentVibrancy) {
                val sourceHct = Hct.fromInt(settings.accentArgb)
                val t = settings.accentVibrancy / 100f

                val tonal = SchemeTonalSpot(
                    sourceColorHct = sourceHct,
                    isDark = settings.useDarkTheme,
                    contrastLevel = 0.0
                )

                val neutral = SchemeNeutral(
                    sourceColorHct = sourceHct,
                    isDark = settings.useDarkTheme,
                    contrastLevel = 0.0
                )

                if (settings.useDarkTheme) {
                    darkColorScheme(
                        primary = Color(blend(neutral.primary, tonal.primary, t)),
                        onPrimary = Color(tonal.onPrimary),
                        primaryContainer = Color(blend(neutral.primaryContainer, tonal.primaryContainer, t)),
                        onPrimaryContainer = Color(tonal.onPrimaryContainer),
                        secondary = Color(blend(neutral.secondary, tonal.secondary, t)),
                        onSecondary = Color(tonal.onSecondary),
                        secondaryContainer = Color(blend(neutral.secondaryContainer, tonal.secondaryContainer, t)),
                        onSecondaryContainer = Color(tonal.onSecondaryContainer),
                        tertiary = Color(blend(neutral.tertiary, tonal.tertiary, t)),
                        onTertiary = Color(tonal.onTertiary),
                        tertiaryContainer = Color(blend(neutral.tertiaryContainer, tonal.tertiaryContainer, t)),
                        onTertiaryContainer = Color(tonal.onTertiaryContainer),
                        error = Color(tonal.error),
                        onError = Color(tonal.onError),
                        errorContainer = Color(tonal.errorContainer),
                        onErrorContainer = Color(tonal.onErrorContainer),
                        background = Color(tonal.background),
                        onBackground = Color(tonal.onBackground),
                        surface = Color(tonal.surface),
                        onSurface = Color(tonal.onSurface),
                        surfaceVariant = Color(tonal.surfaceVariant),
                        onSurfaceVariant = Color(tonal.onSurfaceVariant),
                        outline = Color(tonal.outline),
                        outlineVariant = Color(tonal.outlineVariant),
                        inverseSurface = Color(tonal.inverseSurface),
                        inverseOnSurface = Color(tonal.inverseOnSurface),
                        inversePrimary = Color(tonal.inversePrimary),
                        surfaceTint = Color(blend(neutral.primary, tonal.primary, t))
                    )
                } else {
                    lightColorScheme(
                        primary = Color(blend(neutral.primary, tonal.primary, t)),
                        onPrimary = Color(tonal.onPrimary),
                        primaryContainer = Color(blend(neutral.primaryContainer, tonal.primaryContainer, t)),
                        onPrimaryContainer = Color(tonal.onPrimaryContainer),
                        secondary = Color(blend(neutral.secondary, tonal.secondary, t)),
                        onSecondary = Color(tonal.onSecondary),
                        secondaryContainer = Color(blend(neutral.secondaryContainer, tonal.secondaryContainer, t)),
                        onSecondaryContainer = Color(tonal.onSecondaryContainer),
                        tertiary = Color(blend(neutral.tertiary, tonal.tertiary, t)),
                        onTertiary = Color(tonal.onTertiary),
                        tertiaryContainer = Color(blend(neutral.tertiaryContainer, tonal.tertiaryContainer, t)),
                        onTertiaryContainer = Color(tonal.onTertiaryContainer),
                        error = Color(tonal.error),
                        onError = Color(tonal.onError),
                        errorContainer = Color(tonal.errorContainer),
                        onErrorContainer = Color(tonal.onErrorContainer),
                        background = Color(tonal.background),
                        onBackground = Color(tonal.onBackground),
                        surface = Color(tonal.surface),
                        onSurface = Color(tonal.onSurface),
                        surfaceVariant = Color(tonal.surfaceVariant),
                        onSurfaceVariant = Color(tonal.onSurfaceVariant),
                        outline = Color(tonal.outline),
                        outlineVariant = Color(tonal.outlineVariant),
                        inverseSurface = Color(tonal.inverseSurface),
                        inverseOnSurface = Color(tonal.inverseOnSurface),
                        inversePrimary = Color(tonal.inversePrimary),
                        surfaceTint = Color(blend(neutral.primary, tonal.primary, t))
                    )
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    requestAudioPermissionIfNeeded()
                    requestAllFilesAccessIfNeeded()
                }

                LaunchedEffect(
                    hasAudioPermissionState,
                    settings.scanFolders,
                    settings.maxFolderDepth,
                    settings.useMediaStore,
                    settings.minimumDurationSeconds,
                    settings.onlyMusic,
                    settings.hideRecordings,
                    settings.sortByName,
                    settings.broadScan
                ) {
                    if (hasAudioPermissionState) reloadLibrary(settings)
                }

                // Polling reads state only. It never edits or prepares playback.
                LaunchedEffect(Unit) {
                    while (true) {
                        controller?.let { syncPlayerState(it) }
                        delay(250L)
                    }
                }

                AppScaffold(
                    currentScreen = currentScreen,
                    onScreenChange = { currentScreen = it },
                    showRamUsage = settings.showRamUsage
                ) {
                    when (currentScreen) {
                        AppScreen.PLAYER -> PlayerScreen(
                            tracks = tracks,
                            listeningStats = listeningStats,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            repeatMode = repeatMode,
                            shuffleEnabled = shuffleEnabled,
                            marqueeLongTitles = settings.marqueeLongTitles,
                            showAlbumTileArtwork = settings.showAlbumTileArtwork,
                            albumTileStyle = settings.albumTileStyle,
                            onRepeatClick = {
                                controller?.let { player ->
                                    player.repeatMode = if (
                                        player.repeatMode == Player.REPEAT_MODE_ONE
                                    ) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                                    syncPlayerState(player)
                                }
                            },
                            onShuffleClick = {
                                controller?.let { player ->
                                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                                    syncPlayerState(player)
                                }
                            },
                            onPlayPause = {
                                controller?.let { player ->
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            },
                            onPrevious = { controller?.seekToPreviousMediaItem() },
                            onNext = {
                                controller?.let { player ->
                                    val count = player.mediaItemCount
                                    if (count > 0) {
                                        val nextIndex = (player.currentMediaItemIndex + 1) % count
                                        player.seekTo(nextIndex, 0L)
                                    }
                                }
                            },
                            onSeek = { fraction ->
                                controller?.let { player ->
                                    if (player.duration > 0L) {
                                        player.seekTo(
                                            (fraction.coerceIn(0f, 1f) * player.duration).toLong()
                                        )
                                    }
                                }
                            },
                            onTrackClick = { playTrack(it) },
                            onTrackSwipeLeft = { track ->
                                scope.launch {
                                    musicRepository.setHidden(track.uri.toString(), true)
                                    reloadLibrary(settings)
                                }
                            },

                            onTrackSwipeLeftUndo = { track ->
                                scope.launch {
                                    musicRepository.setHidden(track.uri.toString(), false)
                                    reloadLibrary(settings)
                                }
                            },
                        )

                        AppScreen.STATISTICS -> StatisticsScreen(
                            tracks = tracks,
                            listeningStats = listeningStats,
                            onTrackClick = {
                                playTrack(it)
                                currentScreen = AppScreen.PLAYER
                            }
                        )

                        AppScreen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            hiddenTracks = hiddenTracks,
                            onUnhideTrack = { track ->
                                scope.launch {
                                    musicRepository.setHidden(track.uri.toString(), false)
                                    reloadLibrary(settings)
                                }
                            },
                            onSetAccentVibrancy = { value ->          // ← add this
                                scope.launch { settingsRepository.setAccentVibrancy(value) }
                            },
                            onSetShowRamUsage = { value ->
                                scope.launch { settingsRepository.setShowRamUsage(value) }
                            },
                            onSetMinimumDuration = { value ->
                                scope.launch { settingsRepository.setMinimumDuration(value) }
                            },
                            onSetOnlyMusic = { value ->
                                scope.launch { settingsRepository.setOnlyMusic(value) }
                            },
                            onSetHideRecordings = { value ->
                                scope.launch { settingsRepository.setHideRecordings(value) }
                            },
                            onSetSortByName = { value ->
                                scope.launch { settingsRepository.setSortByName(value) }
                            },
                            onSetRescanOnStartup = { value ->
                                scope.launch { settingsRepository.setRescanOnStartup(value) }
                            },
                            onSetBroadScan = { value ->
                                scope.launch { settingsRepository.setBroadScan(value) }
                            },
                            onAddScanFolder = { uri ->
                                try {
                                    contentResolver.takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                    scope.launch {
                                        settingsRepository.addScanFolder(uri.toString())
                                    }
                                } catch (exception: SecurityException) {
                                    Log.e(TAG, "Could not retain folder permission", exception)
                                }
                            },
                            onRemoveScanFolder = { uri ->
                                scope.launch { settingsRepository.removeScanFolder(uri) }
                            },
                            onSetMaxFolderDepth = { value ->
                                scope.launch { settingsRepository.setMaxFolderDepth(value) }
                            },
                            onSetUseMediaStore = { value ->
                                scope.launch { settingsRepository.setUseMediaStore(value) }
                            },
                            onSetDarkTheme = { value ->
                                scope.launch { settingsRepository.setDarkTheme(value) }
                            },
                            onSetAccentColor = { value ->
                                scope.launch { settingsRepository.setAccentColor(value) }
                            },
                            onSetAccentArgb = { value ->
                                scope.launch { settingsRepository.setAccentArgb(value) }
                            },
                            onSetMarqueeLongTitles = { value ->
                                scope.launch { settingsRepository.setMarqueeLongTitles(value) }
                            },
                            onSetShowAlbumTileArtwork = { value ->
                                scope.launch { settingsRepository.setShowAlbumTileArtwork(value) }
                            },
                            onSetAlbumTileStyle = { value ->
                                scope.launch { settingsRepository.setAlbumTileStyle(value) }
                            },
                            onRescan = { scope.launch { reloadLibrary(settings) } }
                        )
                    }
                }

                val availableUpdate = launchUpdateViewModel.availableUpdate
                if (availableUpdate != null) {
                    AlertDialog(
                        onDismissRequest = { launchUpdateViewModel.dismissUpdate() },
                        title = { Text("Update available") },
                        text = {
                            Column(
                                modifier = Modifier.heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = availableUpdate.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Installed: ${BuildConfig.VERSION_NAME}\n" +
                                            "Available: ${availableUpdate.versionName}"
                                )
                                Text(
                                    text = availableUpdate.notes,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Open GitHub to view the release. " +
                                            "This button does not automatically " +
                                            "download or install an update.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (openGitHubReleases()) launchUpdateViewModel.dismissUpdate()
                            }) { Text("View release") }
                        },
                        dismissButton = {
                            TextButton(onClick = { launchUpdateViewModel.dismissUpdate() }) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
        connectController()
    }

    private fun openGitHubReleases(): Boolean {
        return try {
            startActivity(Intent(
                Intent.ACTION_VIEW,
                Uri.parse("${UpdateRepository.REPOSITORY_URL}/releases")
            ))
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Could not open GitHub releases", exception)
            Toast.makeText(
                this,
                "Could not open a browser. Try again from Settings → Updates.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isDestroyed && !future.isCancelled) {
                try {
                    val connectedController = future.get()
                    controller = connectedController
                    connectedController.addListener(playerListener)
                    syncPlayerState(connectedController)
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not connect to playback service", exception)
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun reloadLibrary(settings: IndexerSettings) {
        try {
            val library = withContext(Dispatchers.IO) {
                val loaded = musicRepository.loadTracksWithFolders(
                    settings = settings,
                    folderScanner = folderScanner
                )
                LoadedLibrary(
                    tracks = loaded,
                    byUri = loaded.associateBy { it.uri.toString() },
                    indices = loaded.withIndex().associate {
                        it.value.uri.toString() to it.index
                    },
                    mediaItems = loaded.map { buildMediaItem(it) }
                )
            }
            tracksByUri = library.byUri
            indicesByUri = library.indices
            libraryMediaItems = library.mediaItems
            tracks = library.tracks
            refreshHiddenTracks(settings)
            controller?.let { syncPlayerState(it) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Could not load music library", exception)
        }
    }

    private suspend fun refreshHiddenTracks(settings: IndexerSettings) {
        val hiddenUris = musicRepository.getHiddenUris()
        if (hiddenUris.isEmpty()) {
            hiddenTracks = emptyList()
            return
        }
        hiddenTracks = withContext(Dispatchers.IO) {
            musicRepository.loadAllTracks(settings, folderScanner)
                .filter { it.uri.toString() in hiddenUris }
        }
    }

    private fun buildMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.uri.toString())
            .setUri(track.uri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(track.uri).build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
    }

    private fun matchesLibraryQueue(player: Player): Boolean {
        if (player.mediaItemCount != libraryMediaItems.size) return false
        return libraryMediaItems.indices.all { index ->
            player.getMediaItemAt(index).mediaId == libraryMediaItems[index].mediaId
        }
    }

    private fun playTrack(track: Track) {
        val player = controller ?: return
        val index = indicesByUri[track.uri.toString()] ?: return
        if (libraryMediaItems.isEmpty()) return

        if (matchesLibraryQueue(player)) {
            player.seekTo(index, 0L)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        } else {
            // Keep the diagnostic's clicked-item-only artwork metadata policy.
            // No decoded images are stored in this playlist.
            val queue = libraryMediaItems.toMutableList()
            val selectedItem = queue[index]
            queue[index] = selectedItem.buildUpon()
                .setMediaMetadata(
                    selectedItem.mediaMetadata.buildUpon()
                        .setArtworkUri(track.artworkUri)
                        .build()
                )
                .build()
            player.setMediaItems(queue, index, 0L)
            player.prepare()
        }
        player.play()
        syncPlayerState(player)
    }

    private fun syncPlayerState(player: Player) {
        // Deliberately read-only with respect to the player and its playlist.
        isPlaying = player.isPlaying
        repeatMode = player.repeatMode
        shuffleEnabled = player.shuffleModeEnabled
        val item = player.currentMediaItem
        if (item == null) {
            currentTrack = null
            positionMs = 0L
            durationMs = 0L
            return
        }
        val previousTrack = currentTrack
        val resolvedTrack = findTrack(item)
        currentTrack = resolvedTrack
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = when {
            player.duration > 0L -> player.duration
            resolvedTrack != null && resolvedTrack.uri == previousTrack?.uri ->
                durationMs.coerceAtLeast(resolvedTrack.durationMs)
            else -> resolvedTrack?.durationMs ?: 0L
        }
    }

    private fun findTrack(item: MediaItem): Track? {
        tracksByUri[item.mediaId]?.let { return it }
        val requestUri = item.requestMetadata.mediaUri?.toString()
        if (requestUri != null) tracksByUri[requestUri]?.let { return it }
        val localUri = item.localConfiguration?.uri?.toString()
        if (localUri != null) tracksByUri[localUri]?.let { return it }
        currentTrack?.let { previous ->
            val uri = previous.uri.toString()
            if (uri == item.mediaId || uri == requestUri || uri == localUri) return previous
        }
        val uriString = when {
            item.mediaId.startsWith("content://") ||
                    item.mediaId.startsWith("file://") -> item.mediaId
            requestUri != null -> requestUri
            localUri != null -> localUri
            else -> return null
        }
        val metadata = item.mediaMetadata
        return Track(
            id = uriString.hashCode().toLong(),
            title = metadata.title?.toString() ?: "Unknown title",
            artist = metadata.artist?.toString() ?: "Unknown artist",
            album = metadata.albumTitle?.toString() ?: "Unknown album",
            durationMs = 0L,
            uri = Uri.parse(uriString),
            artworkUri = metadata.artworkUri
        )
    }

    override fun onResume() {
        super.onResume()
        hasAudioPermissionState = hasAudioPermission()
        controller?.let { syncPlayerState(it) }
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "v0idsound"
    }
}

class LaunchUpdateViewModel : ViewModel() {
    private val checkScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )
    var availableUpdate by mutableStateOf<AppRelease?>(null)
        private set

    init {
        checkScope.launch {
            try {
                val result = UpdateRepository().checkForUpdates()
                if (result is UpdateCheckResult.UpdateAvailable) {
                    availableUpdate = result.release
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Automatic failures must not interrupt the user.
            }
        }
    }

    fun dismissUpdate() {
        availableUpdate = null
    }

    override fun onCleared() {
        checkScope.cancel()
        super.onCleared()
    }
}