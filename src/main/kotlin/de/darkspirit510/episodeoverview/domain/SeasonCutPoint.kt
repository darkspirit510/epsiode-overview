package de.darkspirit510.episodeoverview.domain

/**
 * Represents a cut point where TMDB season should be truncated
 * because filesystem uses different season organization (common with anime)
 */
data class SeasonCutPoint(
    val tmdbSeason: Int,
    val cutAtEpisode: Int  // TMDB episodes beyond this should be excluded
)

/**
 * Contains cut points for a specific series
 */
data class SeriesCutPoints(
    val seriesName: String,
    val cutPoints: List<SeasonCutPoint> = emptyList()
) {
    fun getCutPoint(tmdbSeason: Int): Int? {
        return cutPoints.find { it.tmdbSeason == tmdbSeason }?.cutAtEpisode
    }
    
    fun hasCutPoints(): Boolean = cutPoints.isNotEmpty()
}