package com.example.v0idsound

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class FolderScanner(
    private val context: Context
) {

    fun scanFolder(
        treeUri: Uri,
        maxDepth: Int
    ): List<Track> {
        val tracks = mutableListOf<Track>()

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return tracks

        scanDirectory(
            directory = root,
            currentDepth = 0,
            maxDepth = maxDepth,
            tracks = tracks
        )

        return tracks
    }

    private fun scanDirectory(
        directory: DocumentFile,
        currentDepth: Int,
        maxDepth: Int,
        tracks: MutableList<Track>
    ) {
        if (currentDepth > maxDepth) return

        for (file in directory.listFiles()) {
            if (file.isDirectory) {
                scanDirectory(
                    directory = file,
                    currentDepth = currentDepth + 1,
                    maxDepth = maxDepth,
                    tracks = tracks
                )
            } else if (isAudioFile(file)) {
                tracks.add(
                    Track(
                        id = file.uri.hashCode().toLong(),
                        title = file.name ?: "Unknown title",
                        artist = "Unknown artist",
                        album = "Unknown album",
                        durationMs = 0L,
                        uri = file.uri
                    )
                )
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val mime = file.type?.lowercase()

        return mime?.startsWith("audio/") == true ||
                name.endsWith(".mp3") ||
                name.endsWith(".flac") ||
                name.endsWith(".ogg") ||
                name.endsWith(".opus") ||
                name.endsWith(".m4a") ||
                name.endsWith(".aac") ||
                name.endsWith(".wav") ||
                name.endsWith(".wma")
    }
}