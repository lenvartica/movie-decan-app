package com.decanmoviebox

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.window.DialogProperties
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
    val termsPrefs = remember { context.getSharedPreferences("decan-legal", Context.MODE_PRIVATE) }
    var termsAccepted by remember { mutableStateOf(termsPrefs.getBoolean("terms_v1_accepted", false)) }
    if (!termsAccepted) {
        TermsAcceptanceScreen(
            onAccept = {
                termsPrefs.edit().putBoolean("terms_v1_accepted", true).apply()
                termsAccepted = true
            }
        )
        return
    }
    DecanMovieBoxAppContent()
}

@Composable
private fun TermsAcceptanceScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF11100E)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("DECAN MOVIE", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF2B84B), fontWeight = FontWeight.Bold)
            Text("Terms and Conditions", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Please read these terms before using Decan Movie. You must accept to continue.", color = Color.LightGray)
            Text("1. Legal use. You may use the app only where lawful and only to access content you own, are licensed to access, or are otherwise legally authorized to view or download.", color = Color.White)
            Text("2. Third-party providers. Catalogs, add-ons, links, metadata and streams may be supplied by independent third parties. Decan Movie does not guarantee their availability, accuracy, safety, licensing status or continued operation.", color = Color.White)
            Text("3. Rights and permissions. You are responsible for confirming that you have the necessary rights and permissions before streaming, saving, copying, sharing or redistributing any content. Do not bypass DRM, paywalls, authentication, geographic restrictions or other access controls.", color = Color.White)
            Text("4. Downloads. Download only files that the provider permits you to download and that applicable law allows you to save. A stream being playable does not mean it is authorized for download or redistribution.", color = Color.White)
            Text("5. Privacy and network use. Search and playback may contact third-party services. Those services may process requests under their own privacy policies. Data charges may apply; use Wi-Fi settings where available.", color = Color.White)
            Text("6. No warranty. The app and third-party sources are provided on an ‘as available’ basis. Playback, subtitles, quality selection, downloads and catalog results may fail or change. To the extent allowed by law, the app authors disclaim implied warranties and liability for third-party content or service interruptions.", color = Color.White)
            Text("7. Suspension. Misuse, unlawful access, rights violations or attempts to circumvent technical protections are prohibited. Stop using a source if you lack authorization or receive a rights complaint.", color = Color.White)
            Text("8. Changes and contact. These terms may be updated in future versions. Continued use after a new version of the terms is presented requires acceptance of that version.", color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text("I have read and agree to the Terms and Conditions, and I will use Decan Movie only for lawful, authorized content.", color = Color.White)
            }
            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("Accept and Continue") }
            Text("If you do not agree, do not use the app. Close it without accepting.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DecanMovieBoxAppContent() {
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
                error = if (categories.isEmpty()) "Catalogs are temporarily unavailable. Check your connection and TMDB credentials, then retry." else null
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
                error = if (searchResults.isEmpty()) "No matches found. If TMDB is unavailable, try a simpler title or browse the Catalogs tab." else null
            } catch (e: Exception) {
                error = e.message ?: "Search failed. Please try again."
            } finally {
                loading = false
            }
        }
    }

    fun loadMovieStreams(movie: Movie) {
        // Open details immediately, then ask the configured MovieBox-TUI native providers for real
        // streams. If no provider can resolve this title, fall back to the rights-checked Archive catalog.
        selectedMovie = movie
        fetchingStreams = movie.videoUrl.isBlank()
        if (movie.videoUrl.isNotBlank()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val providerMatch = runCatching { repository.getStreamsForMedia(movie) }.getOrNull()
                if (providerMatch?.videoUrl?.isNotBlank() == true) providerMatch
                else runCatching { repository.findLicensedPlayableMatch(movie) }.getOrNull()
            }
            if (selectedMovie?.id == movie.id) {
                selectedMovie = result?.takeIf { it.videoUrl.isNotBlank() } ?: movie
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
                    selected = destination == Destination.CATALOGS,
                    onClick = { destination = Destination.CATALOGS },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Catalogs") },
                    label = { Text("Catalogs") },
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
                onRetry = { if (query.isNotBlank()) performSearch() else loadHomeCategories() },
                onMovieClick = { loadMovieStreams(it) },
                onResume = { library.lastMovie()?.let { playerMovie = it } },
                onNavigate = { destination = it; if (it == Destination.DOWNLOADS) refreshDownloads++; if (it == Destination.SAVED) refreshFavorites++ },
                loading = loading,
                error = error,
                padding = padding,
            )
            Destination.CATALOGS -> AddonCatalogScreen(padding = padding)
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
                        if (downloadedUri != null) {
                            playerMovie = entry.movie.copy(videoUrl = downloadedUri.toString())
                        } else {
                            Toast.makeText(context, "This download is not finished yet.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { entry ->
                        library.cancelDownload(entry.downloadId)
                        refreshDownloads++
                        Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                    },
                    padding = padding,
                )
            }
        }
    }

    selectedMovie?.let { movie ->
        MovieDetailsDialog(
            movie = movie,
            repository = repository,
            isFavorite = library.isFavorite(movie),
            isFetchingStreams = fetchingStreams,
            onDismiss = { selectedMovie = null },
            onEpisodeSelected = { seasonNumber, episodeNumber ->
                fetchingStreams = true
                scope.launch {
                    val episodeMovie = withContext(Dispatchers.IO) {
                        runCatching { repository.getStreamsForMedia(movie, seasonNumber, episodeNumber) }.getOrNull()
                    }
                    if (selectedMovie?.id == movie.id) {
                        selectedMovie = episodeMovie?.takeIf { it.videoOptions.isNotEmpty() || it.videoUrl.isNotBlank() }
                            ?: movie.copy(videoUrl = "", videoOptions = emptyList(), sourceName = "No episode stream found")
                        fetchingStreams = false
                        if (episodeMovie == null || (episodeMovie.videoOptions.isEmpty() && episodeMovie.videoUrl.isBlank())) {
                            Toast.makeText(context, "No playable source was found for S${seasonNumber}E${episodeNumber}. Try another provider or episode.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onPlay = { chosenSource ->
                val selectedForPlayback = chosenSource ?: movie.videoOptions.firstOrNull()
                val playbackMovie = if (selectedForPlayback != null) movie.copy(
                    videoUrl = selectedForPlayback.url,
                    sourceName = "${movie.sourceName} · ${selectedForPlayback.label}",
                    streamFormat = if (selectedForPlayback.url.contains(".m3u8", ignoreCase = true)) "hls" else movie.streamFormat,
                    videoOptions = listOf(selectedForPlayback) + movie.videoOptions.filterNot { it.url == selectedForPlayback.url },
                ) else movie
                if (playbackMovie.videoUrl.isNotBlank()) {
                    playerMovie = playbackMovie
                    selectedMovie = null
                } else {
                    Toast.makeText(context, "No playable stream was found. Check your connection or try another provider result; open-license Archive titles remain available as a fallback.", Toast.LENGTH_LONG).show()
                }
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
                        val chosenSource = selectedDownloadSource ?: movie.videoOptions.firstOrNull()
                        val targetUrl = chosenSource?.url ?: movie.videoUrl
                        require(isAllowedMediaUrl(targetUrl)) { "This title has no verified downloadable video source. Choose a licensed Internet Archive title instead." }
                        enqueueDownload(
                            context,
                            library,
                            movie,
                            chosenSource ?: VideoSource(targetUrl, "Standard MP4"),
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
    onRetry: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onResume: () -> Unit,
    onNavigate: (Destination) -> Unit,
    loading: Boolean,
    error: String?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item {
            // App Header Branding and right-side navigation menu
            var menuExpanded by remember { mutableStateOf(false) }
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
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("DECAN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("MOVIE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2B84B))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Open navigation menu", tint = Color.White)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        listOf("Browse" to Destination.BROWSE, "Catalogs" to Destination.CATALOGS, "Saved" to Destination.SAVED, "Downloads" to Destination.DOWNLOADS).forEach { (label, target) ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { menuExpanded = false; onNavigate(target) })
                        }
                    }
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

        if (error != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(error.orEmpty(), color = Color(0xFFFFD6A5), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text("Retry") }
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
    onDelete: (DownloadEntry) -> Unit,
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
            Text(
                "Movies you download will appear here.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.LightGray,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.downloadId }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = entry.movie.posterUrl,
                                contentDescription = "Poster for ${entry.movie.title}",
                                modifier = Modifier.width(64.dp).aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.movie.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text("Download ID ${entry.downloadId}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onPlay(entry) }) { Text("Play") }
                                    TextButton(onClick = { onDelete(entry) }) { Text("Delete", color = Color(0xFFFF8A80)) }
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
private fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(142.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
    ) {
        Box {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = "Poster for ${movie.title}",
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).background(Color(0xFF29251E)),
                contentScale = ContentScale.Crop,
            )
            if (movie.videoUrl.isNotBlank()) {
                Box(Modifier.align(Alignment.BottomEnd).padding(7.dp).size(30.dp).clip(RoundedCornerShape(50)).background(Color(0xFFF2B84B)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Available to play", tint = Color(0xFF171411))
                }
            }
        }
        Column(Modifier.padding(9.dp)) {
            Text(movie.title, maxLines = 2, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(movie.year, when (movie.mediaType) { "tv" -> "Series"; "anime" -> "Anime"; else -> "Movie" }, if (movie.voteAverage > 0) "★ ${String.format(java.util.Locale.US, "%.1f", movie.voteAverage)}/10" else "").filter(String::isNotBlank).joinToString(" · "),
                color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MovieDetailsDialog(
    movie: Movie,
    repository: CatalogRepository,
    isFavorite: Boolean,
    isFetchingStreams: Boolean,
    onDismiss: () -> Unit,
    onEpisodeSelected: (seasonNumber: Int, episodeNumber: Int) -> Unit,
    onPlay: (VideoSource?) -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    var selectedPlaybackSource by remember(movie.id, movie.videoOptions) { mutableStateOf<VideoSource?>(movie.videoOptions.firstOrNull()) }
    var seasons by remember(movie.tmdbId) { mutableStateOf<List<SeriesSeason>>(emptyList()) }
    var selectedSeason by remember(movie.tmdbId) { mutableStateOf<Int?>(null) }
    var episodes by remember(movie.tmdbId) { mutableStateOf<List<SeriesEpisode>>(emptyList()) }
    var seasonMenu by remember { mutableStateOf(false) }
    var seriesInfoError by remember(movie.tmdbId) { mutableStateOf<String?>(null) }
    var seriesInfoLoading by remember(movie.tmdbId) { mutableStateOf(false) }

    LaunchedEffect(movie.tmdbId, movie.mediaType) {
        if (movie.mediaType == "tv" && movie.tmdbId > 0) {
            seriesInfoLoading = true
            seriesInfoError = null
            try {
                seasons = withContext(Dispatchers.IO) { repository.getSeriesSeasons(movie.tmdbId) }
                selectedSeason = seasons.firstOrNull()?.seasonNumber
                if (seasons.isEmpty()) seriesInfoError = "No season data returned. Check your TMDB connection or credentials."
            } catch (exception: Exception) {
                seriesInfoError = "Season details are temporarily unavailable."
            } finally {
                seriesInfoLoading = false
            }
        } else if (movie.mediaType == "tv") {
            seriesInfoError = "Season details require a TMDB result with a valid ID."
        }
    }
    LaunchedEffect(movie.tmdbId, selectedSeason) {
        val seasonNumber = selectedSeason ?: return@LaunchedEffect
        episodes = emptyList()
        try {
            episodes = withContext(Dispatchers.IO) { repository.getSeasonEpisodes(movie.tmdbId, seasonNumber) }
        } catch (_: Exception) {
            episodes = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(movie.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                movie.posterUrl?.let { poster ->
                    AsyncImage(model = poster, contentDescription = "Poster for ${movie.title}", modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                }
                Text(listOf(movie.year, if (movie.mediaType == "tv") "TV Series" else if (movie.mediaType == "anime") "Anime" else "Movie", if (movie.voteAverage > 0) "★ ${String.format(java.util.Locale.US, "%.1f", movie.voteAverage)}/10" else "", movie.sourceName).filter(String::isNotBlank).joinToString(" · "), color = Color(0xFFF2B84B), modifier = Modifier.padding(top = 10.dp))
                if (movie.overview.isNotBlank()) Text(movie.overview, modifier = Modifier.padding(top = 8.dp))
                if (movie.licenseUrl.isNotBlank()) Text("Rights / license: ${movie.licenseUrl}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (movie.mediaType == "tv") {
                    Text("Seasons & episodes", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    if (seasons.isEmpty()) {
                        Text(if (seriesInfoLoading) "Loading season information…" else (seriesInfoError ?: "Season information unavailable."), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    } else {
                        Box {
                            TextButton(onClick = { seasonMenu = true }) {
                                val current = seasons.firstOrNull { it.seasonNumber == selectedSeason }
                                Text("${current?.name ?: "Choose season"} ▾")
                            }
                            androidx.compose.material3.DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                                seasons.forEach { season ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("${season.name} · ${season.episodeCount} episodes") },
                                        onClick = { selectedSeason = season.seasonNumber; seasonMenu = false },
                                    )
                                }
                            }
                        }
                        if (episodes.isEmpty()) Text("No episode details were returned for this season.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        else episodes.forEach { episode ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    val seasonNumber = selectedSeason ?: return@clickable
                                    onEpisodeSelected(seasonNumber, episode.episodeNumber)
                                }.padding(vertical = 8.dp)
                            ) {
                                Text("▶ ${episode.episodeNumber}. ${episode.name}", fontWeight = FontWeight.Medium)
                                if (episode.airDate.isNotBlank()) Text(episode.airDate, color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelSmall)
                                if (episode.overview.isNotBlank()) Text(episode.overview, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            }
                        }
                    }
                }
                if (isFetchingStreams) {
                    Text("Searching configured providers for playable sources…", color = Color(0xFFFFD6A5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                } else if (movie.videoOptions.isNotEmpty()) {
                    Text("Available sources · choose one to play", color = Color(0xFF9FE3B1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    movie.videoOptions.forEachIndexed { index, source ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedPlaybackSource = source }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedPlaybackSource == source, onClick = { selectedPlaybackSource = source })
                            Column(Modifier.weight(1f)) {
                                Text(source.label.ifBlank { "Source ${index + 1}" }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text(source.height?.let { "${it}p" } ?: if (source.url.contains(".m3u8", true)) "HLS stream" else "Direct stream", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (movie.videoUrl.isNotBlank()) {
                    Text("Playable source found · ${movie.sourceName}", color = Color(0xFF9FE3B1), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                } else {
                    Text("No playable source was found. Try another title or the Licensed Films You Can Play catalog.", color = Color(0xFFFFD6A5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onFavorite) { Text(if (isFavorite) "Remove Saved" else "♡ Save") }
                    TextButton(onClick = onDownload, enabled = movie.videoUrl.isNotBlank() && isAllowedMediaUrl(movie.videoUrl)) { Text("Download") }
                }
                Button(onClick = { onPlay(selectedPlaybackSource) }, enabled = (movie.videoUrl.isNotBlank() || movie.videoOptions.isNotEmpty()) && !isFetchingStreams, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isFetchingStreams) "Finding playable sources…" else "▶ Play selected source")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun enqueueDownload(context: Context, library: LocalLibrary, movie: Movie, source: VideoSource): Long {
    require(isAllowedMediaUrl(source.url)) { "Downloads are limited to verified HTTPS Internet Archive media files." }
    val updatedMovie = movie.copy(videoUrl = source.url)
    val id = library.startDownload(updatedMovie, source.url)
    check(id > 0L) { "Android could not start this download." }
    return id
}

private fun isAllowedMediaUrl(value: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    uri.scheme == "https" && (uri.host == "archive.org" || uri.host?.endsWith(".archive.org") == true) && uri.path?.contains("/download/") == true
}.getOrDefault(false)
