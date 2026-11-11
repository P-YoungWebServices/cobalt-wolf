package com.pyoungws.bluewolfplayer.ui.tv.channels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.pyoungws.bluewolfplayer.MainActivity
import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.supportsHomeScreenChannels
import com.pyoungws.bluewolfplayer.ui.common.cardSubtitle
import com.pyoungws.bluewolfplayer.ui.common.cardTitle

/** Deep link the launcher fires when a channel program is selected; handled by [MainActivity]. */
fun itemDeepLink(itemId: String): Uri = Uri.parse("bluewolf://item/$itemId")

private const val PROVIDER_PREFIX = "bw_"
private const val CONTINUE_CHANNEL_ID = "${PROVIDER_PREFIX}continue_watching"
private const val RECENT_CHANNEL_ID = "${PROVIDER_PREFIX}recently_added"
private const val MAX_PROGRAMS = 10

data class ChannelProgram(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val landscape: Boolean,
    val kind: MediaKind,
)

data class ChannelPlan(
    val internalId: String,
    val displayName: String,
    val isDefault: Boolean,
    val programs: List<ChannelProgram>,
)

private fun MediaItem.toProgram(landscape: Boolean) =
    ChannelProgram(
        itemId = id,
        title = cardTitle(),
        subtitle = cardSubtitle(),
        imageUrl = if (landscape) landscapeUrl ?: posterUrl else posterUrl ?: landscapeUrl,
        landscape = landscape,
        kind = kind,
    )

/**
 * Maps the home feed to the two published channels: Continue Watching (default channel, shown
 * without user opt-in where the launcher allows it) and Recently Added.
 */
fun planChannels(feed: HomeFeed, continueName: String, recentName: String): List<ChannelPlan> = listOf(
    ChannelPlan(
        internalId = CONTINUE_CHANNEL_ID,
        displayName = continueName,
        isDefault = true,
        programs = feed.continueWatching.take(MAX_PROGRAMS).map { it.toProgram(landscape = true) },
    ),
    ChannelPlan(
        internalId = RECENT_CHANNEL_ID,
        displayName = recentName,
        isDefault = false,
        programs = feed.recentlyAdded.flatMap { it.items }.take(MAX_PROGRAMS).map { it.toProgram(landscape = false) },
    ),
)

/**
 * Publishes Continue Watching and Recently Added to the Android TV home screen through the TV
 * provider. Only called when [android.content.Context.supportsHomeScreenChannels] is true; devices
 * without the TV provider (e.g. Fire TV) are skipped by the caller and by [sync] itself.
 */
class TvHomeChannels(private val context: Context) {
    private val helper = PreviewChannelHelper(context)

    /** Re-publishes both channels from [feed]. Runs provider I/O; call off the main thread. */
    fun sync(feed: HomeFeed, continueName: String, recentName: String) {
        if (!context.supportsHomeScreenChannels()) return
        val plans = planChannels(feed, continueName, recentName)
        val ours = helper.allChannels.filter { it.internalProviderId?.startsWith(PROVIDER_PREFIX) == true }
        plans.forEach { plan ->
            val channelId = ours.firstOrNull { it.internalProviderId == plan.internalId }?.id
                ?: if (plan.programs.isEmpty()) null else publish(plan)
            if (channelId != null) replacePrograms(channelId, plan.programs)
        }
        // Drop our leftover channels (e.g. the feed lost an entire section).
        ours.filter { existing -> plans.none { it.internalId == existing.internalProviderId } }
            .forEach { context.contentResolver.delete(TvContractCompat.buildChannelUri(it.id), null, null) }
        Log.i(TAG, "Home screen channels updated: ${plans.joinToString { "${it.displayName} (${it.programs.size})" }}")
    }

    /** Removes all channels this app published. Runs provider I/O; call off the main thread. */
    fun clear() {
        helper.allChannels
            .filter { it.internalProviderId?.startsWith(PROVIDER_PREFIX) == true }
            .forEach { context.contentResolver.delete(TvContractCompat.buildChannelUri(it.id), null, null) }
    }

    private fun publish(plan: ChannelPlan): Long {
        val appLink = Uri.parse(
            Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME),
        )
        val channel = PreviewChannel.Builder()
            .setDisplayName(plan.displayName)
            .setAppLinkIntentUri(appLink)
            .setInternalProviderId(plan.internalId)
            .build()
        return if (plan.isDefault) helper.publishDefaultChannel(channel) else helper.publishChannel(channel)
    }

    /** Replaces a channel's programs: simplest way to keep order and content in sync. */
    private fun replacePrograms(channelId: Long, programs: List<ChannelProgram>) {
        context.contentResolver.delete(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID}=?",
            arrayOf(channelId.toString()),
        )
        programs.forEach { helper.publishPreviewProgram(it.toPreviewProgram(channelId)) }
    }

    private fun ChannelProgram.toPreviewProgram(channelId: Long): PreviewProgram {
        val intent = Intent(Intent.ACTION_VIEW, itemDeepLink(itemId)).setClass(context, MainActivity::class.java)
        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(title)
            .apply { if (subtitle != null) setDescription(subtitle) }
            .apply { if (imageUrl != null) setPosterArtUri(Uri.parse(imageUrl)) }
            .setPosterArtAspectRatio(
                if (landscape) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                else TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3,
            )
            .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(itemId)
            .setType(
                when (kind) {
                    MediaKind.Movie -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                    MediaKind.Episode -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                    MediaKind.Series -> TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
                    else -> TvContractCompat.PreviewPrograms.TYPE_CLIP
                },
            )
            .build()
    }

    private companion object {
        const val TAG = "CobaltWolfTv"
    }
}
