package de.darkspirit510.episodeoverview

import de.darkspirit510.episodeoverview.domain.*
import de.darkspirit510.episodeoverview.infrastructure.CacheManagerInterface
import java.io.File

fun createTestCacheManager(
    scannedEpisodes: Map<String, List<ScannedEpisode>> = emptyMap(),
    tmdbSeries: Map<Int, TmdbSeries> = emptyMap(),
    blacklistedSeries: Set<String> = emptySet(),
    blacklistedSeasons: Map<String, Set<Int>> = emptyMap()
): CacheManagerInterface {
    // Create temporary TMDB cache files for testing
    val tmdbCacheDir = File(System.getProperty("java.io.tmpdir"), "episodeoverview-test-cache/tmdb")
    tmdbCacheDir.mkdirs()
    
    // Create fake TMDB files so that GenerateTableUseCase can find them
    tmdbSeries.forEach { (id, series) ->
        val normalizedName = series.name
            .replace("'", "")
            .replace(" ", "-")
            .lowercase()
        val fileName = "$id-$normalizedName.json"
        File(tmdbCacheDir, fileName).createNewFile()
    }
    
    return object : CacheManagerInterface {
        override fun loadAllScannedEpisodes(): Map<String, List<ScannedEpisode>> = scannedEpisodes
        override fun loadTmdbSeries(seriesId: Int): TmdbSeries? = tmdbSeries[seriesId]
        override fun isSeriesBlacklisted(seriesName: String): Boolean = blacklistedSeries.contains(seriesName)
        override fun isSeasonBlacklisted(seriesName: String, seasonNumber: Int): Boolean =
            blacklistedSeasons[seriesName]?.contains(seasonNumber) ?: false
        
        // Override other methods to prevent file system operations during tests
        override fun saveScannedEpisodes(seriesName: String, episodes: List<ScannedEpisode>) {}
        override fun loadScannedEpisodes(seriesName: String): List<ScannedEpisode>? = null
        override fun saveTmdbSeries(series: TmdbSeries, filesystemName: String?) {}
        override fun loadTmdbSeriesByName(seriesName: String): TmdbSeries? = null
        override fun getOverriddenId(seriesName: String): Int? = null
        override fun clearEpisodesCache() {}
    }
}