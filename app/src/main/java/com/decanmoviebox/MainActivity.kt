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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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

private enum class Destination { BROWSE, CATALOGS, SAVED, DOWNLOADS }

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
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var pendingDownload by remember { mutableStateOf<Movie?>(null) }
    var selectedDownloadSource by remember { mutableStateOf<VideoSource?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var playerMovie by remember { mutableStateOf<Movie?>(null) }
    var refreshFavorites by remember { mutableIntStateOf(0) }
    var refreshDownloads by remember { mutableIntStateOf(0) }
    var wifiOnly by remember { mutableStateOf(library.wifiOnlyDownloads()) }

    fun loadMovies(reset: Boolean) {
        if (loading) return
        val requestedPage = if (reset) 1 else page + 1
        loading = true
        error = null
        scope.launch {
            try {
                val results = withContext(Dispatchers.IO) { repository.search(query, requestedPage) }
                movies = if (reset) results else movies + results
                page = requestedPage
            } catch (exception: Exception) {
                error = exception.message ?: "Could not load the catalog."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadMovies(reset = true) }

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
                    selected = destination == Destination.CATALOGS,
                    onClick = { destination = Destination.CATALOGS },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Catalogs and metadata") },
                    label = { Text("Catalogs") },
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
                movies = movies,
                query = query,
                onQueryChange = { query = it },
                onSearch = { loadMovies(reset = true) },
                onBrowseCatalogs = { destination = Destination.CATALOGS },
                onMovieClick = { selectedMovie = it },
                onResume = { library.lastMovie()?.let { playerMovie = it } },
                onLoadMore = { loadMovies(reset = false) },
                loading = loading,
                error = error,
                padding = padding,
            )
            Destination.SAVED -> {
                val favoriteMovies = remember(refreshFavorites) { library.favorites() }
                MovieCollectionScreen(
                    heading = "Your saved movies",
                    movies = favoriteMovies,
                    emptyMessage = "Save a movie from its details to find it here.",
                    onMovieClick = { selectedMovie = it },
                    padding = padding,
                )
            }
            Destination.CATALOGS -> AddonCatalogScreen(
                padding = padding,
                onAuthorizedSource = { selectedMovie = it },
            )
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
            title = { Text("Download video?") },
            text = {
                Text(
                    "Download the selected direct video file for offline viewing? " +
                        if (wifiOnly) "Wi-Fi-only downloads are enabled." else "Mobile data may be used.",
                )
                Spacer(Modifier.height(8.dp))
                if (movie.streamFormat == "hls") {
                    Text("HLS playlists cannot be saved as a single video file. Stream it online instead.")
                } else {
                    Text("Download quality", style = MaterialTheme.typography.labelLarge)
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
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        enqueueDownload(
                            context,
                            library,
                            movie,
                            selectedDownloadSource ?: movie.videoOptions.firstOrNull()
                                ?: VideoSource(movie.videoUrl, "Available MP4"),
                        )
                        downloadError = null
                    } catch (exception: Exception) {
                        downloadError = exception.message ?: "The download could not be started."
                    }
                    pendingDownload = null
                    refreshDownloads++
                }, enabled = movie.streamFormat != "hls") { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text("Cancel") }
            },
        )
    }

    downloadError?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("Download could not start") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun BrowseScreen(
    movies: List<Movie>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBrowseCatalogs: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onResume: () -> Unit,
    onLoadMore: () -> Unit,
    loading: Boolean,
    error: String?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(padding)) {
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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("Search licensed movies and shows") },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
        )
        val lastMovie = remember(context) { LocalLibrary(context).lastMovie() }
        if (lastMovie != null) {
            TextButton(onClick = onResume, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                Text("▶  Continue watching ${lastMovie.title}", color = Color(0xFFF2B84B))
            }
        }
        Text(
            if (query.isBlank()) "Public-domain & licensed picks" else "Search results",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
        )
        when {
            loading && movies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && movies.isEmpty() -> Column {
                ErrorPanel(error)
                OutlinedButton(onClick = onBrowseCatalogs, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Browse metadata catalogs")
                }
            }
            movies.isEmpty() -> Text("No titles matched both the rights check and TMDB.", Modifier.padding(16.dp))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(movies, key = { it.id }) { movie -> MovieCard(movie, onClick = { onMovieClick(movie) }) }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onLoadMore, enabled = !loading) {
                            Text(if (loading) "Loading…" else "Load more")
                        }
                        Spacer(Modifier.height(8.dp))
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
            Text("Movies you download will appear here.", Modifier.padding(20.dp), color = Color.LightGray)
        } else {
            entries.forEach { entry ->
                DownloadRow(entry, onPlay)
            }
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry, onPlay: (DownloadEntry) -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    var status by remember(entry.downloadId) { mutableStateOf(downloadStatus(manager, entry.downloadId)) }
    LaunchedEffect(entry.downloadId) {
        while (true) {
            status = downloadStatus(manager, entry.downloadId)
            kotlinx.coroutines.delay(2_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF201E1A)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.movie.posterUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.movie.title, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
        if (status == "Ready to play") {
            TextButton(onClick = { onPlay(entry) }) { Text("Play") }
        }
    }
}

@Composable
private fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
        shape = RoundedCornerShape(14.dp),
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = "Poster for ${movie.title}",
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).background(Color(0xFF29251E)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(10.dp)) {
            Text(movie.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text(movie.year, color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MovieDetailsDialog(
    movie: Movie,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${movie.title} ${movie.year}".trim()) },
        text = {
            Column {
                movie.posterUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Poster for ${movie.title}",
                        modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(movie.overview.ifBlank { "No description is available." }, Modifier.padding(top = 12.dp))
                Text("${if (movie.mediaType == "tv") "First aired" else "Release date"}: " +
                    movie.releaseDate.ifBlank { "Not listed" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("Source: ${movie.sourceName}", style = MaterialTheme.typography.bodySmall)
                Text("Rights: ${movie.licenseUrl}", style = MaterialTheme.typography.bodySmall)
                if (movie.tmdbId > 0) {
                    Text("TMDB metadata • TMDB ID ${movie.tmdbId}", style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onFavorite) { Text(if (isFavorite) "Remove saved" else "Save") }
                TextButton(onClick = onDownload, enabled = movie.streamFormat != "hls") { Text("Download") }
                Button(onClick = onPlay) { Text("Play") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ErrorPanel(message: String) {
    Surface(
        color = Color(0xFF241B18),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(message, color = Color(0xFFFFB4A9), modifier = Modifier.padding(16.dp))
    }
}

private fun enqueueDownload(
    context: Context,
    library: LocalLibrary,
    movie: Movie,
    source: VideoSource,
) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(source.url))
        .setTitle(movie.title)
        .setDescription("Decan Movie Box • ${movie.sourceName}")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(!library.wifiOnlyDownloads())
        .setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_MOVIES,
            "${movie.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}_${source.height ?: source.url.hashCode()}.${downloadExtension(source.url)}",
        )
    library.addDownload(movie.copy(videoUrl = source.url), manager.enqueue(request))
}

private fun downloadExtension(address: String): String {
    val extension = Uri.parse(address).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
    return extension.takeIf { it in setOf("mp4", "m4v", "webm", "mov") } ?: "mp4"
}

private fun downloadStatus(manager: DownloadManager, id: Long): String {
    val cursor = manager.query(DownloadManager.Query().setFilterById(id))
    cursor.use {
        if (it == null || !it.moveToFirst()) return "Not found"
        return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_SUCCESSFUL -> "Ready to play"
            DownloadManager.STATUS_FAILED -> "Download failed"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_PENDING -> "Waiting"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            else -> "Queued"
        }
    }
}
