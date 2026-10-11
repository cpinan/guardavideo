package com.carlospinan.guardavideo.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.media.SearchHit
import com.carlospinan.guardavideo.app.ui.formatClock
import com.carlospinan.guardavideo.app.ui.library.BrandCard
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.app.ui.openInBrowser
import com.carlospinan.guardavideo.app.ui.rememberAskNotifications
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand

/** Global search tab: type, pick the sites (Dailymotion on by default), download from the results. */
@Composable
fun SearchScreen(onOpenLibrary: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val askNotifications = rememberAskNotifications()
    val focus = LocalFocusManager.current // closes the keyboard so results are visible
    val state = viewModel.state
    val seeLibrary = stringResource(R.string.web_search_see_library)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is UiMessage.Res -> context.getString(message.id)
                is UiMessage.Text -> message.text
            }
            if (snackbar.showSnackbar(text, actionLabel = seeLibrary) == SnackbarResult.ActionPerformed) onOpenLibrary()
        }
    }

    Scaffold(containerColor = brand.bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Text(
                    stringResource(R.string.web_search_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = brand.text,
                    modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
                )
            }
            item(key = "field") {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQuery,
                    placeholder = { Text(stringResource(R.string.web_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clear) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.web_search_clear)) }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.search() }),
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = brand.card, focusedContainerColor = brand.card),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
            }
            item(key = "sites") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.web_search_sites), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = brand.muted, modifier = Modifier.padding(horizontal = 20.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.sites, key = { it.id }) { site ->
                            val on = site in viewModel.selected
                            FilterChip(
                                selected = on,
                                onClick = { viewModel.toggle(site); if (viewModel.query.isNotBlank()) viewModel.search() },
                                label = {
                                    Text(if (site.recommended) "${site.label} · ${stringResource(R.string.web_search_recommended)}" else site.label)
                                },
                                leadingIcon = if (on) {{ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }} else null,
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = brand.accent,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = Color.Transparent,
                                    labelColor = brand.muted,
                                ),
                            )
                        }
                    }
                }
            }
            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = brand.accent)
                    }
                }
                !state.searched -> item(key = "intro") {
                    Text(stringResource(R.string.web_search_intro), fontSize = 14.sp, color = brand.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp))
                }
                else -> {
                    for (site in state.failedSites) item(key = "failed-${site.id}") {
                        Text(
                            stringResource(R.string.web_search_failed, site.label),
                            color = brand.err,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.hits.isEmpty() && state.failedSites.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = brand.muted, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.web_search_empty, viewModel.query.trim()), color = brand.muted, textAlign = TextAlign.Center, fontSize = 14.sp)
                        }
                    }
                    items(state.hits, key = { it.url }) { hit ->
                        HitCard(
                            hit,
                            queued = hit.url in state.queued,
                            onDownload = { askNotifications(); viewModel.download(hit) },
                            onOpen = { openInBrowser(context, hit.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: SearchHit, queued: Boolean, onDownload: () -> Unit, onOpen: () -> Unit) {
    val brand = LocalBrand.current
    BrandCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp).clip(RoundedCornerShape(10.dp)).background(brand.card2).aspectRatio(16f / 9f)) {
                AsyncImage(model = hit.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                hit.durationSec?.let {
                    Text(
                        formatClock(it * 1000),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(hit.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.text)
                Text(listOfNotNull(hit.site.label, hit.author).joinToString(" · "), fontSize = 12.sp, color = brand.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedIconButton(onClick = onDownload, enabled = !queued, modifier = Modifier.size(48.dp)) {
                        Icon(if (queued) Icons.Rounded.Check else Icons.Rounded.Download, contentDescription = stringResource(R.string.web_search_download))
                    }
                    OutlinedIconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = stringResource(R.string.web_search_open))
                    }
                }
            }
        }
    }
}
