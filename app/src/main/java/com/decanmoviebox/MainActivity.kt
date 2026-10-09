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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.activity.compose.BackHandler
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
    var menuExpanded by remember { mutableStateOf(false) }
    
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
        // Metadata results are not presumed to have a legal stream. Only verified archive records carry videoUrl.
        selectedMovie = movie
        fetchingStreams = false
    }

    LaunchedEffect(Unit) { loadHomeCategories() }

    BackHandler(enabled = selectedMovie != null) { selectedMovie = null }

    playerMovie?.let { movie ->
        PlayerScreen(movie = movie, library = library, onBack = { playerMovie = null })
        return
    }

    Scaffold(
        containerColor = Color(0xFF11100E),
        bottomBar = {
            if (selectedMovie == null) NavigationBar(containerColor = Color(0xFF1B1916)) {
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
        if (selectedMovie != null) {
            val movie = selectedMovie!!
            MovieDetailsScreen(
                movie = movie,
                repository = repository,
                isFavorite = library.isFavorite(movie),
                isFetchingStreams = fetchingStreams,
                padding = padding,
                onBack = { selectedMovie = null },
                onPlay = { playerMovie = movie; selectedMovie = null },
                onFavorite = { library.toggleFavorite(movie); refreshFavorites++ },
                onDownload = { pendingDownload = movie; selectedDownloadSource = movie.videoOptions.firstOrNull() },
            )
        } else {
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
                    onMenuClick = { menuExpanded = true },
                    menuExpanded = menuExpanded,
                    onMenuDismiss = { menuExpanded = false },
                    onNavigate = { destination = it; menuExpanded = false },
                    onMovieClick = { loadMovieStreams(it) },
                    onResume = { library.lastMovie()?.let { playerMovie = it } },
                    loading = loading,
                    error = error,
                    padding = padding,
                )
                Destination.CATALOGS -> AddonCatalogScreen(
                    padding = padding,
                    repository = repository,
                    onMovieClick = { loadMovieStreams(it) },
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
                            if (downloadedUri != null) {
                                playerMovie = entry.movie.copy(videoUrl = downloadedUri.toString())
                            } else {
                                Toast.makeText(context, "This download is not finished yet.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        padding = padding,
                    )
                }
            }
        }
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
    onMenuClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onNavigate: (Destination) -> Unit,
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
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation menu", tint = Color(0xFFF2B84B))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(text = { Text("Browse") }, onClick = { onNavigate(Destination.BROWSE) }, leadingIcon = { Icon(Icons.Default.Home, null) })
                        DropdownMenuItem(text = { Text("Catalogs & Sources") }, onClick = { onNavigate(Destination.CATALOGS) }, leadingIcon = { Icon(Icons.Default.VideoLibrary, null) })
                        DropdownMenuItem(text = { Text("Saved Movies") }, onClick = { onNavigate(Destination.SAVED) }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
                        DropdownMenuItem(text = { Text("Downloads") }, onClick = { onNavigate(Destination.DOWNLOADS) }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    }
                }
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
                                TextButton(onClick = { onPlay(entry) }) { Text("Play downloaded file") }
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
                listOf(movie.year, when (movie.mediaType) { "tv" -> "Series"; "anime" -> "Anime"; else -> "Movie" }).filter(String::isNotBlank).joinToString(" · "),
                color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelSmall,
            )
            if (movie.videoUrl.isBlank()) Text("Info only", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MovieDetailsScreen(
    movie: Movie,
    repository: CatalogRepository,
    isFavorite: Boolean,
    isFetchingStreams: Boolean,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
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

    Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFF11100E))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1B1916)).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to movies", tint = Color(0xFFF2B84B))
            }
            Column(Modifier.weight(1f)) {
                Text(movie.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("MOVIE DETAILS", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2B84B))
            }
            TextButton(onClick = onFavorite) { Text(if (isFavorite) "♥ Saved" else "♡ Save") }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            movie.posterUrl?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = "Poster for ${movie.title}",
                    modifier = Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                listOf(movie.year, if (movie.mediaType == "tv") "TV Series" else if (movie.mediaType == "anime") "Anime" else "Movie", movie.sourceName)
                    .filter(String::isNotBlank).joinToString(" · "),
                color = Color(0xFFF2B84B),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(movie.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            if (movie.overview.isNotBlank()) Text(movie.overview, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            if (movie.licenseUrl.isNotBlank()) {
                Text("Rights / license: ${movie.licenseUrl}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(top = 10.dp))
            }
            if (movie.mediaType == "tv") {
                Text("Seasons & episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
                if (seasons.isEmpty()) {
                    Text(if (seriesInfoLoading) "Loading season information…" else (seriesInfoError ?: "Season information unavailable."), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                } else {
                    Box {
                        TextButton(onClick = { seasonMenu = true }) {
                            val current = seasons.firstOrNull { it.seasonNumber == selectedSeason }
                            Text("${current?.name ?: "Choose season"} ▾")
                        }
                        DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                            seasons.forEach { season ->
                                DropdownMenuItem(
                                    text = { Text("${season.name} · ${season.episodeCount} episodes") },
                                    onClick = { selectedSeason = season.seasonNumber; seasonMenu = false },
                                )
                            }
                        }
                    }
                    if (episodes.isEmpty()) Text("No episode details were returned for this season.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    else episodes.forEach { episode ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text("${episode.episodeNumber}. ${episode.name}", fontWeight = FontWeight.Medium)
                            if (episode.airDate.isNotBlank()) Text(episode.airDate, color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelSmall)
                            if (episode.overview.isNotBlank()) Text(episode.overview, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        }
                    }
                }
            }
            if (movie.videoUrl.isBlank()) {
                Text(
                    "This catalog entry has information only. Playback and downloads appear when a compatible, rights-checked video source is available.",
                    color = Color(0xFFFFD6A5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    "Video source available · ${movie.videoOptions.size.coerceAtLeast(1)} quality option(s)",
                    color = Color(0xFF9FE3B1), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1B1916)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPlay,
                enabled = movie.videoUrl.isNotBlank() && !isFetchingStreams,
                modifier = Modifier.weight(1f),
            ) { Text(if (isFetchingStreams) "Checking…" else "▶ Play") }
            Button(
                onClick = onDownload,
                enabled = movie.videoUrl.isNotBlank() && isAllowedMediaUrl(movie.videoUrl),
                modifier = Modifier.weight(1f),
            ) { Text("Download") }
        }
    }
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
