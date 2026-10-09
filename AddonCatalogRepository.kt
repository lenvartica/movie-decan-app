package com.decanmoviebox

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MetadataAddon(
    val name: String,
    val manifestUrl: String,
)

data class AddonCatalog(
    val addon: MetadataAddon,
    val type: String,
    val id: String,
    val name: String,
    val supportsSearch: Boolean,
)

data class AddonCatalogItem(
    val id: String,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String?,
    val sourceName: String,
)

class AddonCatalogRepository {
    fun loadCatalogs(addon: MetadataAddon): List<AddonCatalog> {
        val manifest = getJson(addon.manifestUrl)
        check(!manifest.optBoolean("provides_stream") && !manifestHasStreamResource(manifest)) {
            "${addon.name} declares stream support and is disabled for safety."
        }
        val catalogs = manifest.optJSONArray("catalogs")
            ?: error("${addon.name} did not provide a catalog list.")
        val results = mutableListOf<AddonCatalog>()

        for (index in 0 until catalogs.length()) {
            val catalog = catalogs.optJSONObject(index) ?: continue
            val type = catalog.optString("type")
            val id = catalog.optString("id")
            if (type !in setOf("movie", "series") || id.isBlank()) continue
            results += AddonCatalog(
                addon = addon,
                type = type,
                id = id,
                name = catalog.optString("name").ifBlank { id },
                supportsSearch = catalogSupportsSearch(catalog),
            )
        }
        check(results.isNotEmpty()) { "${addon.name} has no movie or series catalogs." }
        return results
    }

    fun loadItems(catalog: AddonCatalog, query: String, page: Int): List<AddonCatalogItem> {
        val base = catalog.addon.manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
        val path = buildString {
            append("catalog/${Uri.encode(catalog.type)}/${Uri.encode(catalog.id)}")
            val extras = mutableListOf<String>()
            if (query.isNotBlank() && catalog.supportsSearch) {
                extras += "search=${Uri.encode(query.trim())}"
            }
            if (page > 1) extras += "skip=${(page - 1) * 20}"
            if (extras.isNotEmpty()) append("/${extras.joinToString("&")}")
            append(".json")
        }
        val uri = Uri.parse(base).buildUpon().appendEncodedPath(path).build()
        val metas = getJson(uri.toString()).optJSONArray("metas") ?: JSONArray()
        return (0 until metas.length()).mapNotNull { index ->
            metas.optJSONObject(index)?.toCatalogItem(catalog.addon.name)
        }.let { items ->
            if (query.isNotBlank() && !catalog.supportsSearch) {
                items.filter { it.title.contains(query.trim(), ignoreCase = true) }
            } else {
                items
            }
        }
    }

    private fun JSONObject.toCatalogItem(sourceName: String): AddonCatalogItem? {
        val id = optString("id").trim()
        val title = optString("name").trim()
        if (id.isBlank() || title.isBlank()) return null
        val releaseInfo = optString("releaseInfo")
        return AddonCatalogItem(
            id = id,
            type = optString("type").ifBlank { "movie" },
            title = title,
            year = releaseInfo.take(4).takeIf { it.all(Char::isDigit) }.orEmpty(),
            overview = optString("description"),
            posterUrl = optString("poster").takeIf(String::isNotBlank),
            sourceName = sourceName,
        )
    }

    private fun catalogSupportsSearch(catalog: JSONObject): Boolean {
        val extras = catalog.optJSONArray("extra") ?: return false
        return (0 until extras.length()).any { index ->
            when (val extra = extras.opt(index)) {
                is JSONObject -> extra.optString("name") == "search"
                is String -> extra == "search"
                else -> false
            }
        }
    }

    private fun manifestHasStreamResource(manifest: JSONObject): Boolean {
        val resources = manifest.optJSONArray("resources") ?: return false
        return (0 until resources.length()).any { index ->
            val resource = resources.opt(index)
            when (resource) {
                is JSONObject -> resource.optString("name") == "stream"
                is String -> resource == "stream"
                else -> false
            }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "DecanMovieBox/1.0 (Android; catalog-only)")
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) {
                "Catalog request failed (HTTP $status): ${body.take(200)}"
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}

val metadataAddons = listOf(
    MetadataAddon("Cinemeta", "https://v3-cinemeta.strem.io/manifest.json"),
    MetadataAddon(
        "Streaming Catalogs",
        "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json",
    ),
    MetadataAddon(
        "The Movie Database Addon",
        "https://94c8cb9f702d-tmdb-addon.baby-beamup.club/manifest.json",
    ),
    MetadataAddon(
        "TOP Streaming",
        "https://top-streaming.stream/username=temporary_username/manifest.json",
    ),
)
