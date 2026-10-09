package com.decanmoviebox

import android.content.Context
import org.json.JSONObject

data class DownloadEntry(val movie: Movie, val downloadId: Long)

class LocalLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("decan-library", Context.MODE_PRIVATE)

    fun favorites(): List<Movie> = preferences.getStringSet("favorites", emptySet())
        .orEmpty()
        .mapNotNull(::decodeMovie)

    fun isFavorite(movie: Movie): Boolean = preferences.getStringSet("favorite_ids", emptySet())
        .orEmpty().contains(movie.id)

    fun toggleFavorite(movie: Movie): Boolean {
        val ids = preferences.getStringSet("favorite_ids", emptySet()).orEmpty().toMutableSet()
        val entries = preferences.getStringSet("favorites", emptySet()).orEmpty().toMutableSet()
        val wasFavorite = ids.remove(movie.id)
        if (wasFavorite) {
            entries.removeAll { decodeMovie(it)?.id == movie.id }
        } else {
            ids.add(movie.id)
            entries.removeAll { decodeMovie(it)?.id == movie.id }
            entries.add(encodeMovie(movie))
        }
        preferences.edit().putStringSet("favorite_ids", ids).putStringSet("favorites", entries).apply()
        return !wasFavorite
    }

    fun saveProgress(movie: Movie, positionMs: Long) {
        preferences.edit()
            .putString("last_movie", encodeMovie(movie))
            .putLong("position_${movie.id}", positionMs)
            .apply()
    }

    fun lastMovie(): Movie? = preferences.getString("last_movie", null)?.let(::decodeMovie)

    fun progress(movie: Movie): Long = preferences.getLong("position_${movie.id}", 0L)

    fun downloads(): List<DownloadEntry> = preferences.getStringSet("downloads", emptySet())
        .orEmpty()
        .mapNotNull { value ->
            runCatching {
                val json = JSONObject(value)
                val movie = decodeMovie(json.getJSONObject("movie").toString()) ?: return@runCatching null
                DownloadEntry(movie, json.getLong("downloadId"))
            }.getOrNull()
        }

    fun addDownload(movie: Movie, downloadId: Long) {
        val entries = preferences.getStringSet("downloads", emptySet()).orEmpty().toMutableSet()
        entries.removeAll {
            runCatching { JSONObject(it).getJSONObject("movie").optString("id") == movie.id }.getOrDefault(false)
        }
        entries.add(JSONObject().put("movie", JSONObject(encodeMovie(movie))).put("downloadId", downloadId).toString())
        preferences.edit().putStringSet("downloads", entries).apply()
    }

    fun wifiOnlyDownloads(): Boolean = preferences.getBoolean("wifi_only", false)

    fun setWifiOnlyDownloads(enabled: Boolean) {
        preferences.edit().putBoolean("wifi_only", enabled).apply()
    }

    private fun encodeMovie(movie: Movie): String = JSONObject().apply {
        put("id", movie.id)
        put("title", movie.title)
        put("year", movie.year)
        put("overview", movie.overview)
        put("posterUrl", movie.posterUrl)
        put("releaseDate", movie.releaseDate)
        put("archiveUrl", movie.archiveUrl)
        put("licenseUrl", movie.licenseUrl)
        put("videoUrl", movie.videoUrl)
        put("tmdbId", movie.tmdbId)
        put("mediaType", movie.mediaType)
        put("sourceName", movie.sourceName)
        put("streamFormat", movie.streamFormat)
        put("videoOptions", org.json.JSONArray().apply {
            movie.videoOptions.forEach { source ->
                put(JSONObject().put("url", source.url).put("label", source.label).put("height", source.height))
            }
        })
    }.toString()

    private fun decodeMovie(value: String): Movie? = runCatching {
        val json = JSONObject(value)
        Movie(
            id = json.getString("id"),
            title = json.getString("title"),
            year = json.optString("year"),
            overview = json.optString("overview"),
            posterUrl = json.optString("posterUrl").takeIf(String::isNotBlank),
            releaseDate = json.optString("releaseDate"),
            archiveUrl = json.getString("archiveUrl"),
            licenseUrl = json.getString("licenseUrl"),
            videoUrl = json.getString("videoUrl"),
            tmdbId = json.optInt("tmdbId"),
            mediaType = json.optString("mediaType", "movie"),
            sourceName = json.optString("sourceName", "Internet Archive"),
            streamFormat = json.optString("streamFormat", "progressive"),
            videoOptions = json.optJSONArray("videoOptions")?.let { options ->
                (0 until options.length()).mapNotNull { index ->
                    val option = options.optJSONObject(index) ?: return@mapNotNull null
                    VideoSource(
                        url = option.optString("url"),
                        label = option.optString("label"),
                        height = option.optInt("height").takeIf { option.has("height") },
                    )
                }
            }.orEmpty(),
        )
    }.getOrNull()
}
