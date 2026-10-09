package com.decanmoviebox

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.max

class CatalogRepository(
    private val tmdbApiKey: String,
    private val tmdbAccessToken: String = "",
) {
    // Hidden internal Add-on Manifest Endpoints
    private val hiddenAddonEndpoints = listOf(
        "https://v3-cinemeta.strem.fun/manifest.json",
        "https://cyberflix.kables.dev/manifest.json",
        "https://streaming-catalogs.elfhosted.com/manifest.json",
        "https://anime-kitsu.strem.fun/manifest.json",
        "https://torrentio.strem.fun/manifest.json",
        "https://aiostreams.elfhosted.com/manifest.json",
        "https://mediafusion.elfhosted.com/manifest.json",
        "https://comet.elfhosted.com/manifest.json"
    )

    /**
     * Categorized movie/series fetching for vertical & horizontal rows
     */
    fun getCategories(): Map<String, List<Movie>> {
        val categories = mutableMapOf<String, List<Movie>>()
        
        categories["Trending Now"] = fetchTmdbCategory("trending/all/week")
        categories["New Movie Releases"] = fetchTmdbCategory("movie/now_playing")
        categories["Popular TV Series"] = fetchTmdbCategory("tv/popular")
        categories["Horror Night"] = fetchTmdbGenre("movie", 27) // 27 = Horror Genre ID
        categories["Top Anime"] = fetchAnimeCategory()

        return categories
    }

    /**
     * Search across both TMDB catalog and hidden Stremio/Kitsu add-on engines
     */
    fun search(query: String, page: Int = 1): List<Movie> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<Movie>()
        val tmdbResults = searchTmdb(query, page)
        results.addAll(tmdbResults)

        // Fallback to Archive.org if search yields low results
        if (results.size < 5) {
            results.addAll(searchArchiveOrg(query, page))
        }

        return results.distinctBy { it.id }
    }

    /**
     * Fetch direct streams from hidden resolver add-ons for a given media item
     */
    fun getStreamsForMedia(imdbId: String, type: String = "movie", season: Int = 1, episode: Int = 1): List<VideoSource> {
        val streams = mutableListOf<VideoSource>()
        val mediaKey = if (type == "tv") "$imdbId:$season:$episode" else imdbId

        // Query hidden stream resolvers in parallel / sequence
        val resolverUrls = listOf(
            "https://torrentio.strem.fun/stream/$type/$mediaKey.json",
            "https://comet.elfhosted.com/stream/$type/$mediaKey.json",
            "https://mediafusion.elfhosted.com/stream/$type/$mediaKey.json"
        )

        for (endpoint in resolverUrls) {
            try {
                val json = getJson(endpoint)
                val streamArray = json.optJSONArray("streams") ?: continue
                for (i in 0 until streamArray.length()) {
                    val streamObj = streamArray.optJSONObject(i) ?: continue
                    val title = streamObj.optString("title").ifBlank { streamObj.optString("name") }
                    val url = streamObj.optString("url")
                    val infoHash = streamObj.optString("infoHash")

                    val streamUrl = when {
                        url.isNotBlank() -> url
                        infoHash.isNotBlank() -> "magnet:?xt=urn:btih:$infoHash"
                        else -> null
                    } ?: continue

                    streams.add(
                        VideoSource(
                            url = streamUrl,
                            label = title.take(60).replace("\n", " "),
                            height = parseHeightFromTitle(title)
                        )
                    )
                }
            } catch (e: Exception) {
                // Continue checking remaining resolvers silently
                continue
            }
        }

        return streams.sortedByDescending { it.height ?: 0 }
    }

    private fun fetchTmdbCategory(endpoint: String): List<Movie> {
        return try {
            val uri = Uri.parse("https://api.themoviedb.org/3/$endpoint").buildUpon()
                .appendQueryParameter("language", "en-US")
                .apply {
                    if (tmdbAccessToken.isBlank()) appendQueryParameter("api_key", tmdbApiKey)
                }.build()

            val json = getJson(uri.toString(), tmdbAccessToken.takeIf(String::isNotBlank))
            val results = json.optJSONArray("results") ?: JSONArray()
            parseTmdbResults(results)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchTmdbGenre(mediaType: String, genreId: Int): List<Movie> {
        return try {
            val uri = Uri.parse("https://api.themoviedb.org/3/discover/$mediaType").buildUpon()
                .appendQueryParameter("with_genres", genreId.toString())
                .appendQueryParameter("language", "en-US")
                .appendQueryParameter("sort_by", "popularity.desc")
                .apply {
                    if (tmdbAccessToken.isBlank()) appendQueryParameter("api_key", tmdbApiKey)
                }.build()

            val json = getJson(uri.toString(), tmdbAccessToken.takeIf(String::isNotBlank))
            val results = json.optJSONArray("results") ?: JSONArray()
            parseTmdbResults(results, defaultType = mediaType)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchAnimeCategory(): List<Movie> {
        return try {
            val json = getJson("https://anime-kitsu.strem.fun/catalog/anime/kitsu-anime-trending.json")
            val metas = json.optJSONArray("metas") ?: JSONArray()
            val animeList = mutableListOf<Movie>()

            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                animeList.add(
                    Movie(
                        id = item.optString("id"),
                        title = item.optString("name"),
                        year = item.optString("releaseInfo").take(4),
                        overview = item.optString("description"),
                        posterUrl = item.optString("poster"),
                        releaseDate = item.optString("releaseInfo"),
                        archiveUrl = "",
                        licenseUrl = "",
                        videoUrl = "",
                        tmdbId = 0,
                        mediaType = "anime",
                        videoOptions = emptyList()
                    )
                )
            }
            animeList
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchTmdb(query: String, page: Int): List<Movie> {
        return try {
            val uri = Uri.parse("https://api.themoviedb.org/3/search/multi").buildUpon()
                .appendQueryParameter("query", query)
                .appendQueryParameter("page", page.toString())
                .appendQueryParameter("include_adult", "false")
                .appendQueryParameter("language", "en-US")
                .apply {
                    if (tmdbAccessToken.isBlank()) appendQueryParameter("api_key", tmdbApiKey)
                }.build()

            val json = getJson(uri.toString(), tmdbAccessToken.takeIf(String::isNotBlank))
            val results = json.optJSONArray("results") ?: JSONArray()
            parseTmdbResults(results)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTmdbResults(array: JSONArray, defaultType: String = "movie"): List<Movie> {
        val movies = mutableListOf<Movie>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mediaType = obj.optString("media_type").ifBlank { defaultType }
            if (mediaType != "movie" && mediaType != "tv") continue

            val id = obj.optInt("id").toString()
            val title = obj.optString(if (mediaType == "tv") "name" else "title")
            val releaseDate = obj.optString(if (mediaType == "tv") "first_air_date" else "release_date")
            val posterPath = obj.optString("poster_path")

            movies.add(
                Movie(
                    id = id,
                    title = title,
                    year = releaseDate.take(4),
                    overview = obj.optString("overview"),
                    posterUrl = posterPath.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" },
                    releaseDate = releaseDate,
                    archiveUrl = "",
                    licenseUrl = "",
                    videoUrl = "",
                    tmdbId = obj.optInt("id"),
                    mediaType = mediaType,
                    videoOptions = emptyList()
                )
            )
        }
        return movies
    }

    private fun searchArchiveOrg(query: String, page: Int): List<Movie> {
        val safeTerms = query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}'-]"), "") }
            .filter(String::isNotBlank)
        
        val archiveQuery = buildString {
            append("(mediatype:movies OR mediatype:tv)")
            if (safeTerms.isNotEmpty()) {
                append(" AND title:(")
                append(safeTerms.joinToString(" AND ") { "\"$it\"" })
                append(')')
            }
        }

        val searchUri = Uri.parse("https://archive.org/advancedsearch.php").buildUpon()
            .appendQueryParameter("q", archiveQuery)
            .appendQueryParameter("fl[]", "identifier")
            .appendQueryParameter("fl[]", "title")
            .appendQueryParameter("fl[]", "year")
            .appendQueryParameter("rows", "12")
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("output", "json")
            .build()

        val docs = getJson(searchUri.toString()).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        val movies = mutableListOf<Movie>()

        for (index in 0 until docs.length()) {
            val doc = docs.optJSONObject(index) ?: continue
            val identifier = doc.optString("identifier").trim()
            val archiveTitle = doc.optString("title").trim()
            if (identifier.isBlank() || archiveTitle.isBlank()) continue

            movies.add(
                Movie(
                    id = identifier,
                    title = archiveTitle,
                    year = doc.optString("year"),
                    overview = "",
                    posterUrl = null,
                    releaseDate = doc.optString("year"),
                    archiveUrl = "https://archive.org/details/${Uri.encode(identifier)}",
                    licenseUrl = "",
                    videoUrl = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(identifier)}.mp4",
                    tmdbId = 0,
                    mediaType = "movie",
                    videoOptions = emptyList()
                )
            )
        }
        return movies
    }

    private fun parseHeightFromTitle(title: String): Int? {
        return when {
            title.contains("2160p", ignoreCase = true) || title.contains("4K", ignoreCase = true) -> 2160
            title.contains("1080p", ignoreCase = true) -> 1080
            title.contains("720p", ignoreCase = true) -> 720
            title.contains("480p", ignoreCase = true) -> 480
            else -> null
        }
    }

    private fun getJson(address: String, bearerToken: String? = null): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "DecanMovieBox/1.0 (Android)")
        connection.setRequestProperty("Accept", "application/json")
        bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) {
                "Catalog request failed (HTTP $status): ${body.take(250)}"
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
