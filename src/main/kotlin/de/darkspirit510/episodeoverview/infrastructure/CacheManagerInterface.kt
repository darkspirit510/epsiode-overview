package de.darkspirit510.episodeoverview.infrastructure

import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import de.darkspirit510.episodeoverview.domain.TmdbSeries

interface CacheManagerInterface {
    fun loadAllScannedEpisodes(): Map<String, List<ScannedEpisode>>
    fun loadTmdbSeries(seriesId: Int): TmdbSeries?
    fun isSeriesBlacklisted(seriesName: String): Boolean
    fun isSeasonBlacklisted(seriesName: String, seasonNumber: Int): Boolean
    fun isEpisodeOverridden(seriesName: String, seasonNumber: Int, episodeNumber: Int): Boolean
    fun saveScannedEpisodes(seriesName: String, episodes: List<ScannedEpisode>)
    fun loadScannedEpisodes(seriesName: String): List<ScannedEpisode>?
    fun saveTmdbSeries(series: TmdbSeries, filesystemName: String?)
    fun loadTmdbSeriesByName(seriesName: String): TmdbSeries?
    fun getOverriddenId(seriesName: String): Int?
    fun clearEpisodesCache()
}