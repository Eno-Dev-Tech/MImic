package com.example.mimic.online

import com.example.mimic.data.online.*
import com.example.mimic.online.data.model.HomeShelf
import com.example.mimic.online.data.model.ShelfItem
import org.junit.Assert.*
import org.junit.Test

class DiscoveryMapperTest {
    @Test fun recentUploadsRequireARealDateWithinFourteenDays() {
        val now = 30 * 86_400_000L
        assertFalse(YouTubeSearch.isRecentUpload(null, now))
        assertFalse(YouTubeSearch.isRecentUpload(now + 1, now))
        assertFalse(YouTubeSearch.isRecentUpload(now - 15 * 86_400_000L, now))
        assertTrue(YouTubeSearch.isRecentUpload(now - 7 * 86_400_000L, now))
    }
    @Test fun albumsKeepRealIdentityArtAndSubtitleAndNeverIncludePlaylistsOrArtists() {
        val rows = listOf(
            ShelfItem("Album", "Artist · 2026", "https://example.test/cover", null, "MPREbalbum"),
            ShelfItem("Playlist", "Curator", null, null, "VLplaylist"),
            ShelfItem("Artist", "Channel", null, null, "UCartist"),
            ShelfItem("Song", "Artist", null, "abcdefghijk", null),
        )
        val content = DiscoveryMapper.shelves(listOf(HomeShelf("Picks", rows + rows)), albumsOnly = true)
        assertTrue(content.tracks.isEmpty())
        val album = content.collections.single()
        assertEquals("MPREbalbum", album.browseId)
        assertEquals(OnlineCollectionKind.ALBUM, album.kind)
        assertEquals("Artist · 2026", album.subtitle)
        assertTrue(album.artworkUrl!!.startsWith("https://example.test/cover"))
    }

    @Test fun releasesKeepSongsAndAlbumsButDropMalformedTrackIds() {
        val rows = listOf(ShelfItem("Song", "Artist", null, "abcdefghijk", null), ShelfItem("Invalid", "Artist", null, "invalid", null), ShelfItem("Album", "Artist", null, null, "MPREbalbum"))
        val content = DiscoveryMapper.shelves(listOf(HomeShelf("New releases", rows)))
        assertEquals(listOf("abcdefghijk"), content.tracks.map { it.videoId })
        assertEquals(listOf("MPREbalbum"), content.collections.map { it.browseId })
    }

    @Test fun relatedRankingRewardsAgreementPreservesMetadataAndExcludesSeeds() {
        fun track(id: String) = OnlineTrack(id, "Title $id", "Artist $id", artworkUrl = "https://example.test/$id")
        val a = track("a"); val b = track("b"); val seed = track("seed")
        val ranked = DiscoveryMapper.rankRelated(listOf(listOf(seed, a, b, a), listOf(b)), setOf("seed"))
        assertEquals(listOf(b, a), ranked)
        assertEquals(2, ranked.map { it.videoId }.distinct().size)
    }
}
