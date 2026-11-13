package com.pyoungws.bluewolfplayer.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent

/** Cast framework options: the default media receiver (no custom receiver app needed). */
class WolfCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .build()

    override fun getAdditionalSessionProviders(context: Context) = null
}

/**
 * Bridges the local ExoPlayer and a [CastPlayer] for the player screen. While a
 * Cast session is live [activePlayer] is the CastPlayer (bound to the PlayerView
 * so seek/play controls drive the receiver); when the session ends playback
 * returns to the local player at the receiver's last position.
 *
 * Stream URLs normally travel with an Authorization header, which the receiver
 * cannot send, so the token is appended as an `api_key` query parameter on the
 * URL handed to Cast instead.
 */
@UnstableApi
class CastController(context: Context) {
    private val castContext = runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
    private var castPlayer: CastPlayer? = null
    private var localPlayer: Player? = null
    private var castItemBuilder: (() -> MediaItem?)? = null

    var activePlayer by mutableStateOf<Player?>(null)
        private set
    var isCasting by mutableStateOf(false)
        private set
    val available get() = castContext != null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = moveToCast()
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = moveToCast()
        override fun onSessionEnded(session: CastSession, error: Int) = moveToLocal()
        override fun onSessionSuspended(session: CastSession, reason: Int) = moveToLocal()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    /**
     * Watches the Cast session while [local] is the on-device player.
     * [castItem] rebuilds the current media for the receiver on handoff.
     */
    fun attach(local: Player, castItem: () -> MediaItem?) {
        detach()
        localPlayer = local
        castItemBuilder = castItem
        castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        if (castContext?.sessionManager?.currentCastSession?.isConnected == true) moveToCast()
    }

    fun detach() {
        castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        localPlayer = null
        castItemBuilder = null
    }

    private fun moveToCast() {
        val context = castContext ?: return
        val local = localPlayer ?: return
        val item = castItemBuilder?.invoke() ?: return
        val player = castPlayer ?: CastPlayer(context, DefaultMediaItemConverter()).also { castPlayer = it }
        player.setMediaItem(item, local.currentPosition)
        player.playWhenReady = local.playWhenReady
        local.pause()
        activePlayer = player
        isCasting = true
    }

    private fun moveToLocal() {
        val cast = castPlayer ?: return
        val local = localPlayer
        if (local != null) {
            cast.currentPosition.takeIf { it > 0 }?.let(local::seekTo)
            local.playWhenReady = cast.playWhenReady
        }
        cast.stop()
        cast.release()
        castPlayer = null
        activePlayer = null
        isCasting = false
    }

    fun release() {
        detach()
        castPlayer?.release()
        castPlayer = null
    }
}

/** A Cast route picker button for app bars; no-op when Play services are missing. */
fun castRouteButton(context: Context): MediaRouteButton = MediaRouteButton(context).also {
    runCatching { CastButtonFactory.setUpMediaRouteButton(context, it) }
}

/** Rebuilds a local media item for the Cast receiver: same URI + token baked into the query. */
fun MediaItem.forCastReceiver(token: String?): MediaItem {
    val uri = localConfiguration?.uri ?: return this
    val castUri = if (token.isNullOrBlank() || uri.toString().contains("api_key=")) {
        uri
    } else {
        uri.buildUpon().appendQueryParameter("api_key", token).build()
    }
    return buildUpon().setUri(castUri).build()
}
