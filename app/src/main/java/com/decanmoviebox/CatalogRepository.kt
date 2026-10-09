package com.decanmoviebox

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.max

class CatalogRepository(private val tmdbKey: String) {
    fun search(query: String, page: Int = 1): List<Movie> {
        check(tmdbKey.isNotBlank()) {
            "TMDB_API_KEY is missing. Add it as a Gradle property or the TMDB_API_KEY environment variable."
        }

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
            .appendQueryParameter("fl[]", "licenseurl")
            .appendQueryParameter("rows", "12")
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("output", "json")
            .build()
        val docs = getJson(searchUri.toString()).optJSONObject("response")
            ?.optJSONArray("docs") ?: JSONArray()

        val movies = mutableListOf<Movie>()
        for (index in 0 until docs.length()) {
            val doc = docs.optJSONObject(index) ?: continue
            val identifier = doc.optString("identifier").trim()
            val archiveTitle = doc.optString("title").trim()
            val licenseUrl = doc.optString("licenseurl").trim()
            if (identifier.isBlank() || archiveTitle.isBlank() || !hasReuseLicense(licenseUrl)) continue

            val details = getJson("https://archive.org/metadata/${Uri.encode(identifier)}")
            val metadata = details.optJSONObject("metadata") ?: continue
            val metadataLicense = metadata.optString("licenseurl").ifBlank { licenseUrl }
            if (!hasReuseLicense(metadataLicense)) continue

            val title = metadata.optString("title").ifBlank { archiveTitle }
            val year = metadata.optString("year").ifBlank { doc.optString("year") }
            val tmdbMovie = findTmdbMatch(title, year) ?: continue
            val mediaType = tmdbMovie.optString("media_type").ifBlank { "movie" }
            val videoOptions = findMp4s(identifier, details.optJSONArray("files"))
            if (videoOptions.isEmpty()) continue
            val archiveUrl = "https://archive.org/details/${Uri.encode(identifier)}"
            val licenseName = metadataLicense
            val tmdbDate = tmdbMovie.optString(
                if (mediaType == "tv") "first_air_date" else "release_date",
            )

            movies += Movie(
                id = identifier,
                title = tmdbMovie.optString(if (mediaType == "tv") "name" else "title").ifBlank { title },
                year = tmdbDate.take(4).ifBlank { year },
                overview = tmdbMovie.optString("overview"),
                posterUrl = tmdbMovie.optString("poster_path").takeIf(String::isNotBlank)
                    ?.let { "https://image.tmdb.org/t/p/w500$it" },
                releaseDate = tmdbDate,
                archiveUrl = archiveUrl,
                licenseUrl = licenseName,
                videoUrl = videoOptions.first().url,
                tmdbId = tmdbMovie.optInt("id"),
                mediaType = mediaType,
                videoOptions = videoOptions,
            )
        }
        return movies
    }

    private fun findTmdbMatch(title: String, year: String): JSONObject? {
        val uri = Uri.parse("https://api.themoviedb.org/3/search/multi").buildUpon()
            .appendQueryParameter("api_key", tmdbKey)
            .appendQueryParameter("query", title)
            .appendQueryParameter("include_adult", "false")
            .appendQueryParameter("language", "en-US")
            .build()
        val results = getJson(uri.toString()).optJSONArray("results") ?: return null
        val archiveYear = year.take(4).toIntOrNull()
        var best: JSONObject? = null
        var bestScore = 0.0

        for (index in 0 until results.length()) {
            val candidate = results.optJSONObject(index) ?: continue
            val mediaType = candidate.optString("media_type")
            if (mediaType != "movie" && mediaType != "tv") continue
            if (candidate.optBoolean("adult")) continue
            val candidateTitle = candidate.optString(if (mediaType == "tv") "name" else "title")
            val similarity = titleSimilarity(title, candidateTitle)
            val candidateYear = candidate.optString(
                if (mediaType == "tv") "first_air_date" else "release_date",
            ).take(4).toIntOrNull()
            val yearCompatible = archiveYear == null || candidateYear == null ||
                kotlin.math.abs(archiveYear - candidateYear) <= 2
            val score = if (yearCompatible) similarity else similarity * 0.7
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best?.takeIf { bestScore >= 0.84 }
    }

    private fun findMp4s(identifier: String, files: JSONArray?): List<VideoSource> {
        files ?: return emptyList()
        val options = mutableListOf<VideoSource>()
        for (index in 0 until files.length()) {
            val file = files.optJSONObject(index) ?: continue
            val name = file.optString("name")
            val format = file.optString("format")
            if (name.endsWith(".mp4", ignoreCase = true) &&
                (format.isBlank() || format.contains("mpeg4", ignoreCase = true) ||
                    format.contains("h.264", ignoreCase = true))
            ) {
                val height = file.optString("height").toIntOrNull()
                val size = file.optString("size").toLongOrNull()
                val sizeLabel = size?.let { " • ${formatBytes(it)}" }.orEmpty()
                val label = height?.let { "${it}p$sizeLabel" }
                    ?: "${name.substringAfterLast('/').removeSuffix(".mp4")}$sizeLabel"
                options += VideoSource(
                    url = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(name)}",
                    label = label,
                    height = height,
                )
            }
        }
        return options.sortedByDescending { it.height ?: 0 }.take(8)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000 -> "${"%.0f".format(bytes / 1_000_000.0)} MB"
        else -> "${"%.0f".format(bytes / 1_000.0)} KB"
    }

    private fun hasReuseLicense(value: String): Boolean {
        val license = value.trim().lowercase().trimEnd('/')
        if (license.isBlank()) return false
        if (license.contains("creativecommons.org/publicdomain/zero/") ||
            license.contains("creativecommons.org/publicdomain/mark/")
        ) return true

        val licensePattern = Regex(
            """creativecommons\.org/licenses/(by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd)/[0-9.]+""",
        )
        return licensePattern.containsMatchIn(license)
    }

    private fun titleSimilarity(first: String, second: String): Double {
        val left = normalize(first)
        val right = normalize(second)
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / max(left.length, right.length)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "DecanMovieBox/1.0 (Android)")
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
