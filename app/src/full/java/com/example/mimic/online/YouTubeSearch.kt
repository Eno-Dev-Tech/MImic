package com.example.mimic.online

import com.example.mimic.data.online.OnlineTrack
import com.example.mimic.online.data.innertube.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import com.example.mimic.data.online.OnlineCollection
import com.example.mimic.data.online.OnlineCollectionKind
import com.example.mimic.data.online.OnlineCollectionPage
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Regular YouTube search (NewPipeExtractor, already bundled for stream resolution).
 *
 * Why it exists: YouTube Music's song catalog is region-limited. Where it isn't offered — measured
 * from Albania on 2026-10-25: catalog song search answers "No results" while YouTube itself
 * answers normally — MImic falls back to YouTube's own results instead of showing nothing. No
 * region is spoofed; these are the results YouTube serves to this connection, labelled as coming
 * from YouTube. Pages are fetched on demand ("Load more").
 */
object YouTubeSearch {
    class Page1(val tracks: List<OnlineTrack>, val nextToken: String?)

    private val pending = ConcurrentHashMap<String, Pair<SearchExtractor, Page>>()

    /** Public YouTube fallbacks use the existing library. They do not spoof location,
     * use account cookies, or claim playlist suggestions are catalogue album releases.
     */
    suspend fun related(videoId: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        StreamResolver.ensureNewPipe()
        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            .relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::toTrack).filter { it.durationMs in 30_000..1_200_000 }.take(20)
    }

    suspend fun suggestedAlbums(musicQuery: String = ""): List<OnlineCollection> = withContext(Dispatchers.IO) {
        StreamResolver.ensureNewPipe()
        val yt = ServiceList.YouTube
        val ex = yt.getSearchExtractor(yt.searchQHFactory.fromQuery("$musicQuery official full album".trim(), listOf("playlists"), ""))
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val id = android.net.Uri.parse(item.url).getQueryParameter("list")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,200}")) } ?: return@mapNotNull null
            // Real album playlists often use only the release name (no literal word "album").
            // Keep the actual playlist type and publisher; don't manufacture album identities.
            OnlineCollection("ytpl:$id", OnlineCollectionKind.PLAYLIST, item.name, "YouTube playlist · " + item.uploaderName.orEmpty(), item.thumbnails.maxByOrNull { it.width * it.height }?.url)
        }.distinctBy { it.browseId }.take(20)
    }

    suspend fun recentMusic(musicQuery: String = "", exactQuery: String? = null): List<OnlineTrack> = withContext(Dispatchers.IO) {
        StreamResolver.ensureNewPipe()
        val query = exactQuery ?: "$musicQuery official music".trim()
        // The public search page's video + this-week filter. Date metadata is checked too.
        val url = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8") + "&sp=EgQIAxAB"
        val handler = SearchQueryHandler(url, url, query, listOf("videos"), "")
        val ex = ServiceList.YouTube.getSearchExtractor(handler)
        ex.fetchPage()
        val now = System.currentTimeMillis()
        val tracks = linkedMapOf<String, OnlineTrack>()
        val uploadedAt = mutableMapOf<String, Long>()
        var page = ex.initialPage
        var pages = 0
        while (true) {
            page.items.filterIsInstance<StreamInfoItem>().forEach { item ->
                val uploaded = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: return@forEach
                if (!isRecentUpload(uploaded, now)) return@forEach
                val track = toTrack(item) ?: return@forEach
                if (track.durationMs !in 30_000..1_200_000) return@forEach
                tracks.putIfAbsent(track.videoId, track)
                uploadedAt[track.videoId] = uploaded
            }
            if (tracks.size >= 6 || ++pages >= 3 || !Page.isValid(page.nextPage)) break
            page = ex.getPage(page.nextPage)
        }
        tracks.values.sortedByDescending { uploadedAt[it.videoId] }.take(20)
    }

    internal fun isRecentUpload(uploaded: Long?, now: Long): Boolean =
        uploaded != null && now - uploaded in 0..14 * 86_400_000L

    suspend fun playlist(c: OnlineCollection): OnlineCollectionPage = withContext(Dispatchers.IO) {
        val id = c.browseId.removePrefix("ytpl:")
        require(id.matches(Regex("[A-Za-z0-9_-]{10,200}")))
        StreamResolver.ensureNewPipe()
        val url = "https://www.youtube.com/playlist?list=$id"
        val yt = ServiceList.YouTube
        val extractor = yt.getPlaylistExtractor(url)
        extractor.fetchPage()
        val info = PlaylistInfo.getInfo(extractor)
        val items = info.relatedItems.toMutableList()
        var next = info.nextPage
        var pages = 0
        var incomplete = false
        while (Page.isValid(next) && items.size < 300 && pages++ < 4) {
            // Keep the initialized extractor: a fresh extractor's continuation has no
            // playlist header and NewPipe 0.26.3 dereferences it while checking courses.
            val more = try { extractor.getPage(next) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { incomplete = true; break }
            items.addAll(more.items); next = more.nextPage
        }
        val tracks = items.take(300).mapNotNull(::toTrack).distinctBy { it.videoId }
        val subtitle = c.subtitle + if (incomplete || Page.isValid(next) || items.size > 300) " · showing first ${tracks.size} tracks" else ""
        OnlineCollectionPage(c.browseId, c.kind, info.name.ifBlank { c.title }, subtitle, c.artworkUrl, tracks)
    }

    suspend fun search(query: String): Page1 = withContext(Dispatchers.IO) {
        StreamResolver.ensureNewPipe()
        val yt = ServiceList.YouTube
        val ex = yt.getSearchExtractor(yt.searchQHFactory.fromQuery(query, listOf("videos"), ""))
        ex.fetchPage()
        val page = ex.initialPage
        Page1(page.items.filterIsInstance<StreamInfoItem>().mapNotNull(::toTrack), remember(ex, page.nextPage))
    }

    suspend fun more(token: String): Page1 = withContext(Dispatchers.IO) {
        val (ex, next) = pending.remove(token) ?: return@withContext Page1(emptyList(), null)
        val page = ex.getPage(next)
        Page1(page.items.filterIsInstance<StreamInfoItem>().mapNotNull(::toTrack), remember(ex, page.nextPage))
    }

    private fun remember(ex: SearchExtractor, next: Page?): String? {
        if (next == null || !Page.isValid(next)) return null
        if (pending.size > 20) pending.clear()
        val token = "yts-" + System.nanoTime()
        pending[token] = ex to next
        return token
    }

    private fun toTrack(item: StreamInfoItem): OnlineTrack? {
        val id = item.url.substringAfter("v=", "").take(11).takeIf { it.length == 11 } ?: return null
        if (item.duration <= 0) return null // live streams and premieres aren't songs
        val artist = cleanArtist(item.uploaderName.orEmpty())
        return OnlineTrack(
            videoId = id,
            title = cleanTitle(item.name.orEmpty(), artist),
            artist = artist.ifBlank { "YouTube" },
            album = null,
            durationMs = item.duration * 1000,
            // The video's own thumbnail (largest offered) — per track, never a shared placeholder.
            artworkUrl = item.thumbnails.maxByOrNull { it.width * it.height }?.url,
            isVideo = true,
        )
    }

    /** "Artist - Topic" / "ArtistVEVO" channels are the artist. */
    internal fun cleanArtist(uploader: String): String =
        uploader.removeSuffix(" - Topic").let { if (it.endsWith("VEVO") && it.length > 4) it.dropLast(4) else it }.trim()

    /** "Artist - Song (Official Video)" → "Song"; anything else is kept as YouTube shows it. */
    internal fun cleanTitle(title: String, artist: String): String {
        var t = title.trim()
        if (artist.isNotBlank() && t.startsWith("$artist - ", ignoreCase = true)) t = t.substring(artist.length + 3)
        t = t.replace(Regex("""\s*[(\[](official\s+(music\s+)?(video|audio|hd video|lyric video|visualizer))[)\]]""", RegexOption.IGNORE_CASE), "")
        return t.trim().ifBlank { title }
    }
}
