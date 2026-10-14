package de.darkspirit510.episodeoverview.domain

/**
 * Represents a cut point where TMDB season should be truncated
 * because filesystem uses different season organization (common with anime)
 */
data class SeasonCutPoint(
    val tmdbSeason: Int,
    val cutAtEpisode: Int  // TMDB episodes beyond this should be excluded
)
