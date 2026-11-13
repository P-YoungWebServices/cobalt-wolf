package com.pyoungws.cobaltwolf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.model.BadgeTone
import com.pyoungws.cobaltwolf.shared.model.DetailInfoLabel
import com.pyoungws.cobaltwolf.shared.model.InfoBadge
import com.pyoungws.cobaltwolf.shared.model.SeriesStatus
import com.pyoungws.cobaltwolf.shared.model.formatRuntime
import kotlinx.datetime.LocalDate

private val ChipBackground = Color(0x33FFFFFF)
private val PositiveBackground = Color(0xFF2E7D32)
private val NegativeBackground = Color(0xFFC62828)
private val BadgeContent = Color(0xFFFFFFFF)
private val PlainColor = Color(0xFFE6E6E6)
private val Accent = Color(0xFFFFC107)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
fun LocalDate.shortLabel(): String = "$dayOfMonth ${MONTHS[monthNumber - 1]} $year"

@Composable
private fun BadgeBox(background: Color, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(3.dp)).background(background).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun IconBadge(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, color = PlainColor, fontSize = 14.sp, fontWeight = FontWeight.W500)
    }
}

/**
 * The detail-page badge row: ratings, air date/runtime, series status, parental rating and media
 * chips — the same information contract as the official clients.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun InfoBadgeRow(badges: List<InfoBadge>) {
    // FlowRow so long media-chip sets wrap to a second line instead of clipping off-screen;
    // height cap + clip guard against stray oversized children inflating the row.
    androidx.compose.foundation.layout.FlowRow(
        Modifier.heightIn(max = 84.dp).clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            when (badge) {
                is InfoBadge.StarRating -> IconBadge(Icons.Filled.Star, "%.1f".format(badge.value), Accent)
                is InfoBadge.CriticRating -> IconBadge(Icons.Outlined.ThumbUp, "${badge.percent}%", Accent)
                is InfoBadge.Text -> Text(badge.text, color = PlainColor, fontSize = 14.sp, fontWeight = FontWeight.W500)
                is InfoBadge.Runtime -> IconBadge(Icons.Outlined.Schedule, formatRuntime(badge.minutes), PlainColor)
                is InfoBadge.AirDate -> Text(badge.date.shortLabel(), color = PlainColor, fontSize = 14.sp, fontWeight = FontWeight.W500)
                is InfoBadge.Chip -> BadgeBox(ChipBackground) {
                    Text(badge.text, color = BadgeContent, fontSize = 10.sp, fontWeight = FontWeight.W600)
                }
                is InfoBadge.Status -> BadgeBox(
                    when (badge.tone) {
                        BadgeTone.Positive -> PositiveBackground
                        BadgeTone.Negative -> NegativeBackground
                        BadgeTone.Neutral -> ChipBackground
                    },
                ) {
                    Text(
                        stringResource(
                            when (badge.status) {
                                SeriesStatus.Continuing -> R.string.detail_status_continuing
                                SeriesStatus.Ended -> R.string.detail_status_ended
                                SeriesStatus.Unreleased -> R.string.detail_status_unreleased
                            },
                        ),
                        color = BadgeContent, fontSize = 10.sp, fontWeight = FontWeight.W600,
                    )
                }
            }
        }
    }
}

/** String resource for a [DetailInfoLabel]. */
fun DetailInfoLabel.labelRes(): Int = when (this) {
    DetailInfoLabel.DirectedBy -> R.string.detail_directed_by
    DetailInfoLabel.Seasons -> R.string.detail_seasons_count
    DetailInfoLabel.Runs -> R.string.detail_runs
    DetailInfoLabel.Ends -> R.string.detail_ends
}
