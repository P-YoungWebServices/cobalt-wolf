package com.pyoungws.bluewolfplayer.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pyoungws.bluewolfplayer.MainActivity

/**
 * Publishes the app's player as a media session so the system can show a media
 * notification, route headset/lock-screen transport keys and keep audio alive
 * after the player screen closes. While the player activity is open the service
 * wraps the same player instance; when an audio item keeps playing after the
 * activity is destroyed, the session retains it instead of releasing.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ATTACH -> pendingPlayer?.let { attach(it) }
            ACTION_STOP -> {
                session?.release()
                session = null
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun attach(player: Player) {
        if (session?.player === player) return
        session?.release()
        val openApp = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        pendingPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_ATTACH = "com.pyoungws.bluewolfplayer.player.ATTACH"
        private const val ACTION_STOP = "com.pyoungws.bluewolfplayer.player.STOP"
        private var pendingPlayer: Player? = null

        /**
         * Starts the session service around [player]. Call from the foreground player screen:
         * a notification appears and the service holds the player if the screen closes while
         * audio keeps playing.
         */
        fun attach(context: Context, player: Player) {
            pendingPlayer = player
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_ATTACH))
        }

        /** Stops background playback and releases the held player, if any. */
        fun stop(context: Context) {
            pendingPlayer = null
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }
    }
}
