package com.carlospinan.guardavideo.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobStatus
import com.carlospinan.guardavideo.library.watchedFraction
import java.io.File

/** The web logo: rounded accent square with the download arrow. */
@Composable
fun Logo(size: Dp = 44.dp) {
    val brand = LocalBrand.current
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(brand.accentGradient)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        val c = Color.White
        drawLine(c, Offset(w * .5f, w * .22f), Offset(w * .5f, w * .59f), stroke.width, StrokeCap.Round)
        drawLine(c, Offset(w * .5f, w * .59f), Offset(w * .34f, w * .44f), stroke.width, StrokeCap.Round)
        drawLine(c, Offset(w * .5f, w * .59f), Offset(w * .66f, w * .44f), stroke.width, StrokeCap.Round)
        drawLine(c, Offset(w * .28f, w * .75f), Offset(w * .72f, w * .75f), stroke.width, StrokeCap.Round)
    }
}

/** Primary action with the accent gradient, like the web's "Procesar y descargar". */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val brand = LocalBrand.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) brand.accentGradient else Brush.linearGradient(listOf(brand.line, brand.line)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke()
            Text(text, color = if (enabled) Color.White else brand.muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Status pill ("Listo", "Descargando"…) tinted like the web badges. */
@Composable
fun StatusPill(job: Job) {
    val brand = LocalBrand.current
    val (label, color) = when {
        job.status == JobStatus.Running && job.retries > 0 -> stringResource(R.string.status_retrying) to brand.warn
        job.status == JobStatus.Running -> stringResource(R.string.status_running) to brand.accent
        job.status == JobStatus.Queued -> stringResource(R.string.status_queued) to brand.muted
        job.status == JobStatus.Done -> stringResource(R.string.status_done) to brand.ok
        job.status == JobStatus.Error -> stringResource(R.string.status_error) to brand.err
        else -> stringResource(R.string.status_cancelled) to brand.muted
    }
    Text(
        label,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * 16:9 thumbnail: a frame of the downloaded video, its duration, how much was watched, and a ✓
 * when finished; a progress ring while downloading.
 */
@Composable
fun Thumb(job: Job, modifier: Modifier = Modifier, showCheck: Boolean = false, overlay: @Composable BoxScope.() -> Unit = {}) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(brand.card2, brand.accent.copy(alpha = 0.18f)))),
    ) {
        if (job.status == JobStatus.Done && job.filePath != null) {
            // Keyed on size and length too: when a file is repaired or replaced in place (same
            // path), the thumbnail is fetched again instead of keeping the failed/old frame.
            key(job.filePath, job.sizeBytes, job.durationMs) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(job.filePath!!))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .videoFrameMillis(thumbnailFrameMs(job.durationMs))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
        if (job.status.isActive) {
            ProgressRing(job.percent, indeterminate = job.status == JobStatus.Queued || job.percent <= 0f, modifier = Modifier.align(Alignment.Center))
        }
        job.durationMs?.let { duration ->
            Text(
                formatClock(duration),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        val fraction = watchedFraction(job)
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.35f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(brand.accent))
            }
        }
        if (showCheck && job.status == JobStatus.Done) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(brand.ok).padding(3.dp),
            )
        }
        overlay()
    }
}

/** 10% into the video (1–30 s): the first seconds are often a black intro or a logo. */
internal fun thumbnailFrameMs(durationMs: Long?): Long = ((durationMs ?: 20_000L) / 10).coerceIn(1_000L, 30_000L)

@Composable
fun ProgressRing(percent: Float, indeterminate: Boolean, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(52.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.35f), 0f, 360f, false, style = stroke)
            val sweep = if (indeterminate) 90f else 360f * (percent / 100f)
            drawArc(Color.White, -90f, sweep, false, style = stroke)
        }
        if (!indeterminate) {
            Text("${percent.toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Small round star button over thumbnails (shelf cards). */
@Composable
fun StarOverlay(job: Job, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(40.dp)) {
        Icon(
            if (job.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(if (job.starred) R.string.unstar else R.string.star),
            tint = if (job.starred) Color(0xFFF5B301) else Color.White,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp),
        )
    }
}

/** A card in a shelf: thumbnail, two-line title and a detail line. */
@Composable
fun ShelfCard(job: Job, subline: String, onOpen: () -> Unit, onToggleStar: () -> Unit) {
    val brand = LocalBrand.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.play), onClick = onOpen),
    ) {
        Thumb(job, Modifier.fillMaxWidth()) {
            StarOverlay(job, onToggleStar, Modifier.align(Alignment.TopEnd))
        }
        Text(job.displayTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.text)
        if (subline.isNotEmpty()) Text(subline, fontSize = 12.sp, color = brand.muted, maxLines = 1)
    }
}

/** A plain card surface with the web's border and radius. */
@Composable
fun BrandCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable () -> Unit) {
    val brand = LocalBrand.current
    Surface(
        color = brand.card,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = modifier.border(1.dp, borderColor ?: brand.line, RoundedCornerShape(16.dp)),
        content = content,
    )
}

/** Remaining time from the progress rate (like the web's useEta); null until it can tell. */
@Composable
fun rememberEtaSeconds(job: Job): Long? {
    var startAt by remember(job.id) { mutableLongStateOf(0L) }
    var startPct by remember(job.id) { mutableLongStateOf(-1L) }
    val nowMs = System.currentTimeMillis()
    val pct = job.percent.toLong()
    if (job.status != JobStatus.Running || pct <= 0) return null
    if (startPct < 0) {
        startPct = pct
        startAt = nowMs
        return null
    }
    val elapsed = (nowMs - startAt) / 1000.0
    val gained = pct - startPct
    if (elapsed < 3 || gained <= 0) return null
    return ((100 - pct) / (gained / elapsed)).toLong()
}

fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "~$seconds s"
    seconds < 3600 -> "~${seconds / 60} min ${seconds % 60} s"
    else -> "~${seconds / 3600} h ${(seconds % 3600) / 60} min"
}

/** Edge-to-edge content padding for horizontal carousels. */
val CarouselPadding = PaddingValues(horizontal = 16.dp)

