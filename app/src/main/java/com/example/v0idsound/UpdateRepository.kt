package com.example.v0idsound

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppRelease(
    val versionName: String,
    val title: String,
    val notes: String,
    val releasePageUrl: String,
    val apkName: String?,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long?,
    val apkSha256: String? = null
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val release: AppRelease
    ) : UpdateCheckResult

    data class UpToDate(
        val installedVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult

    data object NoPublishedRelease : UpdateCheckResult

    data class Failure(
        val message: String
    ) : UpdateCheckResult
}

data class DownloadedUpdate(
    val file: File,
    val versionName: String,
    val versionCode: Long,
    val sha256: String
)

sealed interface UpdateDownloadResult {
    data class Success(
        val update: DownloadedUpdate
    ) : UpdateDownloadResult

    data class Failure(
        val message: String
    ) : UpdateDownloadResult
}

sealed interface UpdateInstallResult {
    data class Ready(
        val installerIntent: Intent
    ) : UpdateInstallResult

    data class PermissionRequired(
        val settingsIntent: Intent
    ) : UpdateInstallResult

    data class Failure(
        val message: String
    ) : UpdateInstallResult
}

class UpdateRepository {

    suspend fun checkForUpdates(): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val response = requestLatestRelease()

                if (response == null) {
                    return@withContext UpdateCheckResult.NoPublishedRelease
                }

                val installedVersion = BuildConfig.VERSION_NAME
                val latestVersion =
                    response.optString("tag_name").trim()

                val comparison = compareVersions(
                    latestVersion,
                    installedVersion
                ) ?: return@withContext UpdateCheckResult.Failure(
                    "Could not compare versions. " +
                            "Use release tags such as v1.0 or v1.1.0."
                )

                if (comparison <= 0) {
                    return@withContext UpdateCheckResult.UpToDate(
                        installedVersion = installedVersion,
                        latestVersion = latestVersion
                    )
                }

                val apkAssets = buildList<JSONObject> {
                    val assets = response.optJSONArray("assets")

                    if (assets != null) {
                        for (index in 0 until assets.length()) {
                            val asset =
                                assets.optJSONObject(index) ?: continue

                            val name = asset.optString("name")
                            val downloadUrl =
                                asset.optString("browser_download_url")

                            if (
                                name.endsWith(".apk", ignoreCase = true) &&
                                isRepositoryDownload(downloadUrl)
                            ) {
                                add(asset)
                            }
                        }
                    }
                }

                val apk = apkAssets.singleOrNull()
                    ?: apkAssets.filter {
                        it.optString("name").contains(
                            "universal",
                            ignoreCase = true
                        )
                    }.singleOrNull()

                val digest = apk
                    ?.optString("digest")
                    ?.takeIf {
                        it.startsWith("sha256:", ignoreCase = true)
                    }
                    ?.substringAfter(":")
                    ?.lowercase()
                    ?.takeIf {
                        it.matches(Regex("[0-9a-f]{64}"))
                    }

                UpdateCheckResult.UpdateAvailable(
                    AppRelease(
                        versionName = latestVersion,
                        title = response.optString("name")
                            .takeIf {
                                it.isNotBlank() && it != "null"
                            }
                            ?: latestVersion,
                        notes = response.optString("body")
                            .takeUnless {
                                it.isBlank() || it == "null"
                            }
                            ?: "No release notes provided.",
                        releasePageUrl =
                            "$REPOSITORY_URL/releases",
                        apkName = apk?.optString("name"),
                        apkDownloadUrl =
                            apk?.optString("browser_download_url"),
                        apkSizeBytes = apk
                            ?.optLong("size", 0L)
                            ?.takeIf { it > 0L },
                        apkSha256 = digest
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                UpdateCheckResult.Failure(
                    exception.message
                        ?: "Could not connect to GitHub."
                )
            } catch (exception: Exception) {
                UpdateCheckResult.Failure(
                    "Could not read the GitHub release information."
                )
            }
        }

