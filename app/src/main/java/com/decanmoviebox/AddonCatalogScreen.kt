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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CatalogSort { NEWEST, TITLE }

@Composable
fun AddonCatalogScreen(
    padding: PaddingValues,
    onAuthorizedSource: (Movie) -> Unit,
) {
    val repository = remember { AddonCatalogRepository() }
    var addon by remember { mutableStateOf(metadataAddons.first()) }
    var catalogs by remember { mutableStateOf<List<AddonCatalog>>(emptyList()) }
    var catalog by remember { mutableStateOf<AddonCatalog?>(null) }
    var catalogItems by remember { mutableStateOf<List<AddonCatalogItem>>(emptyList()) }
    var queryInput by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var sourceMenu by remember { mutableStateOf(false) }
    var catalogMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(CatalogSort.NEWEST) }
    var loadingCatalogs by remember { mutableStateOf(false) }
    var loadingItems by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<AddonCatalogItem?>(null) }
    var sourceTargetItem by remember { mutableStateOf<AddonCatalogItem?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf("") }
    var rightsText by remember { mutableStateOf("") }
    var rightsConfirmed by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(addon) {
        loadingCatalogs = true
        catalogs = emptyList()
        catalog = null
        catalogItems = emptyList()
        error = null
        try {
            val available = withContext(Dispatchers.IO) { repository.loadCatalogs(addon) }
            catalogs = available
            catalog = available.first()
            page = 1
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = exception.message ?: "Could not load ${addon.name} catalogs."
        } finally {
            loadingCatalogs = false
        }
    }

    LaunchedEffect(catalog, submittedQuery, page) {
        val selectedCatalog = catalog ?: return@LaunchedEffect
        loadingItems = true
        error = null
        try {
            val results = withContext(Dispatchers.IO) {
                repository.loadItems(selectedCatalog, submittedQuery, page)
            }
            catalogItems = if (page == 1) results else catalogItems + results
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = exception.message ?: "Could not load the catalog."
        } finally {
            loadingItems = false
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            "Catalogs & metadata",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Text(
            "Add-ons provide discovery metadata only. Attach a direct HTTPS video URL you own or are licensed to use.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { sourceMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(addon.name, maxLines = 1)
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    metadataAddons.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                sourceMenu = false
                                addon = option
                            },
                        )
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { sortMenu = true }) {
                    Text(if (sort == CatalogSort.NEWEST) "Newest" else "A–Z")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Newest first") },
                        onClick = { sort = CatalogSort.NEWEST; sortMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Title A–Z") },
                        onClick = { sort = CatalogSort.TITLE; sortMenu = false },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { catalogMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = catalogs.isNotEmpty(),
                ) {
                    Text(catalog?.name ?: if (loadingCatalogs) "Loading…" else "Catalog", maxLines = 1)
                }
                DropdownMenu(expanded = catalogMenu, onDismissRequest = { catalogMenu = false }) {
                    catalogs.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.name} · ${option.type}") },
                            onClick = {
                                catalogMenu = false
                                catalog = option
                                page = 1
                                catalogItems = emptyList()
                            },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = queryInput,
            onValueChange = { queryInput = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("Search this catalog") },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = {
                    submittedQuery = queryInput.trim()
                    page = 1
                    catalogItems = emptyList()
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search catalog")
                }
            },
        )

        when {
            loadingCatalogs || (loadingItems && catalogItems.isEmpty()) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null && catalogItems.isEmpty() -> AddonErrorPanel(error.orEmpty())
            catalogItems.isEmpty() -> Text(
                "No catalog titles found.",
                modifier = Modifier.padding(20.dp),
                color = Color.LightGray,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    if (sort == CatalogSort.TITLE) {
                        catalogItems.sortedBy { it.title.lowercase() }
                    } else {
                        catalogItems.sortedWith(
                            compareByDescending<AddonCatalogItem> { it.year.toIntOrNull() ?: 0 }
                                .thenBy { it.title.lowercase() },
                        )
                    },
                    key = { "${it.sourceName}:${it.type}:${it.id}" },
                ) { item ->
                    AddonCatalogCard(item) { selectedItem = item }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(
                            onClick = { page++ },
                            enabled = !loadingItems,
                        ) {
                            Text(if (loadingItems) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text("${item.title} ${item.year}".trim()) },
            text = {
                Column {
                    item.posterUrl?.let { poster ->
                        AsyncImage(
                            model = poster,
                            contentDescription = "Poster for ${item.title}",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.55f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        item.overview.ifBlank { "No description is available from this catalog." },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Catalog metadata from ${item.sourceName}. The add-on itself does not provide playback here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        sourceTargetItem = item
                        sourceUrl = ""
                        rightsText = ""
                        rightsConfirmed = false
                        sourceError = null
                        selectedItem = null
                        showSourceDialog = true
                    }) { Text("Add authorized URL") }
                    TextButton(onClick = { selectedItem = null }) { Text("Close") }
                }
            },
        )
    }

    if (showSourceDialog) {
        val item = sourceTargetItem
        if (item != null) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("Add authorized source") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Enter a direct HTTPS video file or HLS playlist for ${item.title}. " +
                                "Catalog metadata does not grant video rights.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = sourceUrl,
                            onValueChange = { sourceUrl = it; sourceError = null },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            label = { Text("HTTPS video URL") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = rightsText,
                            onValueChange = { rightsText = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("License / permission details") },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rightsConfirmed,
                                onCheckedChange = { rightsConfirmed = it },
                            )
                            Text("I own or have permission to use this video.")
                        }
                        sourceError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        val uri = Uri.parse(sourceUrl.trim())
                        val extension = uri.lastPathSegment.orEmpty()
                            .substringAfterLast('.', "")
                            .lowercase()
                        val streamFormat = if (extension == "m3u8") "hls" else "progressive"
                        val validDirectFile = extension in setOf("mp4", "m4v", "webm", "mov")
                        val validUrl = uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                            (streamFormat == "hls" || validDirectFile)
                        when {
                            !validUrl -> sourceError =
                                "Use HTTPS with an HLS (.m3u8) URL or direct MP4, M4V, WebM, or MOV file."
                            rightsText.isBlank() -> sourceError =
                                "Enter the license or permission details for this video."
                            !rightsConfirmed -> sourceError =
                                "Confirm that you own or have permission to use this video."
                            else -> {
                                onAuthorizedSource(
                                    Movie(
                                        id = "authorized-${item.sourceName}-${item.id}".replace(
                                            Regex("[^A-Za-z0-9._-]"), "_",
                                        ),
                                        title = item.title,
                                        year = item.year,
                                        overview = item.overview,
                                        posterUrl = item.posterUrl,
                                        releaseDate = item.year,
                                        archiveUrl = item.id,
                                        licenseUrl = rightsText.trim(),
                                        videoUrl = uri.toString(),
                                        tmdbId = 0,
                                        mediaType = item.type,
                                        videoOptions = if (streamFormat == "hls") emptyList() else listOf(
                                            VideoSource(uri.toString(), extension.uppercase()),
                                        ),
                                        sourceName = "User-provided authorized URL · ${item.sourceName}",
                                        streamFormat = streamFormat,
                                    ),
                                )
                                showSourceDialog = false
                            }
                        }
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { showSourceDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun AddonCatalogCard(item: AddonCatalogItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1916)),
        shape = RoundedCornerShape(14.dp),
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = "Poster for ${item.title}",
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).background(Color(0xFF29251E)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(10.dp)) {
            Text(item.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(item.year, item.type.replaceFirstChar(Char::uppercase)).filter(String::isNotBlank)
                    .joinToString(" · "),
                color = Color(0xFFF2B84B),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AddonErrorPanel(message: String) {
    Text(
        message,
        color = Color(0xFFFFB4A9),
        modifier = Modifier.padding(20.dp),
    )
}
