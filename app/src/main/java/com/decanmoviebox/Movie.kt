package com.decanmoviebox

data class VideoSource(
    val url: String,
    val label: String,
    val height: Int? = null,
)

data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String?,
    val releaseDate: String,
    val archiveUrl: String,
    val licenseUrl: String,
    val videoUrl: String,
    val tmdbId: Int,
    val mediaType: String = "movie",
    val videoOptions: List<VideoSource> = emptyList(),
    val sourceName: String = "Internet Archive",
    val streamFormat: String = "progressive",
)
