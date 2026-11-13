package com.pyoungws.bluewolfplayer.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.pyoungws.bluewolfplayer.MainActivity
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.library.ItemQuery
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Publishes the app's player as a media session so the system can show a media
 * notification, route headset/lock-screen transport keys and keep audio alive
 * after the player screen closes — and doubles as the Android Auto media
 * library: cars browse the music libraries and play songs straight from the
 * service. While the player activity is open the service wraps the same player
 * instance; when an audio item keeps playing after the activity is destroyed,
 * the session retains it instead of releasing.
 */
@UnstableApi
/** Stable media ids for the browsable tree: "root" → "lib:<libraryId>" → "item:<itemId>". */
internal object MediaLibraryIds {
    const val ROOT = "root"
    fun library(libraryId: String) = "lib:$libraryId"
    fun item(itemId: String) = "item:$itemId"
    fun isRoot(id: String) = id == ROOT
    fun libraryId(id: String) = id.removePrefix("lib:")
    fun itemId(id: String) = id.removePrefix("item:")
    fun isLibraryNode(id: String) = id.startsWith("lib:")
}

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    /** The service's own player, created lazily when Android Auto plays without the activity open. */
    private var ownedPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val repositories get() = runCatching {
        GlobalContext.get().let { it.get<LibraryRepository>() to it.get<PlaybackRepository>() }
    }.getOrNull()

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
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    private fun sessionWithPlayer(): MediaLibrarySession {
        session?.let { return it }
        attach(ownedPlayer())
        return requireNotNull(session)
    }

    /** The service-owned player; rebuilt when stream headers change (token refresh between plays). */
    private var ownedHeaders: Map<String, String> = emptyMap()
    private fun ownedPlayer(headers: Map<String, String> = emptyMap()): ExoPlayer {
        val existing = ownedPlayer
        if (existing != null && (headers.isEmpty() || headers == ownedHeaders)) return existing
        existing?.release()
        if (headers.isNotEmpty()) ownedHeaders = headers
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory().setDefaultRequestProperties(ownedHeaders)
        val factory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(this, http),
        )
        return ExoPlayer.Builder(this).setMediaSourceFactory(factory).build().also { ownedPlayer = it }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = sessionWithPlayer()

    override fun onDestroy() {
        session?.release()
        session = null
        ownedPlayer?.release()
        ownedPlayer = null
        pendingPlayer = null
        super.onDestroy()
    }

    // --- Media library (Android Auto browsing) -------------------------------------------------

    private fun <T : Any> future(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val result = SettableFuture.create<LibraryResult<T>>()
        serviceScope.launch {
            result.set(runCatching { block() }.getOrElse {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
            })
        }
        return result
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        private fun browsable(id: String, title: String) = PlayerMediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

        private fun playable(item: MediaItem) = PlayerMediaItem.Builder()
            .setMediaId(MediaLibraryIds.item(item.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.name)
                    .setArtist(item.artists.firstOrNull())
                    .setAlbumTitle(item.albumName)
                    .setArtworkUri(item.posterUrl?.let(android.net.Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<PlayerMediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsable(MediaLibraryIds.ROOT, getString(com.pyoungws.bluewolfplayer.R.string.app_name)), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<PlayerMediaItem>>> = future {
            val (library, _) = repositories
                ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_SESSION_SETUP_REQUIRED)
            when {
                MediaLibraryIds.isRoot(parentId) -> {
                    val feed = (library.loadHome() as? Outcome.Success)?.value
                        ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    val children = feed.libraries.filter { it.type == LibraryType.Music }
                        .map { browsable(MediaLibraryIds.library(it.id), it.name) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                }
                MediaLibraryIds.isLibraryNode(parentId) -> {
                    val libraryId = MediaLibraryIds.libraryId(parentId)
                    val page = library.items(
                        ItemQuery(libraryId = libraryId, includeItemKinds = setOf(MediaKind.Song), limit = pageSize.takeIf { it > 0 } ?: 100),
                    )
                    when (page) {
                        is Outcome.Success -> LibraryResult.ofItemList(ImmutableList.copyOf(page.value.items.map(::playable)), params)
                        is Outcome.Failure -> LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
                    }
                }
                else -> LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<PlayerMediaItem>> = future {
            val (library, _) = repositories
                ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_SESSION_SETUP_REQUIRED)
            when (val result = library.item(MediaLibraryIds.itemId(mediaId))) {
                is Outcome.Success -> LibraryResult.ofItem(playable(result.value), null)
                is Outcome.Failure -> LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        /** Resolves leaf media ids to stream URLs with the auth headers the source needs. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<PlayerMediaItem>,
        ): ListenableFuture<MutableList<PlayerMediaItem>> {
            val result = SettableFuture.create<MutableList<PlayerMediaItem>>()
            serviceScope.launch {
                val playback = repositories?.second
                result.set(if (playback == null) mediaItems else resolveMediaItems(mediaItems, playback))
            }
            return result
        }

        private suspend fun resolveMediaItems(
            mediaItems: MutableList<PlayerMediaItem>,
            playback: PlaybackRepository,
        ): MutableList<PlayerMediaItem> = mediaItems.map { requested ->
            val itemId = MediaLibraryIds.itemId(requested.mediaId)
            when (val resolved = playback.prepare(PlaybackTarget.Item(itemId))) {
                is Outcome.Success -> {
                    val source = resolved.value.source
                    ownedPlayer(source.headers)
                    requested.buildUpon().setUri(source.url).build()
                }
                is Outcome.Failure -> requested
            }
        }.toMutableList()
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
