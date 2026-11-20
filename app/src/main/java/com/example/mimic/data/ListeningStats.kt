package com.example.mimic.data

import com.example.mimic.data.db.PlayHistoryEntity

/**
 * Listening statistics (MImic 3.2), computed on the phone from the play history. A "play" is a
 * song that started playing (see PlaybackService); listening time is estimated from each played
 * song's length, so it is an upper bound when songs were skipped early — the screen says so.
 */
data class ListeningStats(
    val plays: Int,
    val distinctSongs: Int,
    val estimatedMinutes: Long,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<String, Int>>,
    /** Plays per day, oldest first, one entry per day of the window (zeros included). */
    val playsPerDay: List<Int>,
    /** Consecutive days up to today with at least one play. */
    val streakDays: Int,
) {
    companion object {
        const val DAY_MS = 86_400_000L

        /** Musify-inspired monthly/annual recap, computed from existing MImic history.
         * Calendar boundaries use the device's time zone, including DST changes.
         * No historical data is converted or overwritten; duration remains an estimate.
         */
        fun calendarRecap(history: List<PlayHistoryEntity>, songs: List<Song>, now: Long, annual: Boolean, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): ListeningStats {
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val start = if (annual) today.withDayOfYear(1) else today.withDayOfMonth(1)
            val since = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val filtered = history.filter { it.playedAt in since..now }
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1
            val base = compute(filtered, songs, now, days, zone.rules.getOffset(java.time.Instant.ofEpochMilli(now)).totalSeconds * 1000L)
            val perDay = IntArray(days)
            for (event in filtered) {
                val date = java.time.Instant.ofEpochMilli(event.playedAt).atZone(zone).toLocalDate()
                perDay[java.time.temporal.ChronoUnit.DAYS.between(start, date).toInt()]++
            }
            val active = history.filter { it.playedAt <= now }.map { java.time.Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }.toSet()
            var streak = 0
            while (today.minusDays(streak.toLong()) in active) streak++
            return base.copy(playsPerDay = perDay.toList(), streakDays = streak)
        }

        fun compute(history: List<PlayHistoryEntity>, songs: List<Song>, now: Long, days: Int, zoneOffsetMs: Long = 0): ListeningStats {
            val byId = songs.associateBy { it.id }
            val since = now - days * DAY_MS
            val window = history.filter { it.playedAt in since..now }
            val counts = window.groupingBy { it.songId }.eachCount()
            val topSongs = counts.entries.mapNotNull { (id, n) -> byId[id]?.let { it to n } }
                .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
                .take(10)
            val artistCounts = HashMap<String, Int>()
            for ((id, n) in counts) {
                val artist = byId[id]?.artist ?: continue
                if (artist == MetadataFallback.UNKNOWN_ARTIST) continue
                artistCounts[artist] = (artistCounts[artist] ?: 0) + n
            }
            val minutes = window.sumOf { (byId[it.songId]?.durationMs ?: 0L) } / 60_000L
            fun dayIndex(t: Long) = Math.floorDiv(t + zoneOffsetMs, DAY_MS)
            val today = dayIndex(now)
            val perDay = IntArray(days)
            for (h in window) {
                val i = (days - 1) - (today - dayIndex(h.playedAt)).toInt()
                if (i in 0 until days) perDay[i]++
            }
            val activeDays = history.map { dayIndex(it.playedAt) }.toHashSet()
            var streak = 0
            while (today - streak in activeDays) streak++
            return ListeningStats(
                plays = window.size,
                distinctSongs = counts.size,
                estimatedMinutes = minutes,
                topSongs = topSongs,
                topArtists = artistCounts.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
                playsPerDay = perDay.toList(),
                streakDays = streak,
            )
        }
    }
}
