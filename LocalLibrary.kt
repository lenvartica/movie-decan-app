package com.decanmoviebox

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject

data class DownloadEntry(val movie: Movie, val downloadId: Long)

class LocalLibrary(private val context: Context) {
    private val preferences = context.getSharedPreferences("decan-library", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Start a background file download using Android DownloadManager
     */
    fun startDownload(movie: Movie, targetUrl: String? = null): Long {
        val downloadUrl = targetUrl ?: movie.videoUrl
        val uri = runCatching { Uri.parse(downloadUrl) }.getOrNull() ?: return -1L
        // Only verified, direct Internet Archive downloads are supported by this app.
        if (uri.scheme != "https" || (uri.host != "archive.org" && uri.host?.endsWith(".archive.org") != true) || uri.path?.contains("/download/") != true) return -1L

        val cleanTitle = movie.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(90).ifBlank { "movie" }
        val fileName = "${cleanTitle}_${movie.id.hashCode().toUInt().toString(16)}.mp4"

        val request = DownloadManager.Request(uri)
            .setTitle(movie.title)
            .setDescription("Downloading ${movie.title} on Decan Movie...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "DecanMovie/$fileName")
            .setAllowedOverMetered(!wifiOnlyDownloads())
            .setAllowedOverRoaming(false)

        val downloadId = downloadManager.enqueue(request)
        addDownload(movie, downloadId)
        return downloadId
    }

    /**
     * Check current download status and progress percentage
     */
    fun getDownloadProgress(downloadId: Long): Int {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            if (bytesDownloadedIdx != -1 && bytesTotalIdx != -1) {
                val downloaded = cursor.getLong(bytesDownloadedIdx)
                val total = cursor.getLong(bytesTotalIdx)
                cursor.close()
                if (total > 0) {
                    return ((downloaded * 100) / total).toInt()
                }
            }
            cursor.close()
        }
        return 0
    }

    /**
     * Cancel and remove a download task
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        removeDownload(downloadId)
    }

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

    fun removeDownload(downloadId: Long) {
        val entries = preferences.getStringSet("downloads", emptySet()).orEmpty().toMutableSet()
        entries.removeAll {
            runCatching { JSONObject(it).getLong("downloadId") == downloadId }.getOrDefault(false)
        }
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
        put("voteAverage", movie.voteAverage)
        put("streamFormat", movie.streamFormat)
        put("videoOptions", JSONArray().apply {
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
            sourceName = json.optString("sourceName", "Decan Engine"),
            voteAverage = json.optDouble("voteAverage", 0.0),
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
