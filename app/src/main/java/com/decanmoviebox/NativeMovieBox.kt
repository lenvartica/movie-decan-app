package com.decanmoviebox

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** JNI entry points backed by the MovieBox-TUI provider engine. The native library is built by CI. */
internal object NativeMovieBox {
    val available: Boolean = runCatching { System.loadLibrary("decanmovie_bridge") }.isSuccess

    external fun searchNative(query: String, provider: String, page: Int): String
    external fun streamsNative(provider: String, id: String, season: Int, episode: Int): String
}

class NativeProviderRepository {
    private val providers = listOf("moviebox", "fourkhdhub", "dramachi")

    fun search(query: String, page: Int = 1): List<Movie> {
        if (!NativeMovieBox.available || query.isBlank()) return emptyList()
        val futures = providers.map { provider ->
            CompletableFuture.supplyAsync { searchProvider(query, provider, page) }
        }
        runCatching { CompletableFuture.allOf(*futures.toTypedArray()).get(18, TimeUnit.SECONDS) }
        return futures.flatMap { future -> runCatching { future.getNow(emptyList()) }.getOrDefault(emptyList()) }
            .distinctBy { "${it.sourceName}:${it.id}" }
    }

    private fun searchProvider(query: String, provider: String, page: Int): List<Movie> {
        if (!NativeMovieBox.available) return emptyList()
        return try {
            val response = JSONTokener(NativeMovieBox.searchNative(query, provider, page)).nextValue()
            if (response is JSONObject && response.has("error")) return emptyList()
            val array = response as? JSONArray ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val title = item.optString("title").trim()
                    if (id.isBlank() || title.isBlank()) continue
                    add(Movie(
                        id = id,
                        title = title,
                        year = item.optString("year"),
                        posterUrl = item.optString("posterUrl").takeIf(String::isNotBlank),
                        mediaType = item.optString("mediaType", "movie"),
                        sourceName = item.optString("sourceName", provider),
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun streams(movie: Movie, season: Int = 0, episode: Int = 0): Movie? {
        if (!NativeMovieBox.available) return null
        val knownProvider = providerId(movie.sourceName)
        if (knownProvider != null) {
            resolve(movie, knownProvider, movie.id, season, episode)?.let { return it }
        }

        // TMDB items have different IDs from stream providers. Search by title and require a very
        // close normalized title match before resolving; never pass a TMDB ID to a provider.
        val wanted = normalize(movie.title)
        val candidates = search(movie.title).map { it to titleDistance(wanted, normalize(it.title)) }
            .filter { (candidate, distance) ->
                distance <= 0.12 && (movie.year.isBlank() || candidate.year.isBlank() || movie.year == candidate.year)
            }
            .sortedBy { it.second }
        for ((candidate, _) in candidates) {
            val provider = providerId(candidate.sourceName) ?: continue
            resolve(movie, provider, candidate.id, season, episode)?.let { return it }
        }
        return null
    }

    private fun resolve(movie: Movie, provider: String, providerId: String, season: Int, episode: Int): Movie? {
        return try {
            val response = JSONTokener(NativeMovieBox.streamsNative(provider, providerId, season, episode)).nextValue()
            if (response is JSONObject && response.has("error")) return null
            val array = response as? JSONArray ?: return null
            val options = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url").trim()
                    if (!url.startsWith("https://")) continue
                    val headerObject = item.optJSONObject("headers")
                    val headers = buildMap {
                        if (headerObject != null) {
                            val keys = headerObject.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, headerObject.optString(key))
                            }
                        }
                    }
                    add(VideoSource(url, item.optString("label").ifBlank { "Auto" }, item.optInt("height").takeIf { it > 0 }, headers))
                }
            }.distinctBy { it.url }
            val first = options.firstOrNull() ?: return null
            movie.copy(videoUrl = first.url, videoOptions = options, sourceName = "${providerLabel(provider)} · streaming provider")
        } catch (_: Exception) { null }
    }

    private fun providerId(name: String): String? {
        val value = name.lowercase()
        return providers.firstOrNull { value.contains(it) || (it == "fourkhdhub" && value.contains("4khdhub")) }
    }

    private fun providerLabel(provider: String): String = when (provider) {
        "fourkhdhub" -> "4KHDHub"
        "dramachi" -> "Dramachi"
        else -> "MovieBox"
    }

    private fun normalize(value: String) = value.lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    private fun titleDistance(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 1.0
        if (a == b) return 0.0
        val left = a.split(" ").toSet(); val right = b.split(" ").toSet()
        val union = left.union(right).size.toDouble()
        return if (union == 0.0) 1.0 else 1.0 - left.intersect(right).size / union
    }
}
