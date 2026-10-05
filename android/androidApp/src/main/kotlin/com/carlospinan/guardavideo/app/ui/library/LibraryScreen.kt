package com.carlospinan.guardavideo.app.ui.library

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.settings.ThemeMode
import com.carlospinan.guardavideo.app.ui.formatBytes
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.formatWhen
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.LibraryFilter
import com.carlospinan.guardavideo.library.LibrarySort
import com.carlospinan.guardavideo.source.LinkSource
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QUALITIES = listOf<Int?>(null, 1080, 720, 480)

@Composable
fun LibraryScreen(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    onPlay: (String) -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.onUrlChange(sharedUrl)
            onSharedUrlConsumed()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is UiMessage.Res -> context.getString(message.id)
                    is UiMessage.Text -> message.text
                },
            )
        }
    }

    // Asked once, at the first download: the progress notification needs it on Android 13+.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val submit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.submit()
    }

    Scaffold(containerColor = brand.bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                // Soft accent glow at the top, like the web page background.
                .background(Brush.verticalGradient(0f to brand.bgGlow, 0.35f to brand.bg)),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") { Header(themeMode, onToggleTheme, onSearch = viewModel::openSearch) }
                if (viewModel.searchOpen) {
                    item(key = "search") { SearchBar(state.controls.query, viewModel::onQuery, viewModel::closeSearch) }
                }
                // While searching, results come right under the search bar: the form steps aside.
                if (!viewModel.searchOpen) item(key = "form") {
                    DownloadForm(
                        url = viewModel.url,
                        onUrlChange = viewModel::onUrlChange,
                        quality = viewModel.quality,
                        onQualityChange = viewModel::onQualityChange,
                        source = viewModel.source,
                        canSubmit = viewModel.canSubmit,
                        linkMessage = viewModel.linkMessage,
                        submitting = viewModel.submitting,
                        onSubmit = submit,
                    )
                }
                if (state.videos > 0 && !viewModel.searchOpen) {
                    item(key = "summary") {
                        Text(
                            stringResource(R.string.summary, state.videos, formatBytes(context, state.bytes), formatBytes(context, state.freeBytes)),
                            fontSize = 13.sp,
                            color = brand.muted,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                if (state.loaded && state.total == 0) {
                    item(key = "empty") { EmptyState(onExample = viewModel::useExample) }
                } else if (state.total > 0) {
                    libraryContent(state, viewModel, onPlay)
                }
            }
        }
    }
}

private fun LazyListScope.libraryContent(state: LibraryUiState, vm: LibraryViewModel, onPlay: (String) -> Unit) {
    if (state.searching) {
        // Search results only: a plain list, newest first (or the chosen sort), filters still apply.
        item(key = "results") {
            Text(
                pluralStringResource(R.plurals.search_results, state.flat.size, state.flat.size, state.controls.query),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalBrand.current.muted,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        item(key = "toolbar") { Toolbar(state, vm) }
        searchResults(state.flat, vm, onPlay)
        return
    }
    item(key = "heading") {
        val brand = LocalBrand.current
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Text(
                stringResource(R.string.downloads),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = brand.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (state.active > 0) stringResource(R.string.counts_active_done, state.active, state.done) else stringResource(R.string.counts_done, state.done),
                fontSize = 13.sp,
                color = brand.muted,
                modifier = Modifier.padding(start = 10.dp, bottom = 3.dp),
            )
        }
    }
    val shelves = state.shelves
    shelf("continue", "▶", R.string.shelf_continue, shelves.continueWatching, onPlay, vm) { job ->
        val total = job.durationMs?.let { " / ${formatClock(it)}" }.orEmpty()
        stringResource(R.string.resume_at, formatClock(job.positionMs)) + total
    }
    shelf("favorites", "★", R.string.shelf_favorites, shelves.favorites, onPlay, vm) { job -> job.durationMs?.let { formatClock(it) }.orEmpty() }
    shelf("views", "🔥", R.string.shelf_views, shelves.mostViewed, onPlay, vm) { job ->
        if (job.viewCount == 1) stringResource(R.string.views_one) else stringResource(R.string.views_many, job.viewCount)
    }
    shelf("recent", "🕒", R.string.shelf_recent, shelves.recent, onPlay, vm) { job -> formatWhen(job.createdAt) }

    item(key = "toolbar") { Toolbar(state, vm) }

    if (state.noMatches) {
        item(key = "nomatch") {
            Text(stringResource(R.string.no_matches), color = LocalBrand.current.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }

    for (group in state.groups) {
        val key = group.day.toString()
        item(key = "day-$key") { DayTitle(group.day) }
        items(group.pending, key = { it.id }) { job ->
            PendingCard(
                job,
                onCancel = { vm.cancel(job.id) },
                onRetry = { vm.retry(job.id) },
                onDelete = { vm.delete(job.id) },
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
        if (group.finished.isNotEmpty()) {
            item(key = "rail-$key") {
                // Finished videos of the day: a swipeable carousel; the next card peeks in.
                val width = (LocalConfiguration.current.screenWidthDp * 0.8f).coerceAtMost(340f).dp
                LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(group.finished, key = { it.id }) { job ->
                        FinishedCard(
                            job,
                            onPlay = { onPlay(job.id) },
                            onToggleStar = { vm.toggleStar(job) },
                            onSaveToGallery = { vm.saveToGallery(job) },
                            onDelete = { vm.delete(job.id) },
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
        }
    }
    searchResults(state.flat, vm, onPlay)
}

private fun LazyListScope.searchResults(jobs: List<Job>, vm: LibraryViewModel, onPlay: (String) -> Unit) {
    items(jobs, key = { it.id }) { job ->
        val modifier = Modifier.padding(horizontal = 16.dp).animateItem()
        if (job.status == com.carlospinan.guardavideo.library.JobStatus.Done) {
            FinishedCard(job, { onPlay(job.id) }, { vm.toggleStar(job) }, { vm.saveToGallery(job) }, { vm.delete(job.id) }, modifier.fillMaxWidth())
        } else {
            PendingCard(job, { vm.cancel(job.id) }, { vm.retry(job.id) }, { vm.delete(job.id) }, modifier)
        }
    }
}

private fun LazyListScope.shelf(
    key: String,
    icon: String,
    title: Int,
    jobs: List<Job>,
    onPlay: (String) -> Unit,
    vm: LibraryViewModel,
    subline: @Composable (Job) -> String,
) {
    if (jobs.isEmpty()) return
    item(key = "shelf-$key") {
        val brand = LocalBrand.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$icon  ${stringResource(title)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = brand.text,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(jobs, key = { "$key-${it.id}" }) { job ->
                    ShelfCard(job, subline(job), onOpen = { onPlay(job.id) }, onToggleStar = { vm.toggleStar(job) })
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val brand = LocalBrand.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // keyboard up as soon as search opens
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_search)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = brand.card, focusedContainerColor = brand.card),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun Header(themeMode: ThemeMode, onToggleTheme: () -> Unit, onSearch: () -> Unit) {
    val brand = LocalBrand.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
        Logo()
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = brand.text, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.tagline), fontSize = 14.sp, color = brand.muted)
        }
        val (icon, label) = when (themeMode) {
            ThemeMode.System -> Icons.Rounded.BrightnessAuto to R.string.theme_system
            ThemeMode.Light -> Icons.Rounded.LightMode to R.string.theme_light
            ThemeMode.Dark -> Icons.Rounded.DarkMode to R.string.theme_dark
        }
        BrandCard {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.open_search), tint = brand.text) }
        }
        BrandCard {
            IconButton(onClick = onToggleTheme) { Icon(icon, contentDescription = stringResource(label), tint = brand.text) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadForm(
    url: String,
    onUrlChange: (String) -> Unit,
    quality: Int?,
    onQualityChange: (Int?) -> Unit,
    source: LinkSource,
    canSubmit: Boolean,
    linkMessage: String,
    submitting: Boolean,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    BrandCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.url_label)) },
                placeholder = { Text(stringResource(R.string.url_placeholder), maxLines = 1) },
                singleLine = true,
                isError = url.isNotBlank() && !canSubmit,
                trailingIcon = {
                    IconButton(onClick = { pasteText(context)?.let(onUrlChange) }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.paste))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = brand.card2, focusedContainerColor = brand.card),
                modifier = Modifier.fillMaxWidth(),
            )
            if (url.isNotBlank()) LinkChip(source, canSubmit, linkMessage)
            QualityPicker(quality, onQualityChange, Modifier.fillMaxWidth())
            GradientButton(
                text = stringResource(R.string.download),
                onClick = onSubmit,
                enabled = canSubmit && !submitting,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = if (canSubmit) Color.White else brand.muted, modifier = Modifier.size(20.dp)) },
            )
        }
    }
}

/** The colored hint under the field, like the web's source chip. */
@Composable
private fun LinkChip(source: LinkSource, canSubmit: Boolean, message: String) {
    val brand = LocalBrand.current
    val color = when {
        source is LinkSource.Drm || source == LinkSource.Invalid -> brand.err
        !canSubmit -> brand.warn
        source is LinkSource.Hls || source is LinkSource.DirectFile -> brand.accent
        else -> brand.accent2
    }
    Text(
        message,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPicker(quality: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label: @Composable (Int?) -> String = { q -> q?.let { "${it}p" } ?: stringResource(R.string.quality_best) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label(quality),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.quality_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QUALITIES.forEach { q -> DropdownMenuItem(text = { Text(label(q)) }, onClick = { onChange(q); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Toolbar(state: LibraryUiState, vm: LibraryViewModel) {
    val brand = LocalBrand.current
    val c = state.controls
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                LibraryFilter.All to R.string.filter_all,
                LibraryFilter.Starred to R.string.filter_starred,
                LibraryFilter.Active to R.string.filter_active,
                LibraryFilter.Done to R.string.filter_done,
                LibraryFilter.Failed to R.string.filter_failed,
            )
            // Like the web: hide empty filters, but keep "Todas" and the selected one.
            items(filters.filter { (f, _) -> f == LibraryFilter.All || f == c.filter || (state.counts[f] ?: 0) > 0 }) { (filter, label) ->
                FilterChip(
                    selected = c.filter == filter,
                    onClick = { vm.onFilter(filter) },
                    label = { Text("${stringResource(label)}  ${state.counts[filter] ?: 0}") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brand.accent,
                        selectedLabelColor = Color.White,
                        containerColor = Color.Transparent,
                        labelColor = brand.muted,
                    ),
                )
            }
        }
        // Search lives in the header (🔍), so the sort gets the whole row.
        SortPicker(c.sort, vm::onSort, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        if ((state.counts[LibraryFilter.Failed] ?: 0) > 0) {
            TextButton(onClick = vm::clearFailed, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.clear_failed), color = brand.err)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortPicker(sort: LibrarySort, onChange: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label: @Composable (LibrarySort) -> String = {
        stringResource(
            when (it) {
                LibrarySort.Recent -> R.string.sort_recent
                LibrarySort.Views -> R.string.sort_views
                LibrarySort.Name -> R.string.sort_name
                LibrarySort.Size -> R.string.sort_size
            },
        )
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label(sort),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(50),
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 14.sp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { s -> DropdownMenuItem(text = { Text(label(s)) }, onClick = { onChange(s); expanded = false }) }
        }
    }
}

@Composable
private fun DayTitle(day: DayLabel) {
    val text = when (day) {
        DayLabel.Today -> stringResource(R.string.today)
        DayLabel.Yesterday -> stringResource(R.string.yesterday)
        is DayLabel.Date -> day.date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")))
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
    }
    Text(
        text.uppercase(Locale.forLanguageTag("es")),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = LocalBrand.current.muted,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun EmptyState(onExample: () -> Unit) {
    val brand = LocalBrand.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 24.dp, end = 24.dp),
    ) {
        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = brand.accent, modifier = Modifier.size(72.dp))
        Text(stringResource(R.string.empty_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = brand.text)
        Text(stringResource(R.string.empty_body), fontSize = 14.sp, color = brand.muted, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onExample) { Text(stringResource(R.string.try_example)) }
    }
}

/** First http(s) link in the clipboard, or null. */
private fun pasteText(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return null
    val text = (0 until clip.itemCount).joinToString(" ") { clip.getItemAt(it).coerceToText(context).toString() }
    return extractUrl(text)
}

/** Shared or pasted text often wraps the link ("Mira esto: https://…"): keep only the link. */
fun extractUrl(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
