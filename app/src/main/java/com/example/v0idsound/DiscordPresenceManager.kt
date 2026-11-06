package com.example.v0idsound

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import java.util.UUID

class DiscordPresenceManager(private val context: Context) {

    private val appId = 1568169944368029767L

    // Upload this in Developer Portal -> Rich Presence -> Art Assets
    private val fallbackArtworkKey = "v0idsound_cover"

    private var rpcConnection: IDiscordRpcConnection? = null

    private var lastTrackKey: String? = null
    private var lastIsPlaying: Boolean? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = IDiscordRpcService.Stub.asInterface(binder ?: return)

            rpcConnection = try {
                service.connect(appId, "1", rpcCallback)
            } catch (e: Exception) {
                Log.e("v0idsound", "Discord RPC connect failed", e)
                null
            }

            Log.d("v0idsound", "Discord RPC connected: $rpcConnection")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rpcConnection = null
        }
    }

    private val rpcCallback = object : IDiscordRpcCallback.Stub() {
        override fun onFrame(frame: String?) {
            Log.d("v0idsound", "Discord RPC frame: $frame")
        }

        override fun onClose(code: Int, message: String?) {
            Log.w("v0idsound", "Discord RPC closed: $code $message")
            rpcConnection = null
        }
    }

    fun bind() {
        val packages = listOf(
            "com.discord",
            "com.discord.canary",
            "com.discord.ptb",
            "com.discord.debug"
        )

        for (pkg in packages) {
            val intent = Intent("com.discord.socialsdk.rpc.IDiscordRpcService")
                .setPackage(pkg)

            val bound = try {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e("v0idsound", "bindService failed for $pkg", e)
                false
            }

            Log.d("v0idsound", "bindService($pkg) = $bound")
            if (bound) return
        }
    }

    fun update(track: Track?, isPlaying: Boolean) {
        val connection = rpcConnection ?: return

        val trackKey = track?.let { "${it.title}|${it.artist}|${it.albumArtUrl}" }

        if (trackKey == lastTrackKey && isPlaying == lastIsPlaying) return

        lastTrackKey = trackKey
        lastIsPlaying = isPlaying

        try {
            if (track == null) {
                connection.sendFrame(clearPayload())
            } else {
                connection.sendFrame(activityPayload(track))
            }
        } catch (e: Exception) {
            Log.e("v0idsound", "Discord RPC send failed", e)
        }
    }

    private fun activityPayload(track: Track): String {
        val start = System.currentTimeMillis()
        val artwork = track.albumArtUrl ?: fallbackArtworkKey

        return """
        {
          "cmd": "SET_ACTIVITY",
          "args": {
            "pid": ${Process.myPid()},
            "activity": {
              "type": 2,
              "details": ${jsonString(track.title)},
              "state": ${jsonString(track.artist)},
              "timestamps": { "start": $start },
              "assets": {
                "large_image": ${jsonString(artwork)},
                "large_text": ${jsonString(track.album ?: "v0idsound")}
              }
            }
          },
          "nonce": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }

    private fun clearPayload(): String {
        return """
        {
          "cmd": "SET_ACTIVITY",
          "args": {
            "pid": ${Process.myPid()},
            "activity": null
          },
          "nonce": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }

    private fun jsonString(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    fun unbind() {
        try {
            rpcConnection?.disconnect()
        } catch (_: Exception) {
        }

        rpcConnection = null

        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
    }
}