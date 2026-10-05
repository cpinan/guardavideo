package com.carlospinan.guardavideo.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.copyLink
import com.carlospinan.guardavideo.app.ui.formatBytes
import com.carlospinan.guardavideo.app.ui.library.BrandCard
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.library.HistoryEntry
import com.carlospinan.guardavideo.library.HistoryStatus
import com.carlospinan.guardavideo.library.classifyFailure
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every link downloaded (or tried), kept even after the video is deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenVideo: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(if (m is UiMessage.Res) context.getString(m.id) else (m as UiMessage.Text).text)
        }
    }
    Scaffold(
        containerColor = brand.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (state.total > 0) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_clear), color = brand.err) },
                                    onClick = { menuOpen = false; confirmClear = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = brand.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.loaded && state.total == 0) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                        Icon(Icons.Rounded.History, null, tint = brand.accent, modifier = Modifier.size(64.dp))
                        Text(stringResource(R.string.history_empty), color = brand.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                return@LazyColumn
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    placeholder = { Text(stringResource(R.string.history_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = brand.card, focusedContainerColor = brand.card),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            for (day in state.days) {
                item(key = "day-${day.date}") {
                    val label = when (day.date) {
                        viewModel.today -> stringResource(R.string.today)
                        viewModel.today.minusDays(1) -> stringResource(R.string.yesterday)
                        else -> day.date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")))
                    }
                    Text(
                        label.uppercase(Locale.forLanguageTag("es")),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = brand.muted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                    )
                }
                items(day.entries, key = { it.jobId }) { entry ->
                    HistoryRow(entry, onOpenVideo, viewModel::downloadAgain, viewModel::remove)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text(stringResource(R.string.history_clear), color = brand.err) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.keep)) } },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpenVideo: (String) -> Unit,
    onDownloadAgain: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    var menuOpen by rememberSaveable(entry.jobId) { mutableStateOf(false) }
    val (icon, color, label) = when (entry.status) {
        HistoryStatus.Downloaded -> Triple(Icons.Rounded.CheckCircle, brand.ok, stringResource(R.string.history_downloaded))
        HistoryStatus.InProgress -> Triple(Icons.Rounded.HourglassTop, brand.accent, stringResource(R.string.history_in_progress))
        HistoryStatus.Failed -> Triple(Icons.Rounded.ErrorOutline, brand.err, "${stringResource(R.string.history_failed)}: ${classifyFailure(entry.error).title.lowercase()}")
        HistoryStatus.Cancelled -> Triple(Icons.Rounded.RemoveCircleOutline, brand.muted, stringResource(R.string.history_cancelled))
        HistoryStatus.Deleted -> Triple(Icons.Rounded.DeleteOutline, brand.muted, stringResource(R.string.history_deleted))
    }
    BrandCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.text)
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
                val details = listOfNotNull(
                    entry.url.substringAfter("://").substringBefore('/').removePrefix("www."),
                    entry.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(context, it) },
                    java.time.Instant.ofEpochMilli(entry.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                ).joinToString(" · ")
                Text(details, fontSize = 12.sp, color = brand.muted, maxLines = 1)
            }
            // The main action for this entry, then everything else in the menu.
            when (entry.status) {
                HistoryStatus.Downloaded -> IconButton(onClick = { onOpenVideo(entry.jobId) }) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.open_video), tint = brand.accent)
                }
                HistoryStatus.Deleted, HistoryStatus.Failed, HistoryStatus.Cancelled -> IconButton(onClick = { onDownloadAgain(entry) }) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.download_again), tint = brand.accent)
                }
                HistoryStatus.InProgress -> Unit
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = brand.muted) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_link)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menuOpen = false
                            com.carlospinan.guardavideo.app.ui.shareLink(context, entry.displayTitle, entry.url, entry.title)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_link)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menuOpen = false; copyLink(context, entry.displayTitle, entry.url) },
                    )
                    if (entry.status != HistoryStatus.InProgress && entry.status != HistoryStatus.Downloaded) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_again)) },
                            leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { menuOpen = false; onDownloadAgain(entry) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_history)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuOpen = false; onRemove(entry) },
                    )
                }
            }
        }
    }
}
