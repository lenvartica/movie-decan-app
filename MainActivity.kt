package com.decanmoviebox

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination { BROWSE, SAVED, DOWNLOADS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF2B84B),
                    onPrimary = Color(0xFF1B160D),
                    secondary = Color(0xFFD6A748),
                    background = Color(0xFF11100E),
                    surface = Color(0xFF1B1916),
                    onSurface = Color(0xFFF5F1E8),
                ),
            ) {
                DecanMovieBoxApp()
            }
        }
    }
}

@Composable
private fun DecanMovieBoxApp() {
    val context = LocalContext.current
    val library = remember { LocalLibrary(context) }
    val repository = remember {
        CatalogRepository(
            tmdbApiKey = BuildConfig.TMDB_API_KEY,
            tmdbAccessToken = BuildConfig.TMDB_ACCESS_TOKEN,
        )
    }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(Destination.BROWSE) }
    
    var categories by remember { mutableStateOf<Map<String, List<Movie>>>(emptyMap()) }
    var searchResults by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var fetchingStreams by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var pendingDownload by remember { mutableStateOf<Movie?>(null) }
    var selectedDownloadSource by remember { mutableStateOf<VideoSource?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var playerMovie by remember { mutableStateOf<Movie?>(null) }
    
    var refreshFavorites by remember { mutableIntStateOf(0) }
    var refreshDownloads by remember { mutableIntStateOf(0) }
    var wifiOnly by remember { mutableStateOf(library.wifiOnlyDownloads()) }

    fun loadHomeCategories() {
        loading = true
        scope.launch {
            try {
                categories = withContext(Dispatchers.IO) { repository.getCategories() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load movie categories."
            } finally {
                loading = false
            }
        }
    }

    fun performSearch() {
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }
        loading = true
        scope.launch {
            try {
                searchResults = withContext(Dispatchers.IO) { repository.search(query) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun loadMovieStreams(movie: Movie) {
        fetchingStreams = true
        selectedMovie = movie
        scope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    repository.getStreamsForMedia(movie.id, movie.mediaType)
                }
                if (streams.isNotEmpty()) {
                    selectedMovie = movie.copy(
                        videoUrl = streams.first().url,
                        videoOptions = streams
                    )
                }
            } catch (_: Exception) {
            } finally {
                fetchingStreams = false
            }
        }
    }

    LaunchedEffect(Unit) { loadHomeCategories() }

    playerMovie?.let { movie ->
        PlayerScreen(movie = movie, library = library, onBack = { playerMovie = null })
        return
    }

    Scaffold(
        containerColor = Color(0xFF11100E),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1B1916)) {
                NavigationBarItem(
                    selected = destination == Destination.BROWSE,
                    onClick = { destination = Destination.BROWSE },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Browse") },
                    label = { Text("Browse") },
                )
                NavigationBarItem(
                    selected = destination == Destination.SAVED,
                    onClick = {
                        destination = Destination.SAVED
                        refreshFavorites++
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Saved") },
                    label = { Text("Saved") },
                )
                NavigationBarItem(
                    selected = destination == Destination.DOWNLOADS,
                    onClick = {
                        destination = Destination.DOWNLOADS
                        refreshDownloads++
                    },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                    label = { Text("Downloads") },
                )
            }
        },
    ) { padding ->
        when (destination) {
            Destination.BROWSE -> BrowseScreen(
                categories = categories,
                searchResults = searchResults,
                query = query,
                onQueryChange = { 
                    query = it 
                    if (it.isBlank()) searchResults = emptyList()
                },
                onSearch = { performSearch() },
                onMovieClick = { loadMovieStreams(it) },
                onResume = { library.lastMovie()?.let { playerMovie = it } },
                loading = loading,
                error = error,
                padding = padding,
            )
            Destination.SAVED -> {
                val favoriteMovies = remember(refreshFavorites) { library.favorites() }
                MovieCollectionScreen(
                    heading = "Your Saved Movies",
                    movies = favoriteMovies,
                    emptyMessage = "Save a movie from details to find it here.",
                    onMovieClick = { loadMovieStreams(it) },
                    padding = padding,
                )
            }
            Destination.DOWNLOADS -> {
                val entries = remember(refreshDownloads) { library.downloads() }
                DownloadsScreen(
                    entries = entries,
                    wifiOnly = wifiOnly,
                    onWifiOnlyChange = {
                        wifiOnly = it
                        library.setWifiOnlyDownloads(it)
                    },
                    onPlay = { entry ->
                        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val downloadedUri = manager.getUriForDownloadedFile(entry.downloadId)
                        if (downloadedUri != null) playerMovie = entry.movie.copy(videoUrl = downloadedUri.toString())
                    },
                    padding = padding,
                )
            }
        }
    }

    selectedMovie?.let { movie ->
        MovieDetailsDialog(
            movie = movie,
            isFavorite = library.isFavorite(movie),
            isFetchingStreams = fetchingStreams,
            onDismiss = { selectedMovie = null },
            onPlay = {
                playerMovie = movie
                selectedMovie = null
            },
            onFavorite = {
                library.toggleFavorite(movie)
                refreshFavorites++
            },
            onDownload = {
                pendingDownload = movie
                selectedDownloadSource = movie.videoOptions.firstOrNull()
            },
        )
    }

    pendingDownload?.let { movie ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("Download Movie") },
            text = {
                Column {
                    Text(
                        if (wifiOnly) "Wi-Fi-only downloads enabled." else "Mobile data may be used.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Select Stream Quality", style = MaterialTheme.typography.labelLarge)
                    if (movie.videoOptions.isEmpty()) {
                        Text("Default progressive source", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        movie.videoOptions.forEach { source ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedDownloadSource == source,
                                    onClick = { selectedDownloadSource = source },
                                )
                                Text(source.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        enqueueDownload(
                            context,
                            library,
                            movie,
                            selectedDownloadSource ?: movie.videoOptions.firstOrNull()
                                ?: VideoSource(movie.videoUrl, "Standard Stream"),
                        )
                        downloadError = null
                    } catch (exception: Exception) {
                        downloadError = exception.message ?: "Download failed to start."
                    }
                    pendingDownload = null
                    refreshDownloads++
                }) { Text("Start Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text("Cancel") }
            },
        )
    }

    downloadError?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("Download Failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun BrowseScreen(
    categories: Map<String, List<Movie>>,
    searchResults: List<Movie>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onResume: () -> Unit,
    loading: Boolean,
    error: String?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item {
            // App Header Branding
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2B84B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("DM", color = Color(0xFF171411), fontWeight = FontWeight.Black)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("DECAN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("MOVIE BOX", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2B84B))
                }
            }

            // Search Field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                label = { Text("Search Movies, Series & Anime") },
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
            )

            val lastMovie = remember(context) { LocalLibrary(context).lastMovie() }
            if (lastMovie != null && query.isBlank()) {
                TextButton(onClick = onResume, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                    Text("▶ Continue watching ${lastMovie.title}", color = Color(0xFFF2B84B))
                }
            }
        }

        // Search Results Section
        if (query.isNotBlank()) {
            item {
                Text(
                    "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
                )
            }
            if (loading && searchResults.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults) { movie ->
                            MovieCard(movie, onClick = { onMovieClick(movie) })
                        }
                    }
                }
            }
        } else {
            // Multi-Directional Category Layout (Vertical column of horizontal rows)
            if (loading && categories.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                categories.forEach { (categoryName, movieList) ->
                    if (movieList.isNotEmpty()) {
                        item {
                            Text(
                                categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(movieList) { movie ->
                                    MovieCard(movie, onClick = { onMovieClick(movie) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCollectionScreen(
    heading: String,
    movies: List<Movie>,
    emptyMessage: String,
    onMovieClick: (Movie) -> Unit,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp))
        if (movies.isEmpty()) {
            Text(emptyMessage, color = Color.LightGray, modifier = Modifier.padding(horizontal = 20.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(movies, key = { it.id }) { movie -> MovieCard(movie, onClick = { onMovieClick(movie) }) }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    entries: List<DownloadEntry>,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onPlay: (DownloadEntry) -> Unit,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only")
                Text("Pause downloads on mobile data", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
            Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
        }
        if (entries.isEmpty()) {
            Text("Movies you download will appear here.", Modifier
