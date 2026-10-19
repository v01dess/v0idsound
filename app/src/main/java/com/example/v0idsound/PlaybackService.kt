package com.example.v0idsound

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var statsRepository: ListeningStatsRepository

    private val trackingScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private val writingScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val pendingWrites = Channel<ListeningWrite>(
        capacity = Channel.UNLIMITED
    )

    private var trackedUri: String? = null
    private var trackedDurationMs = 0L

    private var occurrenceListeningMs = 0L
    private var unsavedListeningMs = 0L

    private var occurrenceCounted = false
    private var unsavedQualifiedPlay = false

    private var wasPlaying = false
    private var lastSampleElapsedMs = 0L
    private var lastListeningAtMs = 0L

    private data class ListeningWrite(
        val trackUri: String,
        val listenedMs: Long,
        val qualifiedPlay: Boolean,
        val playedAtMs: Long
    )

    private val trackingListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            sampleListeningTime()

            wasPlaying = isPlaying

            updateKnownDuration()
            checkQualifiedPlay()

            if (!isPlaying) {
                flushPendingListening()
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            // Finish the previous playback occurrence, including repeats.
            sampleListeningTime()
            flushPendingListening()

            startOccurrence(mediaItem)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateKnownDuration()
            checkQualifiedPlay()

            if (playbackState == Player.STATE_ENDED) {
                sampleListeningTime()
                flushPendingListening()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        statsRepository = ListeningStatsRepository(
            applicationContext
        )

        startStatsWriter()

        val extractorsFactory = DefaultExtractorsFactory()
            .setDisableArtworkMetadata(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(
            this,
            extractorsFactory
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(trackingListener)

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .setBitmapLoader(NoSessionArtworkLoader)
            .build()

        startOccurrence(player.currentMediaItem)

        trackingScope.launch {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)

                updateKnownDuration()
                sampleListeningTime()

                if (unsavedListeningMs >= SAVE_INTERVAL_MS) {
                    flushPendingListening()
                }
            }
        }
    }

    private fun startOccurrence(mediaItem: MediaItem?) {
        trackedUri = trackUriFor(mediaItem)
        trackedDurationMs = 0L

        occurrenceListeningMs = 0L
        unsavedListeningMs = 0L

        occurrenceCounted = false
        unsavedQualifiedPlay = false

        lastListeningAtMs = 0L
        lastSampleElapsedMs = SystemClock.elapsedRealtime()
        wasPlaying = player.isPlaying

        updateKnownDuration()
    }

    private fun trackUriFor(mediaItem: MediaItem?): String? {
        if (mediaItem == null) return null

        return mediaItem.requestMetadata.mediaUri?.toString()
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: mediaItem.mediaId.takeIf {
                it.startsWith("content://") ||
                        it.startsWith("file://")
            }
    }

    private fun updateKnownDuration() {
        if (trackedUri == null) return

        val currentUri = trackUriFor(player.currentMediaItem)

        if (currentUri == trackedUri && player.duration > 0L) {
            trackedDurationMs = player.duration
        }
    }

    private fun sampleListeningTime() {
        val now = SystemClock.elapsedRealtime()
        val elapsedMs = (now - lastSampleElapsedMs)
            .coerceAtLeast(0L)

        lastSampleElapsedMs = now

        if (
            trackedUri == null ||
            !wasPlaying ||
            elapsedMs == 0L
        ) {
            return
        }

        // Uses real elapsed time, not playback position.
        // Seeking forward therefore does not add listening time.
        occurrenceListeningMs += elapsedMs
        unsavedListeningMs += elapsedMs
        lastListeningAtMs = System.currentTimeMillis()

        checkQualifiedPlay()
    }

    private fun checkQualifiedPlay() {
        if (
            trackedUri == null ||
            occurrenceCounted ||
            trackedDurationMs <= MIN_TRACK_DURATION_MS
        ) {
            return
        }

        val qualificationMs = minOf(
            trackedDurationMs / 2L,
            MAX_QUALIFICATION_MS
        )

        if (occurrenceListeningMs >= qualificationMs) {
            occurrenceCounted = true
            unsavedQualifiedPlay = true

            // Save a newly qualified count without waiting for the next batch.
            flushPendingListening()
        }
    }

    private fun flushPendingListening() {
        val uri = trackedUri ?: return

        if (
            unsavedListeningMs == 0L &&
            !unsavedQualifiedPlay
        ) {
            return
        }

        val write = ListeningWrite(
            trackUri = uri,
            listenedMs = unsavedListeningMs,
            qualifiedPlay = unsavedQualifiedPlay,
            playedAtMs = lastListeningAtMs.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        )

        if (pendingWrites.trySend(write).isSuccess) {
            unsavedListeningMs = 0L
            unsavedQualifiedPlay = false
        } else {
            Log.e(
                TAG,
                "Could not queue listening statistics"
            )
        }
    }

    private fun startStatsWriter() {
        val writerJob = writingScope.launch {
            for (write in pendingWrites) {
                var saved = false

                while (!saved) {
                    try {
                        statsRepository.recordListening(
                            trackUri = write.trackUri,
                            listenedMs = write.listenedMs,
                            qualifiedPlay = write.qualifiedPlay,
                            playedAtMs = write.playedAtMs
                        )

                        saved = true
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: IOException) {
                        Log.e(
                            TAG,
                            "Could not save listening statistics; retrying",
                            exception
                        )

                        delay(WRITE_RETRY_DELAY_MS)
                    } catch (exception: Exception) {
                        Log.e(
                            TAG,
                            "Unexpected listening statistics error",
                            exception
                        )

                        // Keep playback running if a stats write fails.
                        saved = true
                    }
                }
            }
        }

        writerJob.invokeOnCompletion {
            writingScope.cancel()
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession = session

    override fun onDestroy() {
        trackingScope.cancel()

        sampleListeningTime()
        flushPendingListening()

        player.removeListener(trackingListener)

        // Let the writer finish already-queued saves without blocking
        // the service's main thread.
        pendingWrites.close()

        session.release()
        player.release()

        super.onDestroy()
    }

    private companion object {
        const val TAG = "ListeningTracker"

        const val SAMPLE_INTERVAL_MS = 1_000L
        const val SAVE_INTERVAL_MS = 15_000L
        const val WRITE_RETRY_DELAY_MS = 5_000L

        const val MIN_TRACK_DURATION_MS = 30_000L
        const val MAX_QUALIFICATION_MS = 240_000L
    }
}

/*
 * Diagnostic loader: deliberately disables service-side artwork decoding.
 * The player UI's Coil artwork loader is independent of this.
 */
@OptIn(UnstableApi::class)
private object NoSessionArtworkLoader : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean {
        return false
    }

    override fun decodeBitmap(
        data: ByteArray
    ): ListenableFuture<Bitmap> {
        return unavailableArtwork()
    }

    override fun loadBitmap(
        uri: Uri
    ): ListenableFuture<Bitmap> {
        return unavailableArtwork()
    }

    private fun unavailableArtwork(): ListenableFuture<Bitmap> {
        return Futures.immediateFailedFuture(
            IOException(
                "Session artwork disabled for memory isolation test"
            )
        )
    }
}