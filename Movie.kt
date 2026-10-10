package com.decanmoviebox

data class VideoSource(
    val url: String,
    val label: String,
    val height: Int? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class Movie(
    val id: String,
    val title: String,
    val year: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val releaseDate: String = "",
    val archiveUrl: String = "",
    val licenseUrl: String = "",
    val videoUrl: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val sourceName: String = "Decan Engine",
    val voteAverage: Double = 0.0,
    val streamFormat: String = "progressive",
    val videoOptions: List<VideoSource> = emptyList(),
)


data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
)

data class SeriesEpisode(
    val episodeNumber: Int,
    val name: String,
    val airDate: String = "",
    val overview: String = "",
)
