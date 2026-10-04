package com.carlospinan.guardavideo.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.formatBytes
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.formatWhen
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobStatus
import java.io.File

@Composable
fun JobCard(
    job: Job,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = job.status == JobStatus.Error || job.status == JobStatus.Cancelled
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (job.status == JobStatus.Error) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        when {
            job.status == JobStatus.Done -> DoneContent(job, onPlay, onDelete, onToggleStar)
            job.status.isActive -> ActiveContent(job, onCancel)
            failed -> FailedContent(job, onRetry, onDelete)
        }
    }
}

@Composable
private fun DoneContent(job: Job, onPlay: () -> Unit, onDelete: () -> Unit, onToggleStar: () -> Unit) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val playLabel = stringResource(R.string.play)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(job.filePath?.let(::File))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .videoFrameMillis(2_000)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSizeCompat(),
            )
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(job.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                job.sizeBytes?.let { formatBytes(context, it) },
                formatWhen(job.createdAt),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val progressNote = when {
                job.watched -> stringResource(R.string.watched)
                job.positionMs >= 1_000 -> stringResource(R.string.resume_at, formatClock(job.positionMs))
                else -> null
            }
            if (progressNote != null) {
                Text(progressNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onToggleStar) {
            Icon(
                if (job.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(if (job.starred) R.string.unstar else R.string.star),
                tint = if (job.starred) Color(0xFFF5B301) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { confirming = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.keep)) } },
        )
    }
}

@Composable
private fun ActiveContent(job: Job, onCancel: () -> Unit) {
    val queued = job.status == JobStatus.Queued
    val pct = job.percent.toInt()
    val status = when {
        job.retries > 0 -> stringResource(R.string.reconnecting, job.retries)
        queued -> stringResource(R.string.status_queued)
        else -> stringResource(R.string.percent, pct)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(job.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val progressLabel = "${job.displayTitle}: $status"
        if (queued || job.percent <= 0f) {
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = progressLabel })
        } else {
            LinearProgressIndicator(
                progress = { job.percent / 100f },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressLabel },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.retries > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun FailedContent(job: Job, onRetry: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(job.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            job.error ?: stringResource(if (job.status == JobStatus.Cancelled) R.string.status_cancelled else R.string.status_error),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
