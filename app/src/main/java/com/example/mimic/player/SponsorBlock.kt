/* Musify-inspired SponsorBlock integration. Copyright (C) 2026 MImic contributors.
 * GPL-3.0-or-later; see SponsorSegments.kt for the adapted upstream parsing code. */
package com.example.mimic.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.mimic.data.AppSettings
import com.example.mimic.data.online.OnlineIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.util.concurrent.TimeUnit

/** Optional seeks on the existing player. Never touches local/Navidrome/radio audio.
 * Disabled by default; no requests until enabled. In a party it never looks up or seeks.
 * Offline YouTube files use only previously cached segments, with no network request.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SponsorBlock(
    context: Context, private val player: Player, scope: CoroutineScope,
    settings: Flow<AppSettings>, private val inParty: () -> Boolean,
) {
    private val cache = File(context.cacheDir, "sponsorblock")
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val job = scope.launch {
        var enabled = false
        val preferenceJob = launch { settings.collect { enabled = it.sponsorBlockEnabled } }
        var loadedKey: Pair<String, Boolean>? = null
        var segments = emptyList<SponsorSegment>()
        var lookup: Deferred<List<SponsorSegment>>? = null
        try {
            while (isActive) {
                val item = player.currentMediaItem
                val videoId = videoId(item)
                val key = videoId?.let { it to (OnlineIds.videoIdOfUri(item?.localConfiguration?.uri?.toString()) != null) }
                if (!enabled || inParty() || key == null) {
                    lookup?.cancel(); lookup = null; loadedKey = null; segments = emptyList()
                } else {
                    if (loadedKey != key) {
                        lookup?.cancel()
                        loadedKey = key
                        segments = emptyList()
                        lookup = async(Dispatchers.IO) { load(key.first, online = key.second) }
                    }
                    lookup?.takeIf { it.isCompleted }?.let { segments = it.await(); lookup = null }
                    if (player.isPlaying && !player.isCurrentMediaItemLive) {
                        SponsorSegments.skipTarget(segments, player.currentPosition, player.duration)?.let(player::seekTo)
                    }
                }
                delay(250)
            }
        } finally { preferenceJob.cancel(); lookup?.cancel() }
    }

    fun release() { job.cancel(); client.dispatcher.cancelAll() }

    private fun videoId(item: MediaItem?): String? =
        (item?.mediaMetadata?.extras?.getString("mimic_youtube_id")
            ?: OnlineIds.videoIdOfUri(item?.localConfiguration?.uri?.toString()))
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }

    private fun load(id: String, online: Boolean): List<SponsorSegment> {
        val file = File(cache, "$id.json")
        try {
            if (file.isFile && file.length() <= MAX_BYTES && (!online || System.currentTimeMillis() - file.lastModified() < 7 * 86400000L)) {
                return SponsorSegments.parse(file.readText())
            }
        } catch (_: Exception) { /* A corrupt cache must never stop playback. */ }
        if (!online) return emptyList()
        return try {
            val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
                .addQueryParameter("videoID", id)
                .addQueryParameter("categories", org.json.JSONArray(SponsorSegments.categories.toList()).toString())
                .addQueryParameter("actionTypes", "[\"skip\"]").build()
            client.newCall(Request.Builder().url(url).header("User-Agent", "MImic-private/SponsorBlock").build()).execute().use { response ->
                val json = when (response.code) {
                    404 -> "[]"
                    200 -> {
                        val input = response.body?.byteStream() ?: return emptyList()
                        val bytes = input.readBytesBounded(MAX_BYTES)
                        bytes.toString(Charsets.UTF_8)
                    }
                    else -> return emptyList() // Failures are not cached as confirmed misses.
                }
                val result = SponsorSegments.parse(json)
                cache.mkdirs()
                // Bound disk use; temporary/cache data is safe to evict.
                cache.listFiles()?.sortedBy { it.lastModified() }?.dropLast(199)?.forEach { it.delete() }
                file.writeText(json)
                result
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            require(out.size() + n <= max) { "Segment response too large" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    companion object { private const val MAX_BYTES = 256 * 1024 }
}
