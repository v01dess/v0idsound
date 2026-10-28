package com.example.v0idsound

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: IndexerSettings,
    hiddenTracks: List<Track>,
    onUnhideTrack: (Track) -> Unit,
    onSetAccentVibrancy: (Float) -> Unit,
    onSetShowRamUsage: (Boolean) -> Unit,
    onSetMinimumDuration: (Int) -> Unit,
    onSetOnlyMusic: (Boolean) -> Unit,
    onSetHideRecordings: (Boolean) -> Unit,
    onSetSortByName: (Boolean) -> Unit,
    onSetRescanOnStartup: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onSetBroadScan: (Boolean) -> Unit,
    onAddScanFolder: (Uri) -> Unit,
    onRemoveScanFolder: (String) -> Unit,
    onSetMaxFolderDepth: (Int) -> Unit,
    onSetUseMediaStore: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccentColor: (AccentColor) -> Unit,
    onSetAccentArgb: (Int) -> Unit,
    onSetMarqueeLongTitles: (Boolean) -> Unit,
    onSetShowAlbumTileArtwork: (Boolean) -> Unit,
    onSetAlbumTileStyle: (String) -> Unit
) {
    var categoryName by rememberSaveable {
        mutableStateOf(SettingsCategory.INDEXING.name)
    }

    val selectedCategory = SettingsCategory.entries.firstOrNull {
        it.name == categoryName
    } ?: SettingsCategory.INDEXING

    var durationInput by remember {
        mutableStateOf(settings.minimumDurationSeconds.toString())
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val activity = remember(context) {
        context.findSettingsActivity()
    }

    val updates = remember(activity) {
        ViewModelProvider(activity)[SettingsUpdateViewModel::class.java]
    }

    val installerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updates.reportMessage(
            "Installer closed. If you cancelled installation, " +
                    "you can tap Install update again."
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updates.requestInstallationResume()
    }

    fun installUpdate(allowPermissionRequest: Boolean) {
        updates.prepareInstallation(
            context = context.applicationContext,
            onResult = { result ->
                when (result) {
                    is UpdateInstallResult.Ready -> {
                        try {
                            installerLauncher.launch(
                                result.installerIntent
                            )
                            updates.reportMessage(
                                "Confirm the update in Android's installer."
                            )
                        } catch (exception: Exception) {
                            updates.reportError(
                                "Could not open Android's package installer."
                            )
                        }
                    }

                    is UpdateInstallResult.PermissionRequired -> {
                        if (allowPermissionRequest) {
                            try {
                                permissionLauncher.launch(
                                    result.settingsIntent
                                )
                                updates.reportMessage(
                                    "Allow installations from v0idsound, " +
                                            "then return to the app."
                                )
                            } catch (exception: Exception) {
                                updates.reportError(
                                    "Could not open installation permissions. " +
                                            "Check Android Settings for permission " +
                                            "to install unknown apps."
                                )
                            }
                        } else {
                            updates.reportError(
                                "Installation permission is still disabled. " +
                                        "Tap Install update to try again."
                            )
                        }
                    }

                    is UpdateInstallResult.Failure -> {
                        updates.reportError(result.message)
                    }
                }
            }
        )
    }

    LaunchedEffect(updates.resumeInstallationRequested) {
        if (updates.resumeInstallationRequested) {
            updates.consumeInstallationResume()
            installUpdate(allowPermissionRequest = false)
        }
    }

    fun openReleasePage() {
        try {
            uriHandler.openUri(
                "${UpdateRepository.REPOSITORY_URL}/releases"
            )
        } catch (exception: Exception) {
            updates.reportError(
                "Could not open a browser. Visit " +
                        "${UpdateRepository.REPOSITORY_URL}/releases manually."
            )
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onAddScanFolder)
    }

    LaunchedEffect(settings.minimumDurationSeconds) {
        durationInput = settings.minimumDurationSeconds.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        categoryName = category.name
                    },
                    label = {
                        Text(category.label)
                    }
                )
            }
        }

        when (selectedCategory) {
            SettingsCategory.INDEXING -> {
                IndexingSettings(
                    settings = settings,
                    hiddenTracks = hiddenTracks,
                    onUnhideTrack = onUnhideTrack,
                    durationInput = durationInput,
                    onDurationInputChange = { newValue ->
                        val digitsOnly = newValue.filter {
                            it.isDigit()
                        }
                        durationInput = digitsOnly

                        digitsOnly.toIntOrNull()?.let { seconds ->
                            if (seconds in 0..3600) {
                                onSetMinimumDuration(seconds)
                            }
                        }
                    },
                    pickFolder = {
                        pickFolder.launch(null)
                    },
                    onSetOnlyMusic = onSetOnlyMusic,
                    onSetHideRecordings = onSetHideRecordings,
                    onSetBroadScan = onSetBroadScan,
                    onRemoveScanFolder = onRemoveScanFolder,
                    onSetMaxFolderDepth = onSetMaxFolderDepth,
                    onSetUseMediaStore = onSetUseMediaStore,
                    onSetSortByName = onSetSortByName,
                    onSetRescanOnStartup = onSetRescanOnStartup,
                    onRescan = onRescan
                )
            }

            SettingsCategory.CUSTOMIZATION -> {
                CustomizationSettings(
                    settings = settings,
                    onSetAccentVibrancy = onSetAccentVibrancy,
                    onSetShowRamUsage = onSetShowRamUsage,
                    onSetDarkTheme = onSetDarkTheme,
                    onSetAccentColor = onSetAccentColor,
                    onSetAccentArgb = onSetAccentArgb,
                    onSetMarqueeLongTitles = onSetMarqueeLongTitles,
                    onSetShowAlbumTileArtwork =
                        onSetShowAlbumTileArtwork,
                    onSetAlbumTileStyle = onSetAlbumTileStyle
                )
            }

            SettingsCategory.UPDATES -> {
                UpdateSettings(
                    updates = updates,
                    onDownload = {
                        updates.downloadUpdate(
                            context.applicationContext
                        )
                    },
                    onInstall = {
                        installUpdate(allowPermissionRequest = true)
                    },
                    onOpenReleases = {
                        openReleasePage()
                    }
                )
            }
        }
    }

    val availableRelease =
        (updates.result as? UpdateCheckResult.UpdateAvailable)?.release

    if (updates.showDialog && availableRelease != null) {
        SettingsUpdateDialog(
            release = availableRelease,
            updates = updates,
            onDownload = {
                updates.downloadUpdate(context.applicationContext)
            },
            onInstall = {
                installUpdate(allowPermissionRequest = true)
            },
            onOpenReleases = {
                openReleasePage()
            }
        )
    }
}