    /*
     * Progress callbacks are delivered on the main dispatcher.
     * totalBytes is null if no reliable size is available.
     */
    suspend fun downloadUpdate(
        context: Context,
        release: AppRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit =
            { _, _ -> }
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            var partialFile: File? = null

            try {
                val downloadUrl = release.apkDownloadUrl
                    ?: throw IOException(
                        "This release has no single suitable APK. " +
                                "Upload one APK, or one clearly named universal APK."
                    )

                if (!isRepositoryDownload(downloadUrl)) {
                    throw IOException(
                        "The APK download URL is not from this repository."
                    )
                }

                val directory = File(
                    context.applicationContext.cacheDir,
                    "updates"
                )

                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException(
                        "Could not create the update download folder."
                    )
                }

                val temporaryFile = File(
                    directory,
                    "v0idsound-update.apk.part"
                )
                partialFile = temporaryFile

                val finalFile = File(
                    directory,
                    "v0idsound-update.apk"
                )

                if (temporaryFile.exists() && !temporaryFile.delete()) {
                    throw IOException(
                        "Could not remove an incomplete update download."
                    )
                }

                val expectedSize = release.apkSizeBytes

                if (
                    expectedSize != null &&
                    expectedSize > MAX_APK_BYTES
                ) {
                    throw IOException(
                        "The APK exceeds the updater's 512 MiB limit."
                    )
                }

                val connection = openDownloadConnection(downloadUrl)

                var receivedBytes = 0L
                val digest = MessageDigest.getInstance("SHA-256")

                try {
                    val responseSize = connection.contentLengthLong
                        .takeIf { it > 0L }

                    if (
                        responseSize != null &&
                        responseSize > MAX_APK_BYTES
                    ) {
                        throw IOException(
                            "The APK exceeds the updater's 512 MiB limit."
                        )
                    }

                    if (
                        expectedSize != null &&
                        responseSize != null &&
                        expectedSize != responseSize
                    ) {
                        throw IOException(
                            "GitHub's APK size does not match the download. " +
                                    "Check for updates again."
                        )
                    }

                    val totalBytes = expectedSize ?: responseSize

                    if (
                        totalBytes != null &&
                        directory.usableSpace < totalBytes + SPACE_MARGIN_BYTES
                    ) {
                        throw IOException(
                            "Not enough free storage to download this update."
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onProgress(0L, totalBytes)
                    }

                    var lastProgressAt = 0L

                    connection.inputStream.use { input ->
                        temporaryFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(32 * 1024)

                            while (true) {
                                currentCoroutineContext().ensureActive()

                                val count = input.read(buffer)
                                if (count == -1) break

                                receivedBytes += count

                                if (
                                    receivedBytes > MAX_APK_BYTES ||
                                    (
                                            totalBytes != null &&
                                                    receivedBytes > totalBytes
                                            )
                                ) {
                                    throw IOException(
                                        "The APK download exceeded its expected size."
                                    )
                                }

                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)

                                val now = android.os.SystemClock.elapsedRealtime()

                                if (now - lastProgressAt >= 200L) {
                                    lastProgressAt = now

                                    withContext(Dispatchers.Main) {
                                        onProgress(
                                            receivedBytes,
                                            totalBytes
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (receivedBytes == 0L) {
                        throw IOException("GitHub returned an empty download.")
                    }

                    if (
                        totalBytes != null &&
                        receivedBytes != totalBytes
                    ) {
                        throw IOException(
                            "The APK download is incomplete. Please retry."
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onProgress(receivedBytes, totalBytes)
                    }
                } finally {
                    connection.disconnect()
                }

                currentCoroutineContext().ensureActive()

                val downloadedHash = digest.digest().toHex()

                if (
                    release.apkSha256 != null &&
                    !downloadedHash.equals(
                        release.apkSha256,
                        ignoreCase = true
                    )
                ) {
                    throw IOException(
                        "The APK checksum does not match GitHub's checksum. " +
                                "Installation has been blocked."
                    )
                }

                val archive = validateApk(
                    context.applicationContext,
                    temporaryFile
                )

                if (finalFile.exists() && !finalFile.delete()) {
                    throw IOException(
                        "Could not replace the previous downloaded APK."
                    )
                }

                if (!temporaryFile.renameTo(finalFile)) {
                    throw IOException(
                        "Could not finish saving the downloaded APK."
                    )
                }

                UpdateDownloadResult.Success(
                    DownloadedUpdate(
                        file = finalFile,
                        versionName =
                            archive.versionName ?: release.versionName,
                        versionCode = packageVersionCode(archive),
                        sha256 = downloadedHash
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                UpdateDownloadResult.Failure(
                    exception.message ?: "The update download failed."
                )
            } catch (exception: Exception) {
                UpdateDownloadResult.Failure(
                    "Could not download or validate the update."
                )
            } finally {
                partialFile?.delete()
            }
        }
    }

    /*
     * Returns an intent; this method does not launch anything.
     * Call it again after returning from installation-permission settings.
     */
    suspend fun prepareInstallation(
        context: Context,
        update: DownloadedUpdate
    ): UpdateInstallResult = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            try {
                val appContext = context.applicationContext
                val expectedFile = File(
                    appContext.cacheDir,
                    "updates/v0idsound-update.apk"
                )

                if (
                    update.file.canonicalFile != expectedFile.canonicalFile ||
                    !update.file.isFile
                ) {
                    throw IOException(
                        "The downloaded APK is no longer available. " +
                                "Download it again."
                    )
                }

                val currentHash = hashFile(update.file)

                if (
                    !currentHash.equals(
                        update.sha256,
                        ignoreCase = true
                    )
                ) {
                    throw IOException(
                        "The downloaded APK changed. Download it again."
                    )
                }

                validateApk(appContext, update.file)

                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !appContext.packageManager.canRequestPackageInstalls()
                ) {
                    return@withLock UpdateInstallResult.PermissionRequired(
                        settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${appContext.packageName}")
                        )
                    )
                }

                val apkUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    update.file
                )

                val installerIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        apkUri,
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                UpdateInstallResult.Ready(installerIntent)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                UpdateInstallResult.Failure(
                    exception.message ?: "Could not prepare installation."
                )
            } catch (exception: Exception) {
                UpdateInstallResult.Failure(
                    "Could not prepare installation. Check that the " +
                            "FileProvider and update_file_paths.xml are configured."
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(
        context: Context,
        file: File
    ): PackageInfo {
        val packageManager = context.packageManager

        val flags = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archive = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            flags
        ) ?: throw IOException(
            "The downloaded file could not be read as an APK."
        )

        if (archive.packageName != context.packageName) {
            throw IOException(
                "This APK belongs to a different app. " +
                        "Installation has been blocked."
            )
        }

        val installed = packageManager.getPackageInfo(
            context.packageName,
            flags
        )

        if (
            packageVersionCode(archive) <=
            packageVersionCode(installed)
        ) {
            throw IOException(
                "This APK's version code is not newer than the installed " +
                        "version. Increase versionCode before publishing it."
            )
        }

        val applicationInfo = archive.applicationInfo
            ?: throw IOException(
                "The APK is missing application information."
            )

        if (applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw IOException(
                "This update requires a newer Android version."
            )
        }

        val archiveSigners = signerFingerprints(archive)
        val installedSigners = signerFingerprints(installed)

        /*
         * Conservative policy: accept only identical current signers.
         * Signing-key rotation is intentionally not supported here.
         */
        if (
            archiveSigners.isEmpty() ||
            installedSigners.isEmpty() ||
            archiveSigners != installedSigners
        ) {
            throw IOException(
                "This APK uses a different signing certificate. " +
                        "It cannot update this installation through this updater. " +
                        "Do not uninstall: your local settings and statistics " +
                        "would be at risk. Android Studio debug builds and " +
                        "release builds usually use different certificates."
            )
        }

        return archive
    }

    @Suppress("DEPRECATION")
    private fun signerFingerprints(
        packageInfo: PackageInfo
    ): Set<String> {
        val signatures = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }

        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .toHex()
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(
        packageInfo: PackageInfo
    ): Long {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

    private suspend fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)

            while (true) {
                currentCoroutineContext().ensureActive()

                val count = input.read(buffer)
                if (count == -1) break

                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().toHex()
    }

    private fun openDownloadConnection(
        downloadUrl: String
    ): HttpURLConnection {
        var url = URL(downloadUrl)

        repeat(6) {
            if (!isAllowedDownloadHost(url)) {
                throw IOException(
                    "The download redirected outside the allowed GitHub hosts."
                )
            }

            val connection =
                url.openConnection() as HttpURLConnection

            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "User-Agent",
                "v0idsound/${BuildConfig.VERSION_NAME}"
            )
            connection.setRequestProperty(
                "Accept-Encoding",
                "identity"
            )

            val status = try {
                connection.responseCode
            } catch (exception: Exception) {
                connection.disconnect()
                throw exception
            }

            if (status == HttpURLConnection.HTTP_OK) {
                return connection
            }

            if (status in setOf(301, 302, 303, 307, 308)) {
                val location =
                    connection.getHeaderField("Location")

                connection.disconnect()

                if (location.isNullOrBlank()) {
                    throw IOException(
                        "GitHub returned an invalid download redirect."
                    )
                }

                url = URL(url, location)
            } else {
                connection.disconnect()

                throw IOException(
                    "APK download failed with HTTP $status."
                )
            }
        }

        throw IOException("The APK download redirected too many times.")
    }

    private fun isRepositoryDownload(value: String): Boolean {
        return try {
            val url = URL(value)

            url.protocol == "https" &&
                    url.host.equals("github.com", ignoreCase = true) &&
                    url.userInfo == null &&
                    (url.port == -1 || url.port == 443) &&
                    url.path.startsWith(
                        "/v01dess/v0idsound/releases/download/"
                    )
        } catch (exception: Exception) {
            false
        }
    }

    private fun isAllowedDownloadHost(url: URL): Boolean {
        return url.protocol == "https" &&
                url.userInfo == null &&
                (url.port == -1 || url.port == 443) &&
                url.host.lowercase() in setOf(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com"
        )
    }

    private fun requestLatestRelease(): JSONObject? {
        val connection = URL(LATEST_RELEASE_API)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.setRequestProperty(
                "X-GitHub-Api-Version",
                "2022-11-28"
            )
            connection.setRequestProperty(
                "User-Agent",
                "v0idsound/${BuildConfig.VERSION_NAME}"
            )

            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }

                    JSONObject(body)
                }

                HttpURLConnection.HTTP_NOT_FOUND -> null

                HttpURLConnection.HTTP_FORBIDDEN,
                429 -> throw IOException(
                    "GitHub refused the request or its request limit " +
                            "was reached. Try again later."
                )

                else -> throw IOException(
                    "GitHub returned HTTP $status. Try again later."
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(
        latest: String,
        installed: String
    ): Int? {
        val latestParts = parseVersion(latest) ?: return null
        val installedParts = parseVersion(installed) ?: return null
        val count = maxOf(latestParts.size, installedParts.size)

        for (index in 0 until count) {
            val comparison = latestParts.getOrElse(index) { 0L }
                .compareTo(
                    installedParts.getOrElse(index) { 0L }
                )

            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun parseVersion(value: String): List<Long>? {
        val normalized = value.trim()
            .removePrefix("v")
            .removePrefix("V")

        if (!normalized.matches(Regex("""\d+(?:\.\d+)*"""))) {
            return null
        }

        return normalized.split(".").map { part ->
            part.toLongOrNull() ?: return null
        }
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"

        return buildString(size * 2) {
            for (byte in this@toHex) {
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }

    companion object {
        const val REPOSITORY_URL =
            "https://github.com/v01dess/v0idsound"

        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/v01dess/v0idsound/releases/latest"

        private const val MAX_APK_BYTES =
            512L * 1024L * 1024L

        private const val SPACE_MARGIN_BYTES =
            16L * 1024L * 1024L

        private val downloadMutex = Mutex()
    }
}