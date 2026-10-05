package com.carlospinan.guardavideo.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.formatBytes
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.formatWhen
import com.carlospinan.guardavideo.app.ui.shareOriginalLink
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobStatus
import com.carlospinan.guardavideo.library.FailureKind
import com.carlospinan.guardavideo.library.classifyFailure
import com.carlospinan.guardavideo.app.ui.openInBrowser
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SdCardAlert
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily

/** Host of the link, for the detail line ("dailymotion.com · 105 MB · hace 2 horas"). */
private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")

/**
 * A download in progress, failed or cancelled: full width, thumbnail on the left, progress or the
 * error below, like the web's job card on a phone.
 */
@Composable
fun PendingCard(job: Job, onCancel: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val failed = job.status == JobStatus.Error
    BrandCard(modifier.fillMaxWidth(), borderColor = if (failed) brand.err.copy(alpha = 0.55f) else null) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(job, Modifier.width(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(job.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.text)
                    StatusPill(job)
                    Text(listOf(hostOf(job.url), formatWhen(job.createdAt)).joinToString(" · "), fontSize = 12.sp, color = brand.muted, maxLines = 1)
                }
            }
            if (job.status.isActive) {
                val pct = job.percent.toInt()
                val queued = job.status == JobStatus.Queued
                val eta = rememberEtaSeconds(job)
                val note = when {
                    job.retries > 0 -> stringResource(R.string.reconnecting, job.retries)
                    queued -> stringResource(R.string.waiting_turn)
                    eta != null -> stringResource(R.string.eta, formatEta(eta))
                    else -> null
                }
                val label = "${job.displayTitle}: $pct %"
                ShimmerProgressBar(
                    percent = job.percent,
                    indeterminate = queued || job.percent <= 0f,
                    retrying = job.retries > 0,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(job.percent / 100f, 0f..1f)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!queued) Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = brand.text, modifier = Modifier.padding(end = 8.dp))
                    Text(note.orEmpty(), fontSize = 13.sp, color = if (job.retries > 0) brand.warn else brand.muted, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            } else {
                FailureDetails(job, onRetry, onDelete)
            }
        }
    }
}

/** Why it failed (icon, cause, what to do) and the actions that make sense for that cause. */
@Composable
private fun FailureDetails(job: Job, onRetry: () -> Unit, onDelete: () -> Unit) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    val cancelled = job.status == JobStatus.Cancelled
    val kind = classifyFailure(job.error)
    var details by rememberSaveable { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (cancelled) brand.card2 else brand.err.copy(alpha = 0.10f))
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (cancelled) Icons.Rounded.Block else iconFor(kind), contentDescription = null, tint = if (cancelled) brand.muted else brand.err)
            Text(
                if (cancelled) stringResource(R.string.status_cancelled) else kind.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (cancelled) brand.text else brand.err,
            )
        }
        if (!cancelled) {
            if (kind.explanation.isNotEmpty()) Text(kind.explanation, fontSize = 13.sp, color = brand.text)
            Text(kind.hint, fontSize = 13.sp, color = brand.muted)
            if (!job.error.isNullOrBlank()) {
                Text(
                    stringResource(if (details) R.string.hide_details else R.string.show_details),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = brand.accent,
                    modifier = Modifier.clickable(role = Role.Button) { details = !details }.padding(vertical = 4.dp),
                )
                if (details) {
                    Text(job.error!!, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = brand.muted)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cancelled || kind.retryable) {
            GradientButton(stringResource(R.string.retry), onRetry, enabled = true, modifier = Modifier.weight(1f))
        } else {
            // Retrying would only fail again: let the user check the site instead.
            OutlinedButton(onClick = { openInBrowser(context, job.url) }, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.open_in_browser), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (kind.retryable && !cancelled) {
            OutlinedIconButton(onClick = { openInBrowser(context, job.url) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = stringResource(R.string.open_in_browser))
            }
        }
        OutlinedIconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

private fun iconFor(kind: FailureKind) = when (kind) {
    FailureKind.Network -> Icons.Rounded.WifiOff
    FailureKind.Private, FailureKind.LoginRequired, FailureKind.Drm -> Icons.Rounded.Lock
    FailureKind.GeoBlocked -> Icons.Rounded.Public
    FailureKind.Removed, FailureKind.NoVideo -> Icons.Rounded.LinkOff
    FailureKind.Forbidden -> Icons.Rounded.Block
    FailureKind.RateLimited -> Icons.Rounded.HourglassTop
    FailureKind.NoSpace -> Icons.Rounded.SdCardAlert
    FailureKind.Processing, FailureKind.Unknown -> Icons.Rounded.ErrorOutline
}

/** A finished video: big thumbnail on top, title, details, and Play / save / delete (rail card). */
@Composable
fun FinishedCard(
    job: Job,
    onPlay: () -> Unit,
    onToggleStar: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    BrandCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Thumb(job, Modifier.fillMaxWidth(), showCheck = true)
            Text(job.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f))
                IconButton(onClick = onToggleStar) {
                    Icon(
                        if (job.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(if (job.starred) R.string.unstar else R.string.star),
                        tint = if (job.starred) Color(0xFFF5B301) else brand.muted,
                    )
                }
                StatusPill(job)
            }
            val details = listOfNotNull(
                hostOf(job.url),
                job.sizeBytes?.let { formatBytes(context, it) },
                when (job.viewCount) {
                    0 -> null
                    1 -> stringResource(R.string.views_one)
                    else -> stringResource(R.string.views_many, job.viewCount)
                },
                formatWhen(job.createdAt),
            ).joinToString(" · ")
            Text(details, fontSize = 12.sp, color = brand.muted, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                val resume = job.positionMs >= 1_000 && !job.watched
                GradientButton(
                    text = if (resume) stringResource(R.string.continue_at, formatClock(job.positionMs)) else stringResource(R.string.play),
                    onClick = onPlay,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                )
                OutlinedIconButton(onClick = { shareOriginalLink(context, job) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_link))
                }
                // Less frequent actions in a menu, so the card keeps a roomy Play button.
                Box {
                    OutlinedIconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_to_gallery)) },
                            leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                            onClick = { menuOpen = false; onSaveToGallery() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_link)) },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                clipboard.setPrimaryClip(ClipData.newPlainText(job.displayTitle, job.url))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = brand.err) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = brand.err) },
                            onClick = { menuOpen = false; confirming = true },
                        )
                    }
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete() }) { Text(stringResource(R.string.delete), color = brand.err) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.keep)) } },
        )
    }
}

