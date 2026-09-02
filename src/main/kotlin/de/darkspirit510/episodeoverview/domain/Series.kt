package de.darkspirit510.episodeoverview.domain

data class Series(
    val name: String,
    val seasons: Map<Int, Season> = emptyMap(),
    val tmdbId: Int? = null
)
