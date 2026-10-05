package com.carlospinan.guardavideo.app.ui.providers

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.ui.theme.LocalBrand
import com.carlospinan.guardavideo.source.Access
import com.carlospinan.guardavideo.source.PROVIDERS
import com.carlospinan.guardavideo.source.Provider
import com.carlospinan.guardavideo.source.ProviderCategory

/** The reviewed list of sites (same as the web's card), grouped like it; from the shared module. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(onBack: () -> Unit) {
    val brand = LocalBrand.current
    Scaffold(
        containerColor = brand.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = brand.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Text(stringResource(R.string.providers_intro), fontSize = 14.sp, color = brand.muted) }
            val supported = PROVIDERS.filter { it.access == Access.Public }
            ProviderCategory.entries.forEach { category ->
                val items = supported.filter { it.category == category }
                if (items.isNotEmpty()) item(key = category.name) { Group(category.label, items, brand.accent) }
            }
            item(key = "login") { Group(stringResource(R.string.providers_login), PROVIDERS.filter { it.access == Access.Login }, brand.warn) }
            item(key = "drm") { Group(stringResource(R.string.providers_drm), PROVIDERS.filter { it.access == Access.Drm }, brand.err) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, providers: List<Provider>, tint: Color) {
    val brand = LocalBrand.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "${title.uppercase()}  ·  ${providers.size}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = brand.muted,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            providers.forEach { provider ->
                Text(
                    provider.name,
                    fontSize = 13.sp,
                    color = brand.text,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
