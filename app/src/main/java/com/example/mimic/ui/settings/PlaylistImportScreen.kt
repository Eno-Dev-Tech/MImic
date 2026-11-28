package com.example.mimic.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mimic.data.PlaylistCsv
import com.example.mimic.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistImportScreen(player: PlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf<PlaylistCsv.Review?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true; review = null; message = null
            try {
                val tracks = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val chars = CharArray(PlaylistCsv.MAX_CHARS + 1)
                        var count = 0
                        while (count < chars.size) {
                            val n = reader.read(chars, count, chars.size - count)
                            if (n < 0) break
                            count += n
                        }
                        PlaylistCsv.parse(String(chars, 0, count))
                    } ?: error("Could not open CSV")
                }
                review = withContext(Dispatchers.Default) { PlaylistCsv.review(tracks, player.knownSongs.value) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not read CSV" }
            finally { working = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Header("Import playlist CSV", onBack)
        Note("Import a Spotify playlist CSV using Track Name and Artist Name columns. Matches songs already in your MImic library, including phone music, Navidrome and saved online tracks. Nothing is uploaded. Missing or ambiguous recordings are skipped for you to review.")
        Note("CSV parser adapted from Musify. Copyright © 2026 Valeri Gokadze, GPL-3.0-or-later. Modified for MImic on 2026-11-20.")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }, enabled = !working) { Text("Choose CSV…") }
            if (working) CircularProgressIndicator()
            message?.let { Text(it) }
            review?.let { r ->
                Text("${r.total} rows · ${r.songs.size} unique matched songs · ${r.missing.size} missing · ${r.ambiguous.size} ambiguous")
                if (r.songs.isNotEmpty()) {
                    Text("Preview (CSV order)", style = MaterialTheme.typography.titleMedium)
                    r.songs.take(20).forEach { Text("${it.title} — ${it.artist}") }
                    Button(onClick = {
                        player.createPlaylist(name.trim(), r.songs)
                        message = "Playlist creation requested: ${r.songs.size} songs. Find it in Library → Playlists."
                        review = null
                    }, enabled = name.isNotBlank() && !working) { Text("Create playlist with ${r.songs.size} songs") }
                }
                (r.missing.map { "Missing: ${it.title} — ${it.artist}" } + r.ambiguous.map { "Ambiguous: ${it.title} — ${it.artist}" }).take(50).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (r.missing.size + r.ambiguous.size > 50) Text("Showing the first 50 skipped rows.")
            }
        }
    }
}
