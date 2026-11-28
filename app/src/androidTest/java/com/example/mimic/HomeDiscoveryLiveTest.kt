package com.example.mimic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mimic.data.online.DiscoveryFeed
import com.example.mimic.data.online.DiscoveryCountry
import com.example.mimic.online.OnlineMusic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit live catalogue probe: no credentials logged, no audio playback/downloads. */
@RunWith(AndroidJUnit4::class)
class HomeDiscoveryLiveTest {
    @Test fun realCatalogueFeedsReturnBrowsableMetadata() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("mimicLiveDiscovery") == "true" && OnlineMusic.AVAILABLE)
        OnlineMusic.init(InstrumentationRegistry.getInstrumentation().targetContext)
        println("LIVE_DISCOVERY accountConnected=${OnlineMusic.youTubeMusicConnected}")
        val country = InstrumentationRegistry.getArguments().getString("mimicDiscoveryCountry")?.let { DiscoveryCountry.valueOf(it) } ?: DiscoveryCountry.WORLDWIDE
        val failures = mutableListOf<String>()
        for (feed in DiscoveryFeed.entries) {
            val result = withTimeout(45_000) { OnlineMusic.discovery(feed, listOf("dQw4w9WgXcQ"), country) }
            val content = result.getOrNull()
            if (content == null) { failures.add("$feed: ${result.exceptionOrNull()?.javaClass?.simpleName}"); continue }
            println("LIVE_DISCOVERY $country $feed tracks=${content.tracks.size} collections=${content.collections.size}")
            if (content.isEmpty) failures.add("$feed returned no items")
            assertTrue(content.tracks.all { it.title.isNotBlank() && it.videoId.length == 11 })
            assertTrue(content.collections.all { it.title.isNotBlank() && it.browseId.isNotBlank() })
            if (feed == DiscoveryFeed.ALBUMS && content.collections.isNotEmpty()) {
                val page = withTimeout(45_000) { OnlineMusic.collection(content.collections.first()).getOrThrow() }
                println("LIVE_DISCOVERY openedAlbumPlaylistTracks=${page.tracks.size}")
                assertTrue("Suggested album must open into actual tracks", page.tracks.isNotEmpty())
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
