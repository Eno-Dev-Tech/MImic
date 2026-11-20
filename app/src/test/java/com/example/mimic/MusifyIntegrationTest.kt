package com.example.mimic

import com.example.mimic.data.*
import com.example.mimic.data.db.PlayHistoryEntity
import com.example.mimic.player.SponsorSegments
import com.example.mimic.player.SponsorSegment
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MusifyIntegrationTest {
    private fun song(id: Long, title: String = "Song", artist: String = "Artist") =
        Song(id, title, artist, "Album", 180000, "content://music/$id", null, 0)

    @Test fun csvHandlesBomQuotesNewlinesAndOrder() {
        val csv = "\uFEFFTrack Name,Artist Name\r\n\"First, song\",Artist\r\n\"A \"\"quote\"\"\nline\",Singer\r\n"
        assertEquals(listOf(PlaylistCsv.Track("First, song", "Artist"), PlaylistCsv.Track("A \"quote\"\nline", "Singer")), PlaylistCsv.parse(csv))
    }
    @Test fun csvDoesNotMistakeArtistIdForName() {
        assertEquals("Artist", PlaylistCsv.parse("Track URI,Track Name,Artist ID,Artist Name\nx,Song,id,Artist").single().artist)
    }
    @Test fun csvRejectsIncompleteQuotes() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistCsv.parse("Track Name,Artist Name\n\"Song,Artist") }
    }
    @Test fun csvRejectsMisalignedRows() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistCsv.parse("Track Name,Artist Name\nSong,Artist,extra") }
    }
    @Test fun csvRejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistCsv.parse("a".repeat(PlaylistCsv.MAX_CHARS + 1)) }
    }
    @Test fun csvReviewPreservesOrderDeduplicatesAndRefusesAmbiguity() {
        val tracks = listOf(PlaylistCsv.Track("Second", "Artist"), PlaylistCsv.Track("song", "artist"), PlaylistCsv.Track("Second", "Artist"), PlaylistCsv.Track("Missing", "Artist"), PlaylistCsv.Track("Ambiguous", "Artist"))
        val review = PlaylistCsv.review(tracks, listOf(song(1), song(2, "Second"), song(3, "Ambiguous"), song(4, "Ambiguous")))
        assertEquals(listOf(2L, 1L), review.songs.map { it.id })
        assertEquals(1, review.missing.size); assertEquals(1, review.ambiguous.size)
        assertEquals(5, review.total)
    }
    @Test fun csvDoesNotGuessAlternateVersionsOrArtist() {
        assertTrue(PlaylistCsv.review(listOf(PlaylistCsv.Track("Song (live)", "Artist"), PlaylistCsv.Track("Song", "Other")), listOf(song(1))).songs.isEmpty())
    }
    @Test fun sponsorSegmentsKeepMillisecondsAndMergeOverlaps() {
        val parsed = SponsorSegments.parse("""[{"category":"sponsor","segment":[1.125,3.5]},{"category":"intro","segment":[3,4]}]""")
        assertEquals(listOf(SponsorSegment(1125, 4000)), parsed)
    }
    @Test fun sponsorSegmentsRejectNonSkipAndInvalidRanges() {
        val parsed = SponsorSegments.parse("""[{"category":"sponsor","actionType":"mute","segment":[1,4]},{"category":"unknown","segment":[1,4]},{"category":"intro","segment":[-1,4]},{"category":"intro","segment":[4,2]}]""")
        assertTrue(parsed.isEmpty())
    }
    @Test fun sponsorSeeksOnlyInsideSegmentAndClampsToDuration() {
        val segments = listOf(SponsorSegment(1000, 5000))
        assertNull(SponsorSegments.skipTarget(segments, 999, 10000))
        assertEquals(5000L, SponsorSegments.skipTarget(segments, 1000, 10000))
        assertNull(SponsorSegments.skipTarget(segments, 5000, 10000))
        assertEquals(3000L, SponsorSegments.skipTarget(segments, 2000, 3000))
        assertNull(SponsorSegments.skipTarget(segments, 2000, -1))
    }
    private val zone = ZoneId.of("Europe/Budapest")
    private fun time(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()
    @Test fun monthlyRecapUsesLocalCalendarBoundaries() {
        val now = time("2026-11-20T12:00:00+01:00")
        val events = listOf("2026-10-31T23:59:59+01:00", "2026-11-01T00:00:00+01:00", "2026-11-20T11:00:00+01:00", "2026-11-21T00:00:00+01:00").map { PlayHistoryEntity(songId = 1, playedAt = time(it)) }
        val stats = ListeningStats.calendarRecap(events, listOf(song(1)), now, false, zone)
        assertEquals(2, stats.plays); assertEquals(20, stats.playsPerDay.size)
        assertEquals(2, stats.playsPerDay.sum()); assertEquals(1, stats.streakDays)
    }
    @Test fun annualRecapExcludesPreviousYearAndFuture() {
        val now = time("2026-01-01T12:00:00+01:00")
        val events = listOf("2025-12-31T23:59:59+01:00", "2026-01-01T00:00:00+01:00", "2026-01-02T00:00:00+01:00").map { PlayHistoryEntity(songId = 1, playedAt = time(it)) }
        val stats = ListeningStats.calendarRecap(events, listOf(song(1)), now, true, zone)
        assertEquals(1, stats.plays); assertEquals(listOf(1), stats.playsPerDay)
        assertEquals(2, stats.streakDays)
    }
    @Test fun recapKeepsCorrectDayAcrossDst() {
        val now = time("2026-03-30T12:00:00+02:00")
        val events = listOf("2026-03-28T23:30:00+01:00", "2026-03-29T23:30:00+02:00").map { PlayHistoryEntity(songId = 1, playedAt = time(it)) }
        val stats = ListeningStats.calendarRecap(events, listOf(song(1)), now, false, zone)
        assertEquals(1, stats.playsPerDay[27]); assertEquals(1, stats.playsPerDay[28]); assertEquals(2, stats.playsPerDay.sum())
    }
}
