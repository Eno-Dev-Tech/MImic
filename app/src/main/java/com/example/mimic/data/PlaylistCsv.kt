/* Adapted from Musify lib/screens/import_spotify_playlist_page.dart.
 * Copyright (C) 2026 Valeri Gokadze; GPL-3.0-or-later.
 * Modified for MImic: strict bounded CSV, exact existing-library matches,
 * explicit review of misses/ambiguities, no third-party exporter or audio search.
 */
package com.example.mimic.data

import java.util.Locale

object PlaylistCsv {
    const val MAX_CHARS = 1024 * 1024
    data class Track(val title: String, val artist: String)
    data class Review(val songs: List<Song>, val missing: List<Track>, val ambiguous: List<Track>, val total: Int)

    fun parse(input: String): List<Track> {
        require(input.length <= MAX_CHARS) { "CSV is larger than 1 MB" }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var i = 0
        fun finishField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
        fun finishRow() {
            finishField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            require(rows.size <= 5001) { "At most 5000 tracks can be imported at once" }
            row = mutableListOf()
        }
        val text = input.removePrefix("\uFEFF")
        while (i < text.length) {
            val c = text[i++]
            when {
                quoted && c == '"' -> {
                    if (i < text.length && text[i] == '"') { field.append('"'); i++ }
                    else { quoted = false; closedQuote = true }
                }
                quoted -> field.append(c)
                c == ',' -> finishField()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i < text.length && text[i] == '\n') i++
                    finishRow()
                }
                c == '"' -> { require(field.isEmpty() && !closedQuote) { "Invalid CSV quoting" }; quoted = true }
                else -> { require(!closedQuote || c.isWhitespace()) { "Unexpected text after quote" }; if (!closedQuote) field.append(c) }
            }
        }
        require(!quoted) { "CSV has an unclosed quote" }
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
        require(rows.size >= 2) { "CSV needs a header and at least one track" }
        val headers = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        fun nameColumn(h: String) = listOf("id", "uri", "url", "genre").none { h.contains(it) }
        val title = headers.indexOfFirst { (it.contains("track") || it.contains("song")) && nameColumn(it) }
        val artist = headers.indexOfFirst { it.contains("artist") && nameColumn(it) }
        require(title >= 0 && artist >= 0) { "Expected Track Name (or Song Name) and Artist Name columns" }
        return rows.drop(1).mapNotNull { r ->
            require(r.size == headers.size) { "A CSV row has a different number of columns" }
            val t = r[title].trim(); val a = r[artist].trim()
            if (t.isBlank() || a.isBlank()) null else Track(t, a)
        }.also { require(it.isNotEmpty()) { "No tracks with a title and artist were found" } }
    }

    fun review(tracks: List<Track>, library: List<Song>): Review {
        fun key(title: String, artist: String) = title.trim().lowercase(Locale.ROOT) to artist.trim().lowercase(Locale.ROOT)
        val index = library.distinctBy { it.id }.groupBy { key(it.title, it.artist) }
        val found = mutableListOf<Song>(); val missing = mutableListOf<Track>(); val ambiguous = mutableListOf<Track>()
        for (track in tracks) {
            val matches = index[key(track.title, track.artist)].orEmpty()
            when (matches.size) {
                0 -> missing.add(track)
                1 -> found.add(matches.single())
                else -> ambiguous.add(track)
            }
        }
        // MImic playlists contain one entry per song ID; preserve first-seen CSV order.
        return Review(found.distinctBy { it.id }, missing, ambiguous, tracks.size)
    }
}
