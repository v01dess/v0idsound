package com.example.v0idsound

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class FolderScanner(
    private val context: Context
) {
    private val artworkDirectory: File
        get() = File(context.cacheDir, "folder_artwork")

    fun scanFolder(
        treeUri: Uri,
        maxDepth: Int
    ): List<Track> {
        val tracks = mutableListOf<Track>()

        val root = DocumentFile.fromTreeUri(
            context,
            treeUri
        ) ?: return tracks

        if (!root.exists() || !root.isDirectory) {
            return tracks
        }

        scanDirectory(
            directory = root,
            currentDepth = 0,
            maxDepth = maxDepth.coerceAtLeast(0),
            tracks = tracks,
            visitedDirectories = mutableSetOf()
        )

        return tracks
    }

    private fun scanDirectory(
        directory: DocumentFile,
        currentDepth: Int,
        maxDepth: Int,
        tracks: MutableList<Track>,
        visitedDirectories: MutableSet<String>
    ) {
        if (currentDepth > maxDepth) return

        if (!visitedDirectories.add(directory.uri.toString())) {
            return
        }

        val children = try {
            directory.listFiles()
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "Could not read directory: ${directory.uri}",
                exception
            )
            return
        }

        for (file in children) {
            try {
                when {
                    file.isDirectory -> {
                        scanDirectory(
                            directory = file,
                            currentDepth = currentDepth + 1,
                            maxDepth = maxDepth,
                            tracks = tracks,
                            visitedDirectories = visitedDirectories
                        )
                    }

                    file.isFile && isAudioFile(file) -> {
                        tracks.add(readTrack(file))
                    }
                }
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "Could not scan file: ${file.uri}",
                    exception
                )
            }
        }
    }

    private fun readTrack(file: DocumentFile): Track {
        val fileName = file.name ?: "Unknown title"

        val fallbackTitle = fileName
            .substringBeforeLast('.', fileName)
            .ifBlank { fileName }

        var title = fallbackTitle
        var artist = "Unknown artist"
        var album = "Unknown album"
        var durationMs = 0L
        var artworkUri: Uri? = null

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, file.uri)

            title = retriever.readTag(
                MediaMetadataRetriever.METADATA_KEY_TITLE
            ) ?: fallbackTitle

            artist = retriever.readTag(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            ) ?: "Unknown artist"

            album = retriever.readTag(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            ) ?: "Unknown album"

            durationMs = retriever.readTag(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            // Artwork failure should not discard readable song metadata.
            artworkUri = try {
                loadOrCacheArtwork(file, retriever)
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "Could not load artwork: ${file.uri}",
                    exception
                )
                null
            }
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "Could not fully read metadata: ${file.uri}",
                exception
            )
        } finally {
            try {
                retriever.release()
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "Could not release metadata retriever",
                    exception
                )
            }
        }

        return Track(
            id = file.uri.toString().hashCode().toLong(),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            uri = file.uri,
            artworkUri = artworkUri
        )
    }

    private fun MediaMetadataRetriever.readTag(
        key: Int
    ): String? {
        return extractMetadata(key)
            ?.trim()
            ?.takeIf { value ->
                value.isNotEmpty() &&
                        !value.equals("<unknown>", ignoreCase = true)
            }
    }

    private fun loadOrCacheArtwork(
        file: DocumentFile,
        retriever: MediaMetadataRetriever
    ): Uri? {
        val directory = artworkDirectory

        if (!directory.exists() && !directory.mkdirs()) {
            return null
        }

        val cacheKey = sha256(
            "${file.uri}|${file.lastModified()}|${file.length()}"
        )

        // .img retains the original image bytes without falsely assuming JPEG.
        val artworkFile = File(directory, "$cacheKey.img")

        if (artworkFile.isFile && artworkFile.length() > 0L) {
            return Uri.fromFile(artworkFile)
        }

        val artworkBytes = retriever.embeddedPicture
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val temporaryFile = File.createTempFile(
            "artwork_",
            ".tmp",
            directory
        )

        try {
            temporaryFile.writeBytes(artworkBytes)

            if (!temporaryFile.renameTo(artworkFile)) {
                // Another scan may already have created the same cache entry.
                if (!artworkFile.isFile || artworkFile.length() == 0L) {
                    return null
                }
            }

            return Uri.fromFile(artworkFile)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type?.lowercase(Locale.ROOT)

        if (
            mime?.startsWith("audio/") == true ||
            mime == "application/ogg"
        ) {
            return true
        }

        val name = file.name?.lowercase(Locale.ROOT)
            ?: return false

        val extension = name.substringAfterLast(
            '.',
            missingDelimiterValue = ""
        )

        return extension in AUDIO_EXTENSIONS
    }

    private companion object {
        const val TAG = "FolderScanner"

        val AUDIO_EXTENSIONS = setOf(
            "mp3",
            "flac",
            "ogg",
            "opus",
            "m4a",
            "aac",
            "wav",
            "wma"
        )
    }
}