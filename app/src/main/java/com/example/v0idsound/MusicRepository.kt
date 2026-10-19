package com.example.v0idsound

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri? = null,
    val dateAddedMs: Long = 0L
)

class MusicRepository(
    private val context: Context
) {
    private val discoveryPreferences by lazy {
        context.getSharedPreferences(
            "v0idsound_track_discovery",
            Context.MODE_PRIVATE
        )
    }

    private val allowedMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/flac",
        "audio/x-flac",
        "audio/ogg",
        "audio/vorbis",
        "application/ogg",
        "audio/mp4",
        "audio/aac",
        "audio/x-aac",
        "audio/x-m4a",
        "audio/m4a",
        "audio/x-ms-wma",
        "audio/wav",
        "audio/x-wav",
        "audio/vnd.wave",
        "audio/opus",
        "audio/ogg; codecs=opus",
        "audio/x-opus+ogg",
        "audio/x-mod",
        "audio/it",
        "audio/x-it",
        "audio/xm",
        "audio/x-xm",
        "audio/x-s3m",
        "audio/x-adpcm",
        "audio/x-adpcm-dvi-ima",
        "audio/x-adpcm-ms"
    )

    private fun isLikelyAudio(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true

        val normalized = mimeType.lowercase()

        return normalized.startsWith("audio/") ||
                normalized == "application/ogg" ||
                normalized.contains("opus") ||
                normalized.contains("flac") ||
                normalized.contains("adpcm")
    }

    fun loadTracks(settings: IndexerSettings): List<Track> {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (settings.minimumDurationSeconds > 0) {
            selectionParts.add(
                "${MediaStore.Audio.Media.DURATION} >= ?"
            )
            selectionArgs.add(
                (settings.minimumDurationSeconds * 1000L).toString()
            )
        }

        val selection = selectionParts
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" AND ")

        val selectionArguments = selection
            ?.let { selectionArgs.toTypedArray() }
            ?: emptyArray()

        val sortOrder = if (settings.sortByName) {
            "${MediaStore.Audio.Media.TITLE} ASC"
        } else {
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        }

        Log.d(
            "v0idsound",
            "Query selection=$selection " +
                    "args=${selectionArguments.joinToString()} sort=$sortOrder"
        )

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArguments,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )
            val albumCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM
            )
            val albumIdCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM_ID
            )
            val durationCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION
            )
            val isMusicCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_MUSIC
            )
            val mimeCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.MIME_TYPE
            )
            val dateAddedCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_ADDED
            )

            while (cursor.moveToNext()) {
                val isMusic = cursor.getInt(isMusicCol) != 0
                val mimeType = cursor.getString(mimeCol)
                    ?.lowercase()
                    .orEmpty()

                val isAcceptedAudio = if (settings.broadScan) {
                    isLikelyAudio(mimeType)
                } else {
                    mimeType in allowedMimeTypes
                }

                if (settings.onlyMusic && !isMusic) continue

                if (settings.hideRecordings && !isAcceptedAudio) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                val audioUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val artworkUri = if (albumId > 0L) {
                    ContentUris.withAppendedId(
                        Uri.parse(
                            "content://media/external/audio/albumart"
                        ),
                        albumId
                    )
                } else {
                    null
                }

                val dateAddedSeconds = if (
                    cursor.isNull(dateAddedCol)
                ) {
                    0L
                } else {
                    cursor.getLong(dateAddedCol).coerceAtLeast(0L)
                }

                tracks.add(
                    Track(
                        id = id,
                        title = cursor.getString(titleCol)
                            ?: "Unknown title",
                        artist = cursor.getString(artistCol)
                            ?: "Unknown artist",
                        album = cursor.getString(albumCol)
                            ?: "Unknown album",
                        durationMs = cursor.getLong(durationCol),
                        uri = audioUri,
                        artworkUri = artworkUri,
                        dateAddedMs = dateAddedSeconds * 1000L
                    )
                )
            }
        }

        Log.d(
            "v0idsound",
            "MediaStore returned ${tracks.size} tracks"
        )

        return tracks
    }

    fun loadTracksWithFolders(
        settings: IndexerSettings,
        folderScanner: FolderScanner
    ): List<Track> {
        val merged = mutableListOf<Track>()
        val seenUris = mutableSetOf<String>()

        if (settings.useMediaStore) {
            loadTracks(settings).forEach { track ->
                if (seenUris.add(track.uri.toString())) {
                    merged.add(track)
                }
            }
        }

        val discoveryTime = System.currentTimeMillis()
        val discoveryEditor = discoveryPreferences.edit()
        var hasNewDiscoveryDates = false

        settings.scanFolders.forEach { folderUri ->
            val folderTracks = runCatching {
                folderScanner.scanFolder(
                    treeUri = Uri.parse(folderUri),
                    maxDepth = settings.maxFolderDepth
                )
            }.onFailure { exception ->
                Log.w(
                    "v0idsound",
                    "Could not scan folder: $folderUri",
                    exception
                )
            }.getOrDefault(emptyList())

            folderTracks.forEach { track ->
                val uriString = track.uri.toString()

                if (seenUris.add(uriString)) {
                    val firstSeen = discoveryPreferences.getLong(
                        uriString,
                        0L
                    )

                    val addedAt = if (firstSeen > 0L) {
                        firstSeen
                    } else {
                        discoveryEditor.putLong(
                            uriString,
                            discoveryTime
                        )
                        hasNewDiscoveryDates = true
                        discoveryTime
                    }

                    merged.add(
                        track.copy(
                            dateAddedMs = if (track.dateAddedMs > 0L) {
                                track.dateAddedMs
                            } else {
                                addedAt
                            }
                        )
                    )
                }
            }
        }

        if (hasNewDiscoveryDates) {
            discoveryEditor.apply()
        }

        Log.d(
            "v0idsound",
            "Merged library returned ${merged.size} tracks"
        )

        return if (settings.sortByName) {
            merged.sortedWith(
                compareBy<Track, String>(
                    String.CASE_INSENSITIVE_ORDER
                ) { it.title }
                    .thenBy { it.uri.toString() }
            )
        } else {
            merged.sortedWith(
                compareByDescending<Track> { it.dateAddedMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        }
    }
}