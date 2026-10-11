package com.example.v0idsound

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null

    private val musicRepository by lazy {
        MusicRepository(applicationContext)
    }

    private val settingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    private val folderScanner by lazy {
        FolderScanner(applicationContext)
    }

    private var currentScreen by mutableStateOf(AppScreen.PLAYER)

    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var currentTrack by mutableStateOf<Track?>(null)

    private var isPlaying by mutableStateOf(false)
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)

    private var repeatMode by mutableStateOf(Player.REPEAT_MODE_ALL)
    private var shuffleEnabled by mutableStateOf(false)

    private var hasAudioPermissionState by mutableStateOf(false)

    private val requestAudioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasAudioPermissionState = granted
        }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        if (hasAudioPermission()) {
            return
        }

        val permission = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        requestAudioPermission.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(
            this,
            ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        val future = MediaController.Builder(
            this,
            token
        ).buildAsync()

        future.addListener(
            {
                controller = future.get()

                controller?.addListener(
                    object : Player.Listener {

                        override fun onIsPlayingChanged(
                            playing: Boolean
                        ) {
                            isPlaying = playing
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            currentTrack = tracks.firstOrNull { track ->
                                track.uri.toString() ==
                                        mediaItem
                                            ?.localConfiguration
                                            ?.uri
                                            .toString()
                            }
                        }
                    }
                )

                setContent {
                    val settings by settingsRepository.settings.collectAsState(
                        initial = IndexerSettings()
                    )

                    val colorScheme = if (settings.useDarkTheme) {
                        darkColorScheme(
                            primary = settings.accent
                        )
                    } else {
                        lightColorScheme(
                            primary = settings.accent
                        )
                    }

                    MaterialTheme(
                        colorScheme = colorScheme
                    ) {
                        val scope = rememberCoroutineScope()

                        LaunchedEffect(Unit) {
                            hasAudioPermissionState = hasAudioPermission()

                            if (!hasAudioPermissionState) {
                                requestAudioPermissionIfNeeded()
                            }
                        }

                        LaunchedEffect(
                            settings,
                            hasAudioPermissionState
                        ) {
                            if (hasAudioPermissionState) {
                                tracks = musicRepository.loadTracksWithFolders(
                                    settings = settings,
                                    folderScanner = folderScanner
                                )
                            }
                        }

                        LaunchedEffect(currentTrack) {
                            while (currentTrack != null) {
                                controller?.let { player ->
                                    positionMs = player.currentPosition
                                        .coerceAtLeast(0L)

                                    durationMs = player.duration
                                        .takeIf { it > 0 }
                                        ?: durationMs
                                }

                                delay(250L)
                            }
                        }

                        AppScaffold(
                            currentScreen = currentScreen,
                            onScreenChange = { screen ->
                                currentScreen = screen
                            }
                        ) {
                            when (currentScreen) {
                                AppScreen.PLAYER -> {
                                    PlayerScreen(
                                        tracks = tracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        repeatMode = repeatMode,
                                        shuffleEnabled = shuffleEnabled,
                                        marqueeLongTitles =
                                            settings.marqueeLongTitles,
                                        onRepeatClick = {
                                            val nextMode =
                                                if (
                                                    repeatMode ==
                                                    Player.REPEAT_MODE_ONE
                                                ) {
                                                    Player.REPEAT_MODE_ALL
                                                } else {
                                                    Player.REPEAT_MODE_ONE
                                                }

                                            repeatMode = nextMode

                                            controller?.repeatMode = nextMode
                                        },
                                        onShuffleClick = {
                                            shuffleEnabled =
                                                !shuffleEnabled

                                            controller?.shuffleModeEnabled =
                                                shuffleEnabled
                                        },
                                        onPlayPause = {
                                            if (isPlaying) {
                                                controller?.pause()
                                            } else {
                                                controller?.play()
                                            }
                                        },
                                        onPrevious = {
                                            controller
                                                ?.seekToPreviousMediaItem()
                                        },
                                        onNext = {
                                            controller
                                                ?.seekToNextMediaItem()
                                        },
                                        onSeek = { fraction ->
                                            if (durationMs > 0) {
                                                controller?.seekTo(
                                                    (
                                                            fraction * durationMs
                                                            ).toLong()
                                                )
                                            }
                                        },
                                        onTrackClick = { track ->
                                            playTrack(track)
                                        }
                                    )
                                }

                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        settings = settings,
                                        onSetMinimumDuration = { seconds ->
                                            scope.launch {
                                                settingsRepository
                                                    .setMinimumDuration(
                                                        seconds
                                                    )
                                            }
                                        },
                                        onSetOnlyMusic = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setOnlyMusic(value)
                                            }
                                        },
                                        onSetHideRecordings = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setHideRecordings(
                                                        value
                                                    )
                                            }
                                        },
                                        onSetSortByName = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setSortByName(value)
                                            }
                                        },
                                        onSetRescanOnStartup = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setRescanOnStartup(
                                                        value
                                                    )
                                            }
                                        },
                                        onSetBroadScan = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setBroadScan(value)
                                            }
                                        },
                                        onAddScanFolder = { uri ->
                                            contentResolver
                                                .takePersistableUriPermission(
                                                    uri,
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                )

                                            scope.launch {
                                                settingsRepository
                                                    .addScanFolder(
                                                        uri.toString()
                                                    )
                                            }
                                        },
                                        onRemoveScanFolder = { uri ->
                                            scope.launch {
                                                settingsRepository
                                                    .removeScanFolder(uri)
                                            }
                                        },
                                        onSetMaxFolderDepth = { depth ->
                                            scope.launch {
                                                settingsRepository
                                                    .setMaxFolderDepth(depth)
                                            }
                                        },
                                        onSetUseMediaStore = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setUseMediaStore(value)
                                            }
                                        },
                                        onSetDarkTheme = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setDarkTheme(value)
                                            }
                                        },
                                        onSetAccentColor = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setAccentColor(value)
                                            }
                                        },
                                        onSetAccentArgb = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setAccentArgb(value)
                                            }
                                        },
                                        onSetMarqueeLongTitles = { value ->
                                            scope.launch {
                                                settingsRepository
                                                    .setMarqueeLongTitles(
                                                        value
                                                    )
                                            }
                                        },
                                        onRescan = {
                                            scope.launch {
                                                tracks =
                                                    musicRepository
                                                        .loadTracksWithFolders(
                                                            settings = settings,
                                                            folderScanner =
                                                                folderScanner
                                                        )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun playTrack(
        track: Track
    ) {
        val queue = tracks.map { queuedTrack ->
            MediaItem.Builder()
                .setUri(queuedTrack.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(queuedTrack.title)
                        .setArtist(queuedTrack.artist)
                        .setAlbumTitle(queuedTrack.album)
                        .build()
                )
                .build()
        }

        val startIndex = tracks.indexOf(track)

        controller?.apply {
            setMediaItems(
                queue,
                startIndex,
                0L
            )

            prepare()
            play()
        }

        currentTrack = track
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}