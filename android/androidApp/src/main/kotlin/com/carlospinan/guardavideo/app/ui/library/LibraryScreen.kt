package com.carlospinan.guardavideo.app.ui.library

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.sourceMessage
import com.carlospinan.guardavideo.source.LinkSource
import kotlinx.coroutines.launch

private val QUALITIES = listOf<Int?>(null, 1080, 720, 480)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    onPlay: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "form") {
                DownloadForm(
                    url = viewModel.url,
                    onUrlChange = viewModel::onUrlChange,
                    quality = viewModel.quality,
                    onQualityChange = viewModel::onQualityChange,
                    source = viewModel.source,
                    submitting = viewModel.submitting,
                    onSubmit = submit,
                )
            }
            val list = jobs
            if (list != null && list.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            items(list.orEmpty(), key = { it.id }) { job ->
                JobCard(
                    job = job,
                    onPlay = { onPlay(job.id) },
                    onCancel = { viewModel.cancel(job.id) },
                    onRetry = { viewModel.retry(job.id) },
                    onDelete = { viewModel.delete(job.id) },
                    onToggleStar = { viewModel.toggleStar(job) },
                    modifier = Modifier.animateItem(),
                )
            }
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
    submitting: Boolean,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showHint = url.isNotBlank()
    val problem = showHint && !source.canDownload
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.url_label)) },
            placeholder = { Text(stringResource(R.string.url_placeholder)) },
            singleLine = true,
            isError = problem,
            supportingText = if (showHint) {
                {
                    Text(
                        sourceMessage(source),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            } else {
                null
            },
            trailingIcon = {
                IconButton(onClick = {
                    val text = pasteText(context)
                    if (text != null) onUrlChange(text)
                }) { Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.paste)) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { scope.launch { onSubmit() } }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            QualityPicker(quality, onQualityChange, Modifier.weight(1f))
            Button(onClick = onSubmit, enabled = source.canDownload && !submitting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.download), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
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
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QUALITIES.forEach { q ->
                DropdownMenuItem(text = { Text(label(q)) }, onClick = { onChange(q); expanded = false })
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
    ) {
        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
