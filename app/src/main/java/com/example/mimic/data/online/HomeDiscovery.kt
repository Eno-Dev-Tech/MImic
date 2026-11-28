package com.example.mimic.data.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class DiscoveryFeed { RECOMMENDED, ALBUMS, NEW_RELEASES }

/** A music-search preference, never inferred from IP, locale or account. */
enum class DiscoveryCountry(val label: String, val musicQuery: String, val recentQuery: String? = null) {
    WORLDWIDE("Worldwide", ""),
    UK("UK", "UK music"),
    USA("USA", "American music", "new music USA official video"),
    ALBANIA("Albania", "muzik shqip", "muzik shqip official video"),
}

data class DiscoveryContent(
    val tracks: List<OnlineTrack> = emptyList(),
    val collections: List<OnlineCollection> = emptyList(),
    val explanation: String = "From YouTube Music",
    val heading: String? = null,
) {
    val isEmpty get() = tracks.isEmpty() && collections.isEmpty()
}

data class DiscoveryShelfState(
    val content: DiscoveryContent? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val updatedAt: Long = 0,
    val attemptedAt: Long? = null,
)

/** Metadata only, held in this session. Failures keep the last successful row.
 * One in-flight lookup per row; ordinary navigation does not repeatedly call the service.
 */
class HomeDiscoveryStore(
    private val fetch: suspend (DiscoveryFeed, List<String>) -> Result<DiscoveryContent>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(DiscoveryFeed.entries.associateWith { DiscoveryShelfState() })
    val state = mutable.asStateFlow()
    private val locks = DiscoveryFeed.entries.associateWith { Mutex() }
    private var generation = 0

    fun clear() {
        generation++
        mutable.value = DiscoveryFeed.entries.associateWith { DiscoveryShelfState() }
    }

    suspend fun load(feed: DiscoveryFeed, seeds: List<String>, force: Boolean = false) {
        val ticket = generation
        locks.getValue(feed).withLock {
            if (ticket != generation) return
            val previous = mutable.value.getValue(feed)
            val age = now() - (previous.attemptedAt ?: Long.MIN_VALUE)
            if (!force && previous.attemptedAt != null && age >= 0 && age < if (previous.failed) 60_000L else 30 * 60_000L) return
            mutable.update { it + (feed to previous.copy(loading = true)) }
            val result = try {
                withTimeout(20_000) { withContext(Dispatchers.IO) { fetch(feed, seeds) } }
            } catch (e: TimeoutCancellationException) {
                Result.failure(e)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            finally {
                if (ticket == generation) mutable.update { it + (feed to it.getValue(feed).copy(loading = false)) }
            }
            currentCoroutineContext().ensureActive()
            if (ticket != generation) return
            val time = now()
            mutable.update { states -> states + (feed to result.fold(
                onSuccess = { content ->
                    if (content.isEmpty && previous.content?.isEmpty == false) previous.copy(loading = false, failed = true, attemptedAt = time)
                    else DiscoveryShelfState(content, updatedAt = time, attemptedAt = time)
                },
                onFailure = { previous.copy(loading = false, failed = true, attemptedAt = time) },
            )) }
        }
    }
}