@Composable
private fun UpdateSettings(
    updates: SettingsUpdateViewModel,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: () -> Unit
) {
    // Auto-check on first composition
    LaunchedEffect(Unit) {
        updates.checkForUpdates()
    }

    Text(
        text = "Updates",
        style = MaterialTheme.typography.titleLarge
    )

    Text(
        text = "Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodyMedium
    )

    Button(
        onClick = { updates.checkForUpdates() },
        enabled = !updates.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (updates.isChecking) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text("Checking...")
            }
        } else {
            Text("Check for updates")
        }
    }

    when (val result = updates.result) {
        null -> Unit

        is UpdateCheckResult.UpToDate -> {
            Text(
                text = "Up to date. Latest release: ${result.latestVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateCheckResult.NoPublishedRelease -> {
            Text(
                text = "No published release found on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is UpdateCheckResult.Failure -> {
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        is UpdateCheckResult.UpdateAvailable -> {
            Text(
                text = "Update available: ${result.release.versionName}",
                style = MaterialTheme.typography.titleSmall
            )

            UpdateTransferControls(
                release = result.release,
                updates = updates,
                onDownload = onDownload,
                onInstall = onInstall
            )
        }
    }

    UpdateMessages(updates)

    HorizontalDivider()

    OutlinedButton(
        onClick = onOpenReleases,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open GitHub releases")
    }
}

@Composable
private fun SettingsUpdateDialog(
    release: AppRelease,
    updates: SettingsUpdateViewModel,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            updates.closeDialog()
        },
        title = {
            Text("Update available")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Installed: ${BuildConfig.VERSION_NAME}\n" +
                            "Available: ${release.versionName}"
                )

                release.apkName?.let {
                    Text(
                        text = "APK: $it",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                release.apkSizeBytes?.let {
                    Text(
                        text = "Download size: ${formatUpdateBytes(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Release notes",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = release.notes,
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                UpdateTransferControls(
                    release = release,
                    updates = updates,
                    onDownload = onDownload,
                    onInstall = onInstall
                )

                UpdateMessages(updates)

                Text(
                    text = "Android will ask you to approve installation. " +
                            "If the APK has a different signing certificate, " +
                            "the updater will block it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenReleases) {
                Text("GitHub")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { updates.closeDialog() }
            ) {
                Text(
                    if (updates.isDownloading) {
                        "Keep downloading"
                    } else {
                        "Close"
                    }
                )
            }
        }
    )
}

@Composable
private fun UpdateTransferControls(
    release: AppRelease,
    updates: SettingsUpdateViewModel,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            updates.isDownloading -> {
                val total = updates.totalBytes

                if (total != null && total > 0L) {
                    val fraction = (
                            updates.downloadedBytes.toDouble() /
                                    total.toDouble()
                            ).toFloat().coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${formatUpdateBytes(updates.downloadedBytes)}" +
                                " / ${formatUpdateBytes(total)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Downloaded " +
                                formatUpdateBytes(updates.downloadedBytes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Downloading and validating the APK...",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    onClick = { updates.cancelDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel download")
                }
            }

            updates.isPreparing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Checking APK before installation...")
                }
            }

            updates.downloadedUpdate != null -> {
                Text(
                    text = "Download ready: " +
                            updates.downloadedUpdate!!.versionName,
                    style = MaterialTheme.typography.titleSmall
                )

                Button(
                    onClick = onInstall,
                    enabled = !updates.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install update")
                }
            }

            release.apkDownloadUrl != null -> {
                Button(
                    onClick = onDownload,
                    enabled = !updates.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download update")
                }
            }

            else -> {
                Text(
                    text = "No single suitable APK was identified. " +
                            "The release needs one APK, or one APK with " +
                            "\"universal\" in its name.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun UpdateMessages(updates: SettingsUpdateViewModel) {
    updates.error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }

    updates.message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/*
 * Activity-scoped state keeps a download running across rotation
 * and while switching away from Settings within the same Activity.
 * It does not survive Android killing the app process.
 */
class SettingsUpdateViewModel : ViewModel() {

    private val repository = UpdateRepository()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private var downloadJob: Job? = null

    var result by mutableStateOf<UpdateCheckResult?>(null)
        private set

    var isChecking by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var isPreparing by mutableStateOf(false)
        private set

    var downloadedBytes by mutableStateOf(0L)
        private set

    var totalBytes by mutableStateOf<Long?>(null)
        private set

    var downloadedUpdate by mutableStateOf<DownloadedUpdate?>(null)
        private set

    var showDialog by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var resumeInstallationRequested by mutableStateOf(false)
        private set

    val isBusy: Boolean
        get() = isChecking || isDownloading || isPreparing

    fun checkForUpdates() {
        if (isBusy) return

        isChecking = true
        error = null
        message = null
        result = null
        downloadedUpdate = null
        showDialog = false

        scope.launch {
            try {
                val checkResult = repository.checkForUpdates()
                result = checkResult
                showDialog =
                    checkResult is UpdateCheckResult.UpdateAvailable
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = "Could not check for updates. Please retry."
            } finally {
                isChecking = false
            }
        }
    }

    fun downloadUpdate(context: Context) {
        if (isBusy) return

        val release = (
                result as? UpdateCheckResult.UpdateAvailable
                )?.release ?: return

        val appContext = context.applicationContext

        isDownloading = true
        downloadedBytes = 0L
        totalBytes = release.apkSizeBytes
        downloadedUpdate = null
        error = null
        message = null

        downloadJob = scope.launch {
            try {
                when (
                    val downloadResult = repository.downloadUpdate(
                        context = appContext,
                        release = release,
                        onProgress = { received, total ->
                            downloadedBytes = received
                            totalBytes = total
                        }
                    )
                ) {
                    is UpdateDownloadResult.Success -> {
                        downloadedUpdate = downloadResult.update
                        message =
                            "APK downloaded and compatibility checks passed. " +
                                    "Tap Install update to continue."
                    }

                    is UpdateDownloadResult.Failure -> {
                        error = downloadResult.message
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = "The download failed. Please retry."
            } finally {
                isDownloading = false
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        if (!isDownloading) return

        message = "Cancelling download..."
        downloadJob?.cancel()
    }

    fun prepareInstallation(
        context: Context,
        onResult: (UpdateInstallResult) -> Unit
    ) {
        if (isBusy) return

        val update = downloadedUpdate

        if (update == null) {
            reportError(
                "No downloaded update is available. Download it again."
            )
            return
        }

        val appContext = context.applicationContext

        isPreparing = true
        error = null
        message = null

        scope.launch {
            try {
                val installResult = repository.prepareInstallation(
                    context = appContext,
                    update = update
                )

                isPreparing = false
                onResult(installResult)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportError("Could not prepare installation. Please retry.")
            } finally {
                isPreparing = false
            }
        }
    }

    fun requestInstallationResume() {
        resumeInstallationRequested = true
    }

    fun consumeInstallationResume() {
        resumeInstallationRequested = false
    }

    fun openDialog() {
        showDialog = true
    }

    fun closeDialog() {
        showDialog = false
    }

    fun reportError(value: String) {
        error = value
        message = null
    }

    fun reportMessage(value: String) {
        message = value
        error = null
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private fun Context.findSettingsActivity(): ComponentActivity {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is ComponentActivity) {
            return current
        }
        current = current.baseContext
    }

    error("SettingsScreen must be hosted in a ComponentActivity.")
}

private fun formatUpdateBytes(bytes: Long): String {
    return String.format(
        Locale.ROOT,
        "%.1f MiB",
        bytes.toDouble() / (1024.0 * 1024.0)
    )
}

@Composable
private fun IndexingSettings(
    settings: IndexerSettings,
    hiddenTracks: List<Track>,
    onUnhideTrack: (Track) -> Unit,
    durationInput: String,
    onDurationInputChange: (String) -> Unit,
    pickFolder: () -> Unit,
    onSetOnlyMusic: (Boolean) -> Unit,
    onSetHideRecordings: (Boolean) -> Unit,
    onSetBroadScan: (Boolean) -> Unit,
    onRemoveScanFolder: (String) -> Unit,
    onSetMaxFolderDepth: (Int) -> Unit,
    onSetUseMediaStore: (Boolean) -> Unit,
    onSetSortByName: (Boolean) -> Unit,
    onSetRescanOnStartup: (Boolean) -> Unit,
    onRescan: () -> Unit
) {
    Text(
        text = "Indexing",
        style = MaterialTheme.typography.titleLarge
    )

    Text("Minimum track duration")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = durationInput,
            onValueChange = onDurationInputChange,
            label = { Text("Seconds") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )
        Text("seconds")
    }

    HorizontalDivider()

    SettingToggle(
        label = "Only show music",
        description =
            "Uses Android’s music flag instead of showing every audio file.",
        checked = settings.onlyMusic,
        onChange = onSetOnlyMusic
    )

    SettingToggle(
        label = "Hide recordings",
        description = "Hides voice recordings and similar audio files.",
        checked = settings.hideRecordings,
        onChange = onSetHideRecordings
    )

    SettingToggle(
        label = "Broad scan",
        description =
            "Include unusual audio formats and files with missing MIME metadata.",
        checked = settings.broadScan,
        onChange = onSetBroadScan
    )

    HorizontalDivider()

    Text(
        text = "Custom folders",
        style = MaterialTheme.typography.titleLarge
    )

    Button(
        onClick = pickFolder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add folder to scan")
    }

    if (settings.scanFolders.isEmpty()) {
        Text("No custom folders selected.")
    } else {
        settings.scanFolders.forEach { folderUri ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = folderUri.substringAfterLast("%3A"),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onRemoveScanFolder(folderUri) }
                ) {
                    Text("Remove")
                }
            }
        }
    }

    Text("Maximum folder depth: ${settings.maxFolderDepth}")

    Slider(
        value = settings.maxFolderDepth.toFloat(),
        onValueChange = { onSetMaxFolderDepth(it.toInt()) },
        valueRange = 1f..15f,
        steps = 13
    )

    SettingToggle(
        label = "Use Android music library",
        description = "Also include audio already indexed by MediaStore.",
        checked = settings.useMediaStore,
        onChange = onSetUseMediaStore
    )

    HorizontalDivider()

    SettingToggle(
        label = "Sort by title",
        description = "Off sorts tracks by the order Android returns them.",
        checked = settings.sortByName,
        onChange = onSetSortByName
    )

    SettingToggle(
        label = "Rescan library on startup",
        description = "Reloads the track list every time v0idsound opens.",
        checked = settings.rescanOnStartup,
        onChange = onSetRescanOnStartup
    )

    HorizontalDivider()

    Button(
        onClick = onRescan,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Rescan library now")
    }

    HorizontalDivider()

    if (hiddenTracks.isNotEmpty()) {
        Text(
            text = "Hidden tracks (${hiddenTracks.size})",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "Swipe left on a track in the player to hide it. " +
                    "Tap Unhide to bring it back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        hiddenTracks.forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(onClick = { onUnhideTrack(track) }) {
                    Text("Unhide")
                }
            }
        }
    }
}

@Composable
private fun CustomizationSettings(
    settings: IndexerSettings,
    onSetShowRamUsage: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccentVibrancy: (Float) -> Unit,
    onSetAccentColor: (AccentColor) -> Unit,
    onSetAccentArgb: (Int) -> Unit,
    onSetMarqueeLongTitles: (Boolean) -> Unit,
    onSetShowAlbumTileArtwork: (Boolean) -> Unit,
    onSetAlbumTileStyle: (String) -> Unit
) {
    var hexInput by remember {
        mutableStateOf(settings.accent.toHexColor())
    }
    var isPickerOpen by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(settings.accentArgb) {
        hexInput = settings.accent.toHexColor()
    }

    Text(
        text = "Customization",
        style = MaterialTheme.typography.titleLarge
    )

    SettingToggle(
        label = "Dark theme",
        description = "Use v0idsound’s dark color scheme.",
        checked = settings.useDarkTheme,
        onChange = onSetDarkTheme
    )

    SettingToggle(
        label = "Show RAM usage",
        description =
            "Display approximate process memory in the title bar.",
        checked = settings.showRamUsage,
        onChange = onSetShowRamUsage
    )

    SettingToggle(
        label = "Scroll long song titles",
        description =
            "Move long titles sideways in the player so the full title can be read.",
        checked = settings.marqueeLongTitles,
        onChange = onSetMarqueeLongTitles
    )

    SettingToggle(
        label = "Album tile artwork",
        description =
            "Show song covers instead of track-title previews in Albums.",
        checked = settings.showAlbumTileArtwork,
        onChange = onSetShowAlbumTileArtwork
    )

    if (settings.showAlbumTileArtwork) {
        Text(
            text = "Album tile style",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = settings.albumTileStyle == "ONE",
                onClick = { onSetAlbumTileStyle("ONE") },
                label = { Text("1 cover") }
            )
            FilterChip(
                selected = settings.albumTileStyle == "FOUR",
                onClick = { onSetAlbumTileStyle("FOUR") },
                label = { Text("4 covers") }
            )
        }
    }

    HorizontalDivider()

    Text("Accent vibrancy: ${settings.accentVibrancy}%")

    Slider(
        value = settings.accentVibrancy,
        onValueChange = onSetAccentVibrancy,
        valueRange = 0f..100f
    )

    Text(
        text = "Accent color",
        style = MaterialTheme.typography.titleLarge
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    settings.accent,
                    RoundedCornerShape(20.dp)
                )
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(20.dp)
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Selected color",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = settings.accent.toHexColor(),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    Button(
        onClick = { isPickerOpen = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open color picker")
    }

    Text(
        text = "Quick colors",
        style = MaterialTheme.typography.titleSmall
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected =
                settings.accentArgb == accent.color.toArgb()

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.color, CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clickable { onSetAccentColor(accent) }
            )
        }
    }

    OutlinedTextField(
        value = hexInput,
        onValueChange = { newValue ->
            val normalized = newValue
                .uppercase()
                .filter {
                    it == '#' || it.isDigit() || it in 'A'..'F'
                }
                .take(9)

            hexInput = normalized

            normalized.toArgbOrNull()?.let(onSetAccentArgb)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Custom hex color") },
        supportingText = { Text("Use #RRGGBB or #AARRGGBB") },
        singleLine = true,
        isError = hexInput.isNotBlank() &&
                hexInput.toArgbOrNull() == null
    )

    if (isPickerOpen) {
        AccentColorPickerDialog(
            initialColor = settings.accent,
            onDismiss = { isPickerOpen = false },
            onApply = {
                onSetAccentArgb(it.toArgb())
                isPickerOpen = false
            }
        )
    }
}

