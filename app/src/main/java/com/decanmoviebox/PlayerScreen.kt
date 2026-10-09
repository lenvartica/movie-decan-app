package com.decanmoviebox

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(movie: Movie, library: LocalLibrary, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player = remember(movie.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(movie.videoUrl))
            prepare()
            seekTo(library.progress(movie))
            playWhenReady = true
        }
    }
    var trackType by remember { mutableStateOf<Int?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(player) {
        while (true) {
            library.saveProgress(movie, player.currentPosition.coerceAtLeast(0))
            delay(3_000)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "This video could not be played."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            library.saveProgress(movie, player.currentPosition.coerceAtLeast(0))
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0x99000000)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Text(movie.title, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                }
                Row {
                    IconButton(onClick = { trackType = C.TRACK_TYPE_AUDIO }) {
                        Icon(Icons.Default.Tune, "Audio tracks", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    IconButton(onClick = { trackType = C.TRACK_TYPE_TEXT }) {
                        Icon(Icons.Default.Subtitles, "Subtitles", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Box {
                        IconButton(onClick = { speedMenu = true }) {
                            Text("${player.playbackParameters.speed}x", color = androidx.compose.ui.graphics.Color.White)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        player.setPlaybackSpeed(speed)
                                        speedMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        landscape = !landscape
                        activity?.requestedOrientation = if (landscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }) {
                        Icon(Icons.Default.Fullscreen, "Toggle fullscreen orientation", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        playbackError?.let { message ->
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xDD171411)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = androidx.compose.ui.graphics.Color.White)
                TextButton(onClick = {
                    playbackError = null
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.playWhenReady = true
                }) { Text("Retry") }
            }
        }
    }

    trackType?.let { type ->
        TrackPickerDialog(player = player, trackType = type, onDismiss = { trackType = null })
    }
}

@Composable
private fun TrackPickerDialog(player: ExoPlayer, trackType: Int, onDismiss: () -> Unit) {
    val tracks = player.currentTracks.groups
        .filter { it.type == trackType }
        .flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val format = group.getTrackFormat(index)
                Triple(group, index, format.label ?: format.language ?: "Track ${index + 1}")
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trackType == C.TRACK_TYPE_AUDIO) "Audio track" else "Subtitles") },
        text = {
            Column {
                if (trackType == C.TRACK_TYPE_TEXT) {
                    TextButton(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onDismiss()
                    }) { Text("Off") }
                }
                tracks.forEach { (group, index, label) ->
                    TextButton(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(trackType, false)
                            .clearOverridesOfType(trackType)
                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                            .build()
                        onDismiss()
                    }) { Text(label) }
                }
                if (tracks.isEmpty()) Text("No tracks available in this video.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}
