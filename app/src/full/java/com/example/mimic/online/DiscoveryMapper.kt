package com.example.mimic.online

import com.example.mimic.data.online.*
import com.example.mimic.online.data.innertube.InnertubeParser
import com.example.mimic.online.data.model.HomeShelf
import com.example.mimic.online.data.model.Song
import com.example.mimic.online.data.model.artworkAt
import com.example.mimic.online.data.model.durationMillis

/** Adapts actual catalogue items; never invents albums or calls a search result a new release. */
internal object DiscoveryMapper {
    fun shelves(shelves: List<HomeShelf>, albumsOnly: Boolean = false): DiscoveryContent {
        val items = shelves.flatMap { it.items }
        val albums = items.mapNotNull { item ->
            item.browseId?.takeIf { it.startsWith("MPREb") }?.let { id ->
                OnlineCollection(id, OnlineCollectionKind.ALBUM, item.title, item.subtitle, item.thumbnailUrl.artworkAt(544))
            }
        }.filter { it.title.isNotBlank() }.distinctBy { it.browseId }.take(20)
        val tracks = if (albumsOnly) emptyList() else items.mapNotNull { item ->
            item.videoId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { id ->
                OnlineTrack(id, item.title, InnertubeParser.artistFromSubtitle(item.subtitle), artworkUrl = item.thumbnailUrl.artworkAt(544))
            }
        }.filter { it.title.isNotBlank() }.distinctBy { it.videoId }.take(20)
        return DiscoveryContent(tracks, albums)
    }

    fun track(song: Song) = OnlineTrack(
        song.videoId, song.title, song.artist, song.albumName, song.albumId, song.artistId,
        song.durationMillis(), song.artworkAt(544), song.isVideo, song.trackNumber ?: 0,
    )

    /** Ranking adapted from Musify common_services.dart _getRecommendationsFromSeedSongs.
     * Copyright (C) 2026 Valeri Gokadze, GPL-3.0-or-later.
     * Modified 2026-11-20: native catalogue radio, excludes seeds, bounded/deduplicated.
     */
    fun rankRelated(groups: List<List<OnlineTrack>>, seeds: Set<String>): List<OnlineTrack> {
        val scores = linkedMapOf<String, Double>()
        val tracks = linkedMapOf<String, OnlineTrack>()
        groups.forEachIndexed { seedIndex, songs ->
            songs.filter { it.videoId !in seeds }.distinctBy { it.videoId }.take(8).forEachIndexed { position, song ->
                scores[song.videoId] = (scores[song.videoId] ?: 0.0) + (1.0 - position / 8.0) * (1.0 - seedIndex.toDouble() / groups.size)
                tracks.putIfAbsent(song.videoId, song)
            }
        }
        return scores.entries.sortedByDescending { it.value }.take(20).map { tracks.getValue(it.key) }
    }
}