@Composable
private fun AccentColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also {
            AndroidColor.colorToHSV(initialColor.toArgb(), it)
        }
    }

    var hue by remember(initialColor) {
        mutableFloatStateOf(initialHsv[0])
    }
    var saturation by remember(initialColor) {
        mutableFloatStateOf(initialHsv[1])
    }
    var value by remember(initialColor) {
        mutableFloatStateOf(initialHsv[2])
    }

    val selectedColor = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(hue, saturation, value)
        )
    )
    val pureHue = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(hue, 1f, 1f)
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose accent color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                selectedColor,
                                RoundedCornerShape(14.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                    )
                    Column {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = selectedColor.toHexColor(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Text("Color and brightness")

                SaturationValuePicker(
                    hueColor = pureHue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    }
                )

                Text("Hue")

                HuePicker(
                    hue = hue,
                    onHueChange = { hue = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selectedColor) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaturationValuePicker(
    hueColor: Color,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit
) {
    fun updateFromPosition(
        position: Offset,
        width: Float,
        height: Float
    ) {
        if (width <= 0f || height <= 0f) return

        onChange(
            (position.x / width).coerceIn(0f, 1f),
            (1f - position.y / height).coerceIn(0f, 1f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.White, hueColor)
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black)
                    )
                )

                val marker = Offset(
                    saturation * size.width,
                    (1f - value) * size.height
                )

                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.Black,
                    radius = 15.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    updateFromPosition(
                        it,
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateFromPosition(
                        change.position,
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
            }
    )
}

@Composable
private fun HuePicker(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    fun updateHue(position: Offset, width: Float) {
        if (width <= 0f) return

        onHueChange(
            ((position.x / width) * 360f).coerceIn(0f, 360f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    )
                )

                val marker = Offset(
                    (hue / 360f) * size.width,
                    size.height / 2f
                )

                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.Black,
                    radius = 13.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    updateHue(it, size.width.toFloat())
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateHue(change.position, size.width.toFloat())
                }
            }
    )
}

@Composable
private fun SettingToggle(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

private fun Color.toHexColor(): String {
    return String.format(
        "#%06X",
        toArgb() and 0x00FFFFFF
    )
}

private fun String.toArgbOrNull(): Int? {
    val text = trim()

    val normalized = when {
        text.matches(Regex("^#[0-9A-Fa-f]{6}$")) -> text
        text.matches(Regex("^#[0-9A-Fa-f]{8}$")) -> text
        else -> return null
    }

    return runCatching {
        AndroidColor.parseColor(normalized)
    }.getOrNull()
}

enum class SettingsCategory(val label: String) {
    INDEXING("Indexing"),
    CUSTOMIZATION("Customization"),
    UPDATES("Updates")
}