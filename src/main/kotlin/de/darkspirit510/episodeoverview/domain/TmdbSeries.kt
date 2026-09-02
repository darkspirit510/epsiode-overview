package de.darkspirit510.episodeoverview.domain

data class TmdbSeries(
    val id: Int,
    val name: String,
    val seasons: List<TmdbSeason>
)
