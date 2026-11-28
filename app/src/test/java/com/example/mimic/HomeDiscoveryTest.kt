package com.example.mimic

import com.example.mimic.data.HomeConfig
import com.example.mimic.data.HomeSection
import com.example.mimic.data.online.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeDiscoveryTest {
    private val track = OnlineTrack("abcdefghijk", "Real track", "Artist")

    @Test fun emptyRefreshCannotErasePreviouslyLoadedPicks() = runBlocking {
        var calls = 0
        val store = HomeDiscoveryStore(fetch = { _, _ -> Result.success(if (++calls == 1) DiscoveryContent(tracks = listOf(track)) else DiscoveryContent()) })
        store.load(DiscoveryFeed.NEW_RELEASES, emptyList())
        store.load(DiscoveryFeed.NEW_RELEASES, emptyList(), force = true)
        assertEquals(listOf(track), store.state.value.getValue(DiscoveryFeed.NEW_RELEASES).content!!.tracks)
        assertTrue(store.state.value.getValue(DiscoveryFeed.NEW_RELEASES).failed)
    }

    @Test fun navigationReusesCacheAndExplicitRefreshReloads() = runBlocking {
        var time = 1000L; var calls = 0
        val store = HomeDiscoveryStore(fetch = { _, _ -> calls++; Result.success(DiscoveryContent(tracks = listOf(track))) }, now = { time })
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        assertEquals(1, calls)
        store.load(DiscoveryFeed.RECOMMENDED, emptyList(), true)
        assertEquals(2, calls)
        time += 30 * 60_000L
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        assertEquals(3, calls)
    }

    @Test fun refreshFailureKeepsPreviousPicksAndHasCooldown() = runBlocking {
        var time = 1000L; var calls = 0
        val store = HomeDiscoveryStore(fetch = { _, _ -> if (++calls == 1) Result.success(DiscoveryContent(tracks = listOf(track))) else Result.failure(Exception("offline")) }, now = { time })
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        time += 1
        store.load(DiscoveryFeed.RECOMMENDED, emptyList(), true)
        val state = store.state.value.getValue(DiscoveryFeed.RECOMMENDED)
        assertTrue(state.failed); assertFalse(state.loading)
        assertEquals(listOf(track), state.content!!.tracks)
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        assertEquals(2, calls)
        time += 60_000
        store.load(DiscoveryFeed.RECOMMENDED, emptyList())
        assertEquals(3, calls)
    }

    @Test fun failedAlbumsDoNotEraseSuccessfulSongsOrNewReleases() = runBlocking {
        val store = HomeDiscoveryStore(fetch = { feed, _ -> if (feed == DiscoveryFeed.ALBUMS) Result.failure(Exception()) else Result.success(DiscoveryContent(tracks = listOf(track))) })
        DiscoveryFeed.entries.map { feed -> async { store.load(feed, emptyList()) } }.awaitAll()
        assertTrue(store.state.value.getValue(DiscoveryFeed.ALBUMS).failed)
        assertEquals(track, store.state.value.getValue(DiscoveryFeed.NEW_RELEASES).content!!.tracks.single())
        assertEquals(track, store.state.value.getValue(DiscoveryFeed.RECOMMENDED).content!!.tracks.single())
    }

    @Test fun concurrentLoadsCoalesce() = runBlocking {
        val calls = AtomicInteger()
        val store = HomeDiscoveryStore(fetch = { _, _ -> calls.incrementAndGet(); delay(50); Result.success(DiscoveryContent()) })
        List(5) { async { store.load(DiscoveryFeed.ALBUMS, emptyList()) } }.awaitAll()
        assertEquals(1, calls.get())
    }

    @Test fun sessionResetDropsOldPersonalizedResponse() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val store = HomeDiscoveryStore(fetch = { _, _ -> started.complete(Unit); finish.await(); Result.success(DiscoveryContent(tracks = listOf(track))) })
        val job = launch { store.load(DiscoveryFeed.RECOMMENDED, emptyList()) }
        started.await(); store.clear(); finish.complete(Unit); job.join()
        assertNull(store.state.value.getValue(DiscoveryFeed.RECOMMENDED).content)
    }

    @Test fun cancellationDoesNotBecomeFailureOrLeaveSpinner() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val store = HomeDiscoveryStore(fetch = { _, _ -> started.complete(Unit); awaitCancellation() })
        val job = launch { store.load(DiscoveryFeed.RECOMMENDED, emptyList()) }
        started.await(); job.cancelAndJoin()
        assertFalse(store.state.value.getValue(DiscoveryFeed.RECOMMENDED).loading)
        assertFalse(store.state.value.getValue(DiscoveryFeed.RECOMMENDED).failed)
    }

    @Test fun oldHomeOrderGainsDiscoveryAfterQuickAccessWithoutLosingHiddenSections() {
        val legacy = HomeSection.entries.filterNot { it in setOf(HomeSection.RECOMMENDED_SONGS, HomeSection.SUGGESTED_ALBUMS, HomeSection.NEW_RELEASES) }
        val config = HomeConfig(legacy, setOf(HomeSection.DOWNLOADS))
        assertEquals(listOf(HomeSection.QUICK_ACCESS, HomeSection.NEW_RELEASES, HomeSection.RECOMMENDED_SONGS, HomeSection.SUGGESTED_ALBUMS), config.completeOrder().take(4))
        assertFalse(HomeSection.DOWNLOADS in config.visibleOrdered())
        assertEquals(legacy, config.completeOrder().filter { it in legacy })
    }
}
