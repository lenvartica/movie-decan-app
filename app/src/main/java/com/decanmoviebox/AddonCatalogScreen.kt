package com.decanmoviebox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CatalogSourceInfo(val name: String, val url: String, val description: String)

@Composable
fun AddonCatalogScreen(
    padding: PaddingValues,
    repository: CatalogRepository,
    onMovieClick: (Movie) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources = remember {
        listOf(
            CatalogSourceInfo("TMDB", "https://api.themoviedb.org/3/", "Movies, series, posters, descriptions, seasons and episodes. Requires your own TMDB API credential."),
            CatalogSourceInfo("Kitsu Anime", "https://kitsu.io/api/edge/trending/anime", "Anime discovery and metadata. Public endpoint; no app-side URL setup required."),
            CatalogSourceInfo("Internet Archive", "https://archive.org/advancedsearch.php", "Searches open-license/public-domain records and checks for compatible MP4 files before enabling playback."),
        )
    }
    var categories by remember { mutableStateOf<Map<String, List<Movie>>>(emptyMap()) }
    var results by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadCatalogs() {
        loading = true
        error = null
        scope.launch {
            try {
                categories = withContext(Dispatchers.IO) { repository.getCategories() }
                if (categories.isEmpty()) error = "Catalog providers did not return titles. Check your connection and retry."
            } catch (e: Exception) {
                error = e.message ?: "Catalog providers are temporarily unavailable."
            } finally {
                loading = false
            }
        }
    }

    fun searchCatalogs() {
        if (query.isBlank()) {
            results = emptyList()
            return
        }
        searching = true
        error = null
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { repository.search(query.trim()) }
                if (results.isEmpty()) error = "No matching titles were found in the available sources. Try a shorter title."
            } catch (e: Exception) {
                error = e.message ?: "Search is temporarily unavailable."
            } finally {
                searching = false
            }
        }
    }

    LaunchedEffect(Unit) { loadCatalogs() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Catalogs & Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Sources are built in. You do not need to add a URL. Catalog metadata is separate from video playback; only rights-checked video files can be played or downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    singleLine = true,
                    label = { Text("Search movies, series or anime") },
                    trailingIcon = {
                        IconButton(onClick = { searchCatalogs() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search sources")
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { searchCatalogs() }, enabled = query.isNotBlank() && !searching) {
                        Text(if (searching) "Searching…" else "Search")
                    }
                    TextButton(onClick = { query = ""; results = emptyList(); loadCatalogs() }) {
                        Text("Refresh catalogs")
                    }
                }
            }
        }

        item {
            Text(
                "Connected data sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        items(sources) { source ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    Text(source.name, fontWeight = FontWeight.Bold, color = Color(0xFFF2B84B))
                    Text(source.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        source.url,
                        color = Color(0xFFB8C9FF),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 5.dp).clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                            }
                        },
                    )
                }
            }
        }

        if (error != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(error.orEmpty(), color = Color(0xFFFFD6A5))
                    TextButton(onClick = { if (query.isNotBlank()) searchCatalogs() else loadCatalogs() }) { Text("Retry") }
                }
            }
        }

        if (query.isNotBlank()) {
            item {
                Text("Search results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            if (searching && results.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (results.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results, key = { "${it.sourceName}:${it.id}" }) { movie ->
                            CatalogMovieCard(movie) { onMovieClick(movie) }
                        }
                    }
                }
            }
        } else if (loading && categories.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            categories.forEach { (heading, movies) ->
                if (movies.isNotEmpty()) {
                    item {
                        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp))
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(movies, key = { "${it.sourceName}:${it.id}" }) { movie ->
                                CatalogMovieCard(movie) { onMovieClick(movie) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMovieCard(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(142.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
        shape = RoundedCornerShape(13.dp),
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = "Poster for ${movie.title}",
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).background(Color(0xFF29251E)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(9.dp)) {
            Text(movie.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                listOf(movie.year, when (movie.mediaType) { "tv" -> "Series"; "anime" -> "Anime"; else -> "Movie" })
                    .filter(String::isNotBlank).joinToString(" · "),
                color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelSmall,
            )
            if (movie.videoUrl.isBlank()) Text("Info only", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
        }
    }
}
