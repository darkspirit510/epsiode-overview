package de.darkspirit510.episodeoverview.application

import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import de.darkspirit510.episodeoverview.domain.TmdbSeries
import de.darkspirit510.episodeoverview.infrastructure.CacheManager
import de.darkspirit510.episodeoverview.infrastructure.FileSystemScanner
import de.darkspirit510.episodeoverview.infrastructure.TmdbClient

class RefreshUseCase(
    private val fileSystemScanner: FileSystemScanner,
    private val tmdbClient: TmdbClient,
    private val cacheManager: CacheManager,
    private val baseDir: String,
    private val subDirs: List<String>
) {

     fun refresh(): Map<String, String> {
         val scanResult = fileSystemScanner.scanDirectory(baseDir, subDirs)
         val summary = mutableMapOf<String, String>()

          scanResult.forEach { (prefixedSeriesName, seasons) ->
              val allEpisodes = seasons.values.flatten()
              cacheManager.saveScannedEpisodes(prefixedSeriesName, allEpisodes)
              summary[prefixedSeriesName] = "Scanned ${allEpisodes.size} episodes in ${seasons.size} seasons"
              
              // Extract the actual series name (without subDir prefix) for TMDB lookup
              val seriesName = extractSeriesName(prefixedSeriesName)
              val normalizedSeriesName = normalizeSeriesName(seriesName)
              
              // Check if we have an override ID for this series
              val overrideId = cacheManager.getOverriddenId(seriesName)
              
              val tmdbFileExists = if (overrideId != null) {
                  // For overridden series, check if file exists with the override ID
                  java.io.File("cache/tmdb").listFiles()?.any { file ->
                      file.name.startsWith("${overrideId}-")
                  } ?: false
              } else {
                  // Normal check for non-overridden series
                  java.io.File("cache/tmdb").listFiles()?.any { file ->
                      file.name.startsWith("${normalizedSeriesName}-") || file.name.contains("-$normalizedSeriesName")
                  } ?: false
              }
              
              // Only fetch TMDB data if it doesn't exist in cache
              if (!tmdbFileExists) {
                  if (overrideId != null) {
                      println("Using override ID $overrideId for series: $seriesName")
                      val tmdbSeries = tmdbClient.getSeriesById(overrideId)
                      tmdbSeries?.let { series ->
                          // Save TMDB series with the actual series name (not prefixed) for filename
                          cacheManager.saveTmdbSeries(series, seriesName)
                          summary["${prefixedSeriesName} (TMDB)"] = "Cached ${series.seasons.sumOf { it.episodes.size }} episodes (overridden)"
                      } ?: run {
                          summary["${prefixedSeriesName} (TMDB)"] = "Failed to fetch with override ID $overrideId"
                      }
                  } else {
                      println("Fetching TMDB data for new series: $seriesName")
                      val tmdbSeries = tmdbClient.getSeries(seriesName)
                      tmdbSeries?.let { series ->
                          // Save TMDB series with the actual series name (not prefixed) for filename
                          cacheManager.saveTmdbSeries(series, seriesName)
                          summary["$prefixedSeriesName (TMDB)"] = "Cached ${series.seasons.sumOf { it.episodes.size }} episodes"
                      } ?: run {
                          summary["$prefixedSeriesName (TMDB)"] = "No TMDB match found"
                      }
                  }
              } else {
                  summary["$prefixedSeriesName (TMDB)"] = "Using cached TMDB data"
              }
          }

         return summary
     }
    
    private fun extractSeriesName(prefixedSeriesName: String) = prefixedSeriesName
        .substringAfter("/", prefixedSeriesName)

    private fun normalizeSeriesName(name: String) = name
        .replace("'", "")
        .replace(" ", "-")
        .lowercase()
}
