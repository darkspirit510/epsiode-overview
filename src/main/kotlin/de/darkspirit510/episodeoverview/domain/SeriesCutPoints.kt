package de.darkspirit510.episodeoverview.domain

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
