package com.decanmoviebox

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Metadata comes from TMDB; playable files are only accepted from verified, openly licensed Internet Archive records. */
class CatalogRepository(
    private val tmdbApiKey: String,
    private val tmdbAccessToken: String = "",
) {
    fun getCategories(): Map<String, List<Movie>> {
        val result = linkedMapOf<String, List<Movie>>()
        result["Trending Now"] = fetchTmdbCategory("trending/all/week")
        result["New Movie Releases"] = fetchTmdbCategory("movie/now_playing")
        result["Popular TV Series"] = fetchTmdbCategory("tv/popular")
        result["Horror Night"] = fetchTmdbGenre("movie", 27)
        result["Top Anime"] = fetchAnimeCategory()
        // Always expose a separate watchable catalog. TMDB and Kitsu are metadata providers and
        // do not grant playback rights; this row contains only Archive.org items whose item
        // metadata advertises a recognized public-domain / Creative Commons license and a direct MP4.
        val openFilms = fetchPopularArchiveFilms()
        if (openFilms.isNotEmpty()) result["Watch & Download · Open License"] = openFilms
        if (result.values.all { it.isEmpty() }) {
            val addonFallback = fetchAddonMetadata(query = "", page = 1, maxCatalogsPerAddon = 1)
            if (addonFallback.isNotEmpty()) result["More Catalog Results"] = addonFallback
        }
        return result.filterValues { it.isNotEmpty() }
    }

    fun search(query: String, page: Int = 1): List<Movie> {
        if (query.isBlank()) return emptyList()
        val tmdb = searchTmdb(query, page)
        // Kitsu is a direct, public anime metadata endpoint and does not require a user-supplied URL.
        val anime = searchKitsuAnime(query, page)
        // Search the playable catalog on every page: a successful TMDB response must not hide
        // legal, downloadable copies that exist in the open-media catalog.
        val archive = searchArchiveOrg(query, page)
        val addonFallback = if (tmdb.size + anime.size < 5) {
            fetchAddonMetadata(query, page, maxCatalogsPerAddon = 2)
        } else emptyList()
        val mergedTmdb = attachVerifiedOpenSources(tmdb, archive)
        val mergedAnime = attachVerifiedOpenSources(anime, archive)
        return (mergedTmdb + mergedAnime + addonFallback + archive).distinctBy { "${it.sourceName}:${it.id}" }
    }

    /**
     * Catalog-only Stremio metadata fallback. Manifests are hardcoded in metadataAddons and
     * stream resources are rejected by AddonCatalogRepository. These results never contain
     * playable URLs; only the separately verified open-media catalog can enable playback.
     */
    private fun fetchAddonMetadata(
        query: String,
        page: Int,
        maxCatalogsPerAddon: Int,
    ): List<Movie> {
        val executor = java.util.concurrent.Executors.newFixedThreadPool(metadataAddons.size.coerceAtLeast(1))
        return try {
            val tasks = metadataAddons.map { addon ->
                java.util.concurrent.Callable {
                    runCatching {
                        val addonRepository = AddonCatalogRepository()
                        addonRepository.loadCatalogs(addon)
                            .filter { it.type == "movie" || it.type == "series" }
                            .take(maxCatalogsPerAddon)
                            .flatMap { catalog ->
                                addonRepository.loadItems(catalog, query, page).map { item ->
                                    Movie(
                                        id = "addon-${addon.name.hashCode()}-${item.type}-${item.id}",
                                        title = item.title,
                                        year = item.year,
                                        overview = item.overview,
                                        posterUrl = item.posterUrl,
                                        mediaType = if (item.type == "series") "tv" else item.type,
                                        sourceName = addon.name,
                                    )
                                }
                            }
                    }.getOrDefault(emptyList())
                }
            }
            executor.invokeAll(tasks, 18, java.util.concurrent.TimeUnit.SECONDS)
                .flatMap { future -> runCatching { future.get() }.getOrDefault(emptyList()) }
                .distinctBy { "${it.sourceName}:${it.id}" }
        } catch (_: Exception) {
            emptyList()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun searchKitsuAnime(query: String, page: Int): List<Movie> = try {
        val uri = Uri.parse("https://kitsu.io/api/edge/anime").buildUpon()
            .appendQueryParameter("filter[text]", query.trim())
            .appendQueryParameter("page[limit]", "20")
            .appendQueryParameter("page[offset]", ((page.coerceAtLeast(1) - 1) * 20).toString())
            .build()
        val data = getJson(uri.toString()).optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val attrs = item.optJSONObject("attributes") ?: continue
                val titles = attrs.optJSONObject("titles")
                val title = titles?.optString("en")?.takeIf(String::isNotBlank)
                    ?: titles?.optString("en_jp")?.takeIf(String::isNotBlank)
                    ?: attrs.optString("canonicalTitle")
                if (title.isBlank()) continue
                val poster = attrs.optJSONObject("posterImage")?.optString("medium")
                    ?.takeIf(String::isNotBlank)
                add(Movie(
                    id = "kitsu-${item.optString("id")}",
                    title = title,
                    year = attrs.optString("startDate").take(4),
                    overview = attrs.optString("synopsis"),
                    posterUrl = poster,
                    releaseDate = attrs.optString("startDate"),
                    mediaType = "anime",
                    sourceName = "Kitsu",
                ))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Join a metadata result to an open-media copy only when the normalized title is an exact
     * match and years do not conflict. This deliberately avoids title-only fuzzy stream matching.
     */
    private fun attachVerifiedOpenSources(metadata: List<Movie>, openFilms: List<Movie>): List<Movie> {
        if (metadata.isEmpty() || openFilms.isEmpty()) return metadata
        val index = openFilms.groupBy { normalizeTitle(it.title) }
        return metadata.map { item ->
            if (item.mediaType !in setOf("movie", "tv") || item.mediaType == "tv") return@map item
            val match = index[normalizeTitle(item.title)]
                ?.firstOrNull { open ->
                    item.year.isBlank() || open.year.isBlank() || item.year == open.year
                }
                ?: return@map item
            item.copy(
                archiveUrl = match.archiveUrl,
                licenseUrl = match.licenseUrl,
                videoUrl = match.videoUrl,
                streamFormat = match.streamFormat,
                videoOptions = match.videoOptions,
                sourceName = "TMDB + ${match.sourceName}",
            )
        }
    }

    private fun normalizeTitle(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    /**
     * Resolve a known Internet Archive item only after checking its rights metadata again.
     * The caller must pass an Archive identifier prefixed with "archive:". TMDB IDs are never
     * treated as stream IDs, and this method intentionally does not scrape third-party pirate hosts.
     */
    fun getStreamsForMedia(id: String, type: String = "movie", season: Int = 1, episode: Int = 1): List<VideoSource> {
        if (type != "movie" || season != 1 || episode != 1 || !id.startsWith("archive:")) return emptyList()
        val identifier = id.removePrefix("archive:").trim()
        if (!identifier.matches(Regex("[A-Za-z0-9._-]{1,100}"))) return emptyList()
        return runCatching {
            verifiedArchiveMovie(identifier, identifier, "")?.videoOptions.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun getSeriesSeasons(tmdbId: Int): List<SeriesSeason> {
        if (tmdbId <= 0) return emptyList()
        val uri = tmdbUri("tv/$tmdbId").appendQueryParameter("language", "en-US").build()
        val seasons = getJson(uri.toString(), token()).optJSONArray("seasons") ?: JSONArray()
        return buildList {
            for (i in 0 until seasons.length()) {
                val item = seasons.optJSONObject(i) ?: continue
                val number = item.optInt("season_number", -1)
                if (number <= 0) continue // skip specials from the regular season selector
                add(SeriesSeason(number, item.optString("name").ifBlank { "Season $number" }, item.optInt("episode_count")))
            }
        }
    }

    fun getSeasonEpisodes(tmdbId: Int, seasonNumber: Int): List<SeriesEpisode> {
        if (tmdbId <= 0 || seasonNumber <= 0) return emptyList()
        val uri = tmdbUri("tv/$tmdbId/season/$seasonNumber").appendQueryParameter("language", "en-US").build()
        val episodes = getJson(uri.toString(), token()).optJSONArray("episodes") ?: JSONArray()
        return buildList {
            for (i in 0 until episodes.length()) {
                val item = episodes.optJSONObject(i) ?: continue
                val number = item.optInt("episode_number", -1)
                if (number <= 0) continue
                add(SeriesEpisode(number, item.optString("name").ifBlank { "Episode $number" }, item.optString("air_date"), item.optString("overview")))
            }
        }
    }

    private fun fetchTmdbCategory(endpoint: String): List<Movie> = safeTmdb {
        val uri = tmdbUri(endpoint).appendQueryParameter("language", "en-US").build()
        val defaultType = if (endpoint.startsWith("tv/")) "tv" else "movie"
        parseTmdbResults(getJson(uri.toString(), token()).optJSONArray("results") ?: JSONArray(), defaultType)
    }

    private fun fetchTmdbGenre(mediaType: String, genreId: Int): List<Movie> = safeTmdb {
        val uri = tmdbUri("discover/$mediaType")
            .appendQueryParameter("with_genres", genreId.toString())
            .appendQueryParameter("language", "en-US")
            .appendQueryParameter("sort_by", "popularity.desc").build()
        parseTmdbResults(getJson(uri.toString(), token()).optJSONArray("results") ?: JSONArray(), mediaType)
    }

    private fun searchTmdb(query: String, page: Int): List<Movie> = safeTmdb {
        val uri = tmdbUri("search/multi")
            .appendQueryParameter("query", query.trim())
            .appendQueryParameter("page", page.coerceAtLeast(1).toString())
            .appendQueryParameter("include_adult", "false")
            .appendQueryParameter("language", "en-US").build()
        parseTmdbResults(getJson(uri.toString(), token()).optJSONArray("results") ?: JSONArray())
    }

    private fun safeTmdb(block: () -> List<Movie>): List<Movie> = try { block() } catch (_: Exception) { emptyList() }

    private fun tmdbUri(path: String): Uri.Builder = Uri.parse("https://api.themoviedb.org/3/$path").buildUpon().apply {
        if (tmdbAccessToken.isBlank() && tmdbApiKey.isNotBlank()) appendQueryParameter("api_key", tmdbApiKey)
    }

    private fun token(): String? = tmdbAccessToken.takeIf(String::isNotBlank)

    private fun parseTmdbResults(array: JSONArray, defaultType: String = "movie"): List<Movie> {
        val result = mutableListOf<Movie>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val type = obj.optString("media_type").ifBlank { defaultType }
            if (type !in setOf("movie", "tv")) continue
            val id = obj.optInt("id")
            if (id <= 0) continue
            val title = obj.optString(if (type == "tv") "name" else "title").trim()
            if (title.isBlank()) continue
            val date = obj.optString(if (type == "tv") "first_air_date" else "release_date")
            val poster = obj.optString("poster_path")
            result += Movie(
                id = id.toString(), title = title, year = date.take(4),
                overview = obj.optString("overview"),
                posterUrl = poster.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" },
                releaseDate = date, tmdbId = id, mediaType = type, sourceName = "TMDB",
            )
        }
        return result
    }

    private fun fetchPopularArchiveFilms(): List<Movie> = searchArchiveOrg("", 1)

    /** Search IA, then verify rights and discover the real MP4 files from each item's metadata. */
    private fun searchArchiveOrg(query: String, page: Int): List<Movie> {
        return try {
            val q = buildString {
                append("mediatype:movies AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain* OR rights:\"public domain\")")
                if (query.isNotBlank()) {
                    val phrase = query.trim()
                        .replace(Regex("[\\p{Cntrl}]"), " ")
                        .replace(Regex("[\\p{Punct}]"), " ")
                        .split(Regex("\\s+"))
                        .filter(String::isNotBlank)
                        .take(8)
                        .joinToString(" ")
                    if (phrase.isNotBlank()) append(" AND title:(\"$phrase\")")
                }
            }
            val uri = Uri.parse("https://archive.org/advancedsearch.php").buildUpon()
                .appendQueryParameter("q", q)
                .appendQueryParameter("fl[]", "identifier")
                .appendQueryParameter("fl[]", "title")
                .appendQueryParameter("fl[]", "year")
                .appendQueryParameter("rows", "16")
                .appendQueryParameter("page", page.coerceAtLeast(1).toString())
                .appendQueryParameter("sort[]", "downloads desc")
                .appendQueryParameter("output", "json").build()
            val docs = getJson(uri.toString()).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(docs.length(), 8)) {
                    val doc = docs.optJSONObject(index) ?: continue
                    val id = doc.optString("identifier").trim()
                    val title = doc.optString("title").trim()
                    if (id.isBlank() || title.isBlank()) continue
                    val verified = runCatching { verifiedArchiveMovie(id, title, doc.optString("year")) }.getOrNull()
                    if (verified != null) add(verified)
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun verifiedArchiveMovie(identifier: String, title: String, year: String): Movie? {
        val metadata = getJson("https://archive.org/metadata/${Uri.encode(identifier)}")
        val meta = metadata.optJSONObject("metadata") ?: return null
        val licenseUrl = meta.optString("licenseurl").ifBlank { meta.optString("license_url") }
        val rights = meta.optString("rights")
        if (!isOpenlyLicensed(licenseUrl, rights)) return null
        val files = metadata.optJSONArray("files") ?: return null
        val options = mutableListOf<VideoSource>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            val format = file.optString("format")
            val private = file.optString("private") == "true"
            if (private || !name.endsWith(".mp4", ignoreCase = true)) continue
            if (format.isNotBlank() && !format.contains("mpeg4", true) && !format.contains("h.264", true) && !format.contains("mp4", true)) continue
            val height = parseHeight("${name} ${file.optString("height")}")
            val label = height?.let { "${it}p" } ?: "MP4 · ${file.optString("size").toLongOrNull()?.let { "${it / 1_000_000} MB" } ?: "Standard"}"
            val url = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(name)}"
            options += VideoSource(url, label, height)
        }
        if (options.isEmpty()) return null
        val sorted = options.distinctBy { it.url }.sortedByDescending { it.height ?: 0 }
        return Movie(
            id = identifier, title = title, year = year.take(4), overview = meta.optString("description").take(1200),
            posterUrl = "https://archive.org/services/img/${Uri.encode(identifier)}",
            releaseDate = year, archiveUrl = "https://archive.org/details/${Uri.encode(identifier)}",
            licenseUrl = licenseUrl.ifBlank { rights }, videoUrl = sorted.first().url,
            mediaType = "movie", sourceName = "Internet Archive · license metadata", videoOptions = sorted,
        )
    }

    private fun isOpenlyLicensed(license: String, rights: String): Boolean {
        val value = "$license $rights".lowercase()
        if (value.contains("all rights reserved") || value.contains("copyrighted") || value.contains("permission required")) return false
        return value.contains("creativecommons.org/licenses/by/") ||
            value.contains("creativecommons.org/licenses/by-") ||
            value.contains("creativecommons.org/publicdomain/") ||
            value.contains("creativecommons.org/licenses/zero/") ||
            value.contains("public domain") || value.contains("public-domain") ||
            value.contains("cc0") || value.contains("no known copyright")
    }

    private fun fetchAnimeCategory(): List<Movie> = try {
        val json = getJson("https://kitsu.io/api/edge/trending/anime")
        val data = json.optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val attrs = item.optJSONObject("attributes") ?: continue
                val titles = attrs.optJSONObject("titles")
                val title = titles?.optString("en_jp")?.takeIf(String::isNotBlank)
                    ?: titles?.optString("en")?.takeIf(String::isNotBlank)
                    ?: attrs.optString("canonicalTitle")
                if (title.isBlank()) continue
                val poster = attrs.optJSONObject("posterImage")?.optString("medium")
                add(Movie(
                    id = "kitsu-${item.optString("id")}", title = title,
                    year = attrs.optString("startDate").take(4), overview = attrs.optString("synopsis"),
                    posterUrl = poster?.takeIf(String::isNotBlank), releaseDate = attrs.optString("startDate"),
                    mediaType = "anime", sourceName = "Kitsu metadata",
                ))
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun parseHeight(value: String): Int? = when {
        Regex("(?i)(2160p|4k)").containsMatchIn(value) -> 2160
        Regex("(?i)1080p").containsMatchIn(value) -> 1080
        Regex("(?i)720p").containsMatchIn(value) -> 720
        Regex("(?i)480p").containsMatchIn(value) -> 480
        Regex("(?i)360p").containsMatchIn(value) -> 360
        else -> null
    }

    private fun getJson(address: String, bearerToken: String? = null): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "DecanMovieBox/1.1 (Android; catalog and licensed media)")
        connection.setRequestProperty("Accept", "application/json")
        bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Request failed (HTTP $status). ${body.take(160)}" }
            return JSONObject(body)
        } finally { connection.disconnect() }
    }
}
