package com.pyoungws.cobaltwolf.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaStatus
import com.pyoungws.cobaltwolf.shared.seerr.SeerrTitle
import com.pyoungws.cobaltwolf.ui.common.Artwork

/**
 * A clear badge on titles that are already on the Jellyfin server or already requested.
 * Not-requested titles show nothing.
 */
@Composable
fun SeerrStatusBadge(title: SeerrTitle, modifier: Modifier = Modifier) {
    val (label, container) = when {
        title.mediaStatus == SeerrMediaStatus.Available -> R.string.seerr_badge_in_library to MaterialTheme.colorScheme.tertiaryContainer
        title.mediaStatus == SeerrMediaStatus.PartiallyAvailable -> R.string.seerr_badge_partial to MaterialTheme.colorScheme.tertiaryContainer
        title.requestStatus == com.pyoungws.cobaltwolf.shared.seerr.SeerrRequestStatus.Declined -> R.string.seerr_badge_declined to MaterialTheme.colorScheme.errorContainer
        title.isRequested -> R.string.seerr_badge_requested to MaterialTheme.colorScheme.secondaryContainer
        else -> return
    }
    Surface(color = container, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SeerrPosterCard(title: SeerrTitle, onClick: () -> Unit, width: Dp = 120.dp, modifier: Modifier = Modifier) {
    Column(modifier.width(width).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Box {
            Artwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
            SeerrStatusBadge(title, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Text(title.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        title.year?.let {
            Text(it.toString(), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
