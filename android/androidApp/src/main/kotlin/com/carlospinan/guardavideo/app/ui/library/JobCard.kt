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
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.formatBytes
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.formatWhen
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobStatus

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
                if (queued || job.percent <= 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics { contentDescription = label }, color = brand.accent, trackColor = brand.card2)
                } else {
                    LinearProgressIndicator(
                        progress = { job.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics { contentDescription = label },
                        color = if (job.retries > 0) brand.warn else brand.accent,
                        trackColor = brand.card2,
                        drawStopIndicator = {},
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!queued) Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = brand.text, modifier = Modifier.padding(end = 8.dp))
                    Text(note.orEmpty(), fontSize = 13.sp, color = if (job.retries > 0) brand.warn else brand.muted, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            } else {
                job.error?.let { Text(it, fontSize = 13.sp, color = brand.err) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradientButton(stringResource(R.string.retry), onRetry, enabled = true, modifier = Modifier.weight(1f))
                    OutlinedIconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
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
                OutlinedIconButton(onClick = onSaveToGallery, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(R.string.save_to_gallery))
                }
                OutlinedIconButton(onClick = { confirming = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
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

