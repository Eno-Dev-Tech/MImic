package com.example.mimic.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mimic.data.HomeSection
import com.example.mimic.data.online.*
import com.example.mimic.player.PlayerViewModel
import com.example.mimic.ui.library.AlbumCard
import com.example.mimic.ui.library.AppNav
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DiscoveryCountryPicker(country: DiscoveryCountry, onSelect: (DiscoveryCountry) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Discover music", style = MaterialTheme.typography.titleMedium)
            Text("No account needed · choose your music feed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("${country.label} ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DiscoveryCountry.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { expanded = false; onSelect(option) })
                }
            }
        }
    }
}

internal fun HomeSection.discoveryFeed(): DiscoveryFeed? = when (this) {
    HomeSection.RECOMMENDED_SONGS -> DiscoveryFeed.RECOMMENDED
    HomeSection.SUGGESTED_ALBUMS -> DiscoveryFeed.ALBUMS
    HomeSection.NEW_RELEASES -> DiscoveryFeed.NEW_RELEASES
    else -> null
}

internal fun LazyListScope.discoveryShelf(
    title: String, feed: DiscoveryFeed, state: DiscoveryShelfState, vm: PlayerViewModel, nav: AppNav,
) {
    val key = "discovery_${feed.name}"
    item(key = "${key}_header") {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.content?.heading ?: title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.loadHomeDiscovery(setOf(feed), force = true) }, enabled = !state.loading) { Text(if (state.failed) "Retry" else "Refresh") }
        }
    }
    if (state.loading) item(key = "${key}_loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
    val content = state.content
    item(key = "${key}_info") {
        val text = when {
            state.failed && content != null && !content.isEmpty -> "Couldn't refresh. Showing picks saved this session · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))
            state.failed -> "Couldn't load this feed. Check your connection and retry. Your local music still works."
            state.loading && content == null -> "Finding music on YouTube…"
            content == null -> "Music discovery from YouTube · no account needed"
            content.isEmpty -> "No matches returned for this feed. Refresh or choose another country."
            else -> content.explanation
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (content != null) {
        if (content.collections.isNotEmpty()) item(key = "${key}_albums") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(content.collections, key = { it.browseId }) { album ->
                    AlbumCard(album.title, album.subtitle, album.artworkUrl, onClick = { vm.rememberCollection(album); nav.openOnline(album) })
                }
            }
        }
        if (content.tracks.isNotEmpty()) songCarousel(key, content.tracks.map { it.toSong() }, vm, nav)
    }
}
