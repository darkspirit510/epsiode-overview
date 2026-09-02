package de.darkspirit510.episodeoverview.domain

data class TmdbSeason(
    val id: Int,
    val seasonNumber: Int,
    val episodes: List<TmdbEpisode>
)
