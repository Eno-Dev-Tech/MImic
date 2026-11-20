package com.example.mimic.online

import com.example.mimic.data.online.*
import com.example.mimic.online.data.YtMusicRepository
import com.example.mimic.online.data.innertube.Innertube
import com.example.mimic.online.data.innertube.InnertubeParser
import com.example.mimic.online.data.model.HomeShelf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses the existing catalogue/account client. No extra credentials, player or database. */
internal object OnlineDiscovery {
    private val mutex = Mutex()
    private var home: List<HomeShelf>? = null
    private var fetchedAt = 0L
    private var session = -1L

    private suspend fun home(): Result<List<HomeShelf>> = mutex.withLock {
        val current = OnlineMusic.discoverySession
        val time = android.os.SystemClock.elapsedRealtime()
        val cached = home
        if (current == session && cached != null && time - fetchedAt in 0 until 30_000) return@withLock Result.success(cached)
        shelves("FEmusic_home").onSuccess {
            home = it; fetchedAt = time; session = current
        }
    }

    private suspend fun shelves(id: String): Result<List<HomeShelf>> = try {
        val raw = Innertube.browse(id)
        val parsed = InnertubeParser.parseHome(raw).ifEmpty { InnertubeParser.parseHomeContinuation(raw) }
        if (com.example.mimic.BuildConfig.DEBUG) {
            val renderers = linkedSetOf<String>()
            fun shape(node: kotlinx.serialization.json.JsonElement) {
                when (node) {
                    is kotlinx.serialization.json.JsonObject -> { renderers.addAll(node.keys.filter { it.endsWith("Renderer") }); node.values.forEach(::shape) }
                    is kotlinx.serialization.json.JsonArray -> node.forEach(::shape)
                    else -> Unit
                }
            }
            shape(raw)
            android.util.Log.d("MImicDiscovery", "$id shelves=${parsed.size} renderers=$renderers")
        }
        Result.success(parsed)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    suspend fun fetch(feed: DiscoveryFeed, seeds: List<String>, country: DiscoveryCountry): Result<DiscoveryContent> {
        // Public discovery is the default: no login and no Music catalogue availability gate.
        // An already connected account may enrich Worldwide picks; country picks stay public.
        if (country != DiscoveryCountry.WORLDWIDE || !OnlineMusic.youTubeMusicConnected) return publicFeed(feed, seeds, country)
        val catalogue = try {
            Innertube.ensureSessionScope()
            when (feed) {
        DiscoveryFeed.ALBUMS -> home().map { DiscoveryMapper.shelves(it, albumsOnly = true) }
        DiscoveryFeed.NEW_RELEASES -> shelves("FEmusic_new_releases")
            .map { DiscoveryMapper.shelves(it).copy(explanation = "New albums, singles and songs selected by YouTube Music") }
        DiscoveryFeed.RECOMMENDED -> recommendations(seeds)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        if (catalogue.getOrNull()?.isEmpty == false) return catalogue
        return publicFeed(feed, seeds, country)
    }

    private suspend fun publicFeed(feed: DiscoveryFeed, seeds: List<String>, country: DiscoveryCountry): Result<DiscoveryContent> {
        return try {
            val prefix = country.musicQuery
            val fallback = when (feed) {
                DiscoveryFeed.ALBUMS -> DiscoveryContent(collections = YouTubeSearch.suggestedAlbums(prefix), explanation = "${country.label} album-search playlists from YouTube · no account needed", heading = "Suggested albums & playlists")
                DiscoveryFeed.NEW_RELEASES -> DiscoveryContent(tracks = YouTubeSearch.recentMusic(prefix, country.recentQuery), explanation = "${country.label} music search · uploads within 14 days · upload date may differ from original release date", heading = if (country == DiscoveryCountry.WORLDWIDE) "New music · recent uploads" else "${country.label} · recent music")
                DiscoveryFeed.RECOMMENDED -> {
                    val selected = if (country == DiscoveryCountry.WORLDWIDE) seeds.filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.distinct().take(2) else emptyList()
                    val groups = coroutineScope { selected.map { id -> async { try { YouTubeSearch.related(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } } }.awaitAll() }
                    val tracks = DiscoveryMapper.rankRelated(groups, selected.toSet()).ifEmpty { YouTubeSearch.search("$prefix official music".trim()).tracks.take(20) }
                    DiscoveryContent(tracks = tracks, explanation = if (groups.any { it.isNotEmpty() }) "Related YouTube picks based on your remembered tracks · no account needed" else "${country.label} music picks from YouTube search · no account needed")
                }
            }
            Result.success(fallback)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun recommendations(seeds: List<String>): Result<DiscoveryContent> = coroutineScope {
        val selected = seeds.filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.distinct().take(3)
        if (selected.isNotEmpty()) {
            val groups = selected.map { id -> async { YtMusicRepository.radio(id).getOrDefault(emptyList()).map(DiscoveryMapper::track) } }.awaitAll()
            val related = DiscoveryMapper.rankRelated(groups, selected.toSet())
            if (related.isNotEmpty()) return@coroutineScope Result.success(DiscoveryContent(tracks = related, explanation = "Based on your YouTube Music listening and saved tracks"))
        }
        home().map { shelves ->
            val discovery = shelves.filterNot { shelf -> listOf("recent", "history", "listen again").any { shelf.title.contains(it, ignoreCase = true) } }
            DiscoveryMapper.shelves(discovery).copy(collections = emptyList(), explanation = "Selected by YouTube Music")
        }
    }
}
