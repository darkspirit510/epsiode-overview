package de.darkspirit510.episodeoverview.application

import de.darkspirit510.episodeoverview.domain.Episode
import de.darkspirit510.episodeoverview.domain.EpisodeStatus
import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import de.darkspirit510.episodeoverview.domain.Season
import de.darkspirit510.episodeoverview.domain.Series
import de.darkspirit510.episodeoverview.domain.TmdbEpisode
import de.darkspirit510.episodeoverview.infrastructure.CacheManagerInterface

class GenerateTableUseCase(
    private val cacheManager: CacheManagerInterface
) {

     fun generateTable(): List<TableRow> {
         val allSeries = mutableMapOf<String, SeriesTableData>()
         val seriesDisplayNames = mutableMapOf<String, String>()
         val seriesToSubDir = mutableMapOf<String, String>()
         val processedTmdbIds = mutableSetOf<Int>()

         // Load all scanned episodes from single cache file
         val allScannedEpisodes = cacheManager.loadAllScannedEpisodes()
         allScannedEpisodes.forEach { (prefixedSeriesName, episodes) ->
             // Extract subDir and series name from prefixed name (format: "subDir/seriesName")
             val subDir = prefixedSeriesName.substringBefore("/", prefixedSeriesName)
             val seriesName = prefixedSeriesName.substringAfter("/", prefixedSeriesName)
             seriesToSubDir[seriesName] = subDir
             
             val normalizedSeriesName = normalizeSeriesName(seriesName)
             seriesDisplayNames[normalizedSeriesName] = seriesName
             seriesToSubDir[normalizedSeriesName] = subDir  // Also store by normalized name
             val seriesData = allSeries.getOrPut(normalizedSeriesName) { SeriesTableData(mutableMapOf()) }
             
             episodes.groupBy { it.seasonNumber }.forEach { (seasonNumber, seasonEpisodes) ->
                 seriesData.seasons[seasonNumber] = seasonEpisodes.associateBy { episode -> episode.episodeNumber }
             }
         }

            val tmdbCacheDir = java.io.File("cache/tmdb")
          tmdbCacheDir.listFiles()?.sortedBy { file ->
             // Sort files so that filesystem name versions (without special chars like ?,!) come first
             val fileNameParts = file.nameWithoutExtension.split("-", limit = 2)
             val namePart = fileNameParts.getOrNull(1) ?: ""
             // Filesystem names are less likely to have special characters, so sort them first
             !namePart.any { it in setOf('?', '!', ',') }
         }?.forEach { file ->
             // Extract series ID and filesystem name from filename (format: "ID-filesystem-name.json" or "ID-tmdb-name.json")
             val fileNameParts = file.nameWithoutExtension.split("-", limit = 2)
             val seriesId = fileNameParts.firstOrNull()?.toIntOrNull() ?: return@forEach
             val filesystemNameFromFile = if (fileNameParts.size > 1) fileNameParts[1] else null
             
             // Skip if we already processed this TMDB ID
             if (processedTmdbIds.contains(seriesId)) return@forEach
             processedTmdbIds.add(seriesId)
             
             val tmdbSeries = cacheManager.loadTmdbSeries(seriesId) ?: return@forEach
             
             // Use filesystem name from filename if available, otherwise use TMDB name
             val seriesNameToUse = filesystemNameFromFile ?: tmdbSeries.name
             val normalizedSeriesName = normalizeSeriesName(seriesNameToUse)
             
             // Keep the original name for display if we don't have a filesystem name yet
             if (!seriesDisplayNames.containsKey(normalizedSeriesName)) {
                 seriesDisplayNames[normalizedSeriesName] = seriesNameToUse
             }
             val seriesData = allSeries.getOrPut(normalizedSeriesName) { SeriesTableData(mutableMapOf()) }
             tmdbSeries.seasons.forEach { tmdbSeason ->
                 val seasonEpisodes = tmdbSeason.episodes.associateBy { it.episodeNumber }
                 seriesData.tmdbSeasons[tmdbSeason.seasonNumber] = seasonEpisodes
             }
         }

          // Group by subDir, then by series
         val subDirGroups = mutableMapOf<String, MutableList<TableRow>>()
         
         allSeries.forEach { (normalizedSeriesName, seriesData) ->
             val displayName = seriesDisplayNames[normalizedSeriesName] ?: normalizedSeriesName
             val subDir = seriesToSubDir[displayName] ?: "Unknown"
             
             // Check if series is fully blacklisted
             if (cacheManager.isSeriesBlacklisted(displayName)) {
                 return@forEach
             }
             
               // Get all season numbers from both filesystem and TMDB, excluding season 0 (S00)
               val allSeasonNumbers = (seriesData.seasons.keys + seriesData.tmdbSeasons.keys).filter { it != 0 }.sorted()

               val seriesRows = allSeasonNumbers.flatMap { seasonNumber ->
                   // Check if season is blacklisted
                   if (cacheManager.isSeasonBlacklisted(displayName, seasonNumber)) {
                       return@flatMap listOf()
                   }
                  
                  val filesystemEpisodes = seriesData.seasons[seasonNumber] ?: emptyMap()
                  val tmdbEpisodes = seriesData.tmdbSeasons[seasonNumber] ?: emptyMap()
                  val allEpisodeNumbers = (filesystemEpisodes.keys + tmdbEpisodes.keys).sorted()

                  if (allEpisodeNumbers.isEmpty()) return@flatMap listOf()

                  val episodes = allEpisodeNumbers.map { episodeNumber ->
                      val filesystemEpisode = filesystemEpisodes[episodeNumber]
                      val tmdbEpisode = tmdbEpisodes[episodeNumber]

                       when {
                           filesystemEpisode != null && tmdbEpisode != null -> {
                               // Extract name from filename by removing SXXEYY pattern, file extension, and any leading/trailing separators
                               val fileNameWithoutPattern = filesystemEpisode.fileName
                                   .replace(Regex("S\\d+E\\d+"), "")
                                   .replace(Regex("^[\\s-]+"), "")
                                   .replace(Regex("\\.[^.]*$"), "")  // Remove file extension
                                   .replace(Regex("[\\s-]+"), " ")  // Replace multiple separators with single space
                                   .trim()
                                val normFs = normalizeForComparison(fileNameWithoutPattern)
                                val normTmdb = normalizeForComparison(tmdbEpisode.name)
                                val status = if (normFs.equals(normTmdb, ignoreCase = true)) {
                                    EpisodeStatus.MATCHED
                                } else {
                                    // Fallback: try comparing without spaces
                                    val normFsNoSpaces = normFs.replace(Regex("\\s"), "")
                                    val normTmdbNoSpaces = normTmdb.replace(Regex("\\s"), "")
                                    if (normFsNoSpaces.equals(normTmdbNoSpaces, ignoreCase = true)) {
                                        EpisodeStatus.MATCHED
                                    } else {
                                        EpisodeStatus.NAME_MISMATCH
                                    }
                                }
                               EpisodeTableCell(episodeNumber, status, filesystemEpisode.fileName, tmdbEpisode.name)
                          }
                          filesystemEpisode != null -> {
                              EpisodeTableCell(episodeNumber, EpisodeStatus.FILESYSTEM_ONLY, filesystemEpisode.fileName, null)
                          }
                          else -> {
                              EpisodeTableCell(episodeNumber, EpisodeStatus.TMDB_ONLY, null, tmdbEpisode?.name)
                          }
                      }
                  }

                   listOf(SeriesTableRow(displayName, seasonNumber, episodes, allEpisodeNumbers.size))
              }
              
               // Filter out seasons where all episodes are MATCHED
               val filteredSeriesRows = seriesRows.filter { seasonRow ->
                   !seasonRow.episodes.all { it.status == EpisodeStatus.MATCHED }
               }
               
               // Check if all seasons are complete (all episodes MATCHED)
               val allSeasonsComplete = seriesRows.isNotEmpty() && 
                   seriesRows.all { seasonRow -> seasonRow.episodes.all { it.status == EpisodeStatus.MATCHED } }
                
               // Add series header and season rows to subDir group
               val groupForSubDir = subDirGroups.getOrPut(subDir) { mutableListOf() }
               if (seriesRows.isNotEmpty()) {
                   val seriesDisplayName = if (allSeasonsComplete) "$displayName (Vollständig)" else displayName
                   groupForSubDir.add(SeriesHeaderRow(seriesDisplayName))
                   groupForSubDir.addAll(filteredSeriesRows)
               }
         }
         
         // Build final result with subDir headers
         val result = mutableListOf<TableRow>()
         subDirGroups.entries.sortedBy { it.key }.forEach { (subDir, rows) ->
             // Add subDir header
             result.add(SubDirHeaderRow(subDir))
             
             // Add series header and season rows (already grouped by series)
             result.addAll(rows)
         }
         
         return result
     }

    private fun normalizeName(name: String?): String {
         if (name == null) return ""
         return name
             .replace(Regex("\\.[^.]+"), "")
             .lowercase()
             .replace(Regex("[^a-z0-9]"), "")
     }

     private fun normalizeSeriesName(name: String): String {
         return name
             .replace("'", "")  // Remove apostrophes
             .replace(" ", "-")  // Replace spaces with dashes
             .lowercase()
     }

     private fun normalizeForComparison(name: String): String {
         return name
             .replace("&", "und")  // Treat & as und
             .replace("\\", " ")  // Replace backslashes with space
             .replace("/", " ")  // Replace forward slashes with space
             .replace("#", "")  // Remove hashtags
             .replace(".", "")  // Remove dots
             .replace("à", "a")
             .replace("á", "a")
             .replace("â", "a")
             .replace("ã", "a")
             .replace("å", "a")
             .replace("è", "e")
             .replace("é", "e")
             .replace("ê", "e")
             .replace("ë", "e")
             .replace("ì", "i")
             .replace("í", "i")
             .replace("î", "i")
             .replace("ï", "i")
             .replace("ò", "o")
             .replace("ó", "o")
             .replace("ô", "o")
             .replace("õ", "o")
             .replace("ù", "u")
             .replace("ú", "u")
             .replace("û", "u")
             .replace("ý", "y")
             .replace("ÿ", "y")
             .replace("ñ", "n")
             .replace("ç", "c")
             .replace("À", "A")
             .replace("Á", "A")
             .replace("Â", "A")
             .replace("Ã", "A")
             .replace("Å", "A")
             .replace("È", "E")
             .replace("É", "E")
             .replace("Ê", "E")
             .replace("Ë", "E")
             .replace("Ì", "I")
             .replace("Í", "I")
             .replace("Î", "I")
             .replace("Ï", "I")
             .replace("Ò", "O")
             .replace("Ó", "O")
             .replace("Ô", "O")
             .replace("Õ", "O")
             .replace("Ù", "U")
             .replace("Ú", "U")
             .replace("Û", "U")
             .replace("Ý", "Y")
             .replace("Ñ", "N")
             .replace("Ç", "C")
             .replace("ä", "ae")
             .replace("ö", "oe")
             .replace("ü", "ue")
             .replace("ß", "ss")
             .replace("Ä", "Ae")
             .replace("Ö", "Oe")
             .replace("Ü", "Ue")
             .replace(Regex("\\((\\d+)\\)"), "Teil $1")  // (n) → Teil n (do this BEFORE removing special chars)
             .replace(Regex("[^a-zA-ZäöüßÄÖÜ\\s-]"), "")  // Remove all except Latin, umlauts, spaces, and hyphens
              .replace(Regex("[\\s-]+"), " ")  // Collapse multiple whitespace/hyphens to single space
              .replace("I\\b".toRegex(), "1")  // Roman I → 1
             .replace("II\\b".toRegex(), "2")  // Roman II → 2
             .replace("III\\b".toRegex(), "3")  // Roman III → 3
             .replace("IV\\b".toRegex(), "4")  // Roman IV → 4
             .replace("V\\b".toRegex(), "5")  // Roman V → 5
             .replace("VI\\b".toRegex(), "6")  // Roman VI → 6
             .replace("VII\\b".toRegex(), "7")  // Roman VII → 7
             .replace("VIII\\b".toRegex(), "8")  // Roman VIII → 8
             .replace("IX\\b".toRegex(), "9")  // Roman IX → 9
             .replace("X\\b".toRegex(), "10")  // Roman X → 10
             .trim()
             .lowercase()  // Ensure case-insensitive comparison
     }

    sealed class TableRow
    
    data class SubDirHeaderRow(val subDir: String) : TableRow()
    
    data class SeriesHeaderRow(val seriesName: String) : TableRow()
    
    data class SeriesTableRow(
        val seriesName: String,
        val seasonNumber: Int,
        val episodes: List<EpisodeTableCell>,
        val totalEpisodeCount: Int
    ) : TableRow()

    data class EpisodeTableCell(
        val episodeNumber: Int,
        val status: EpisodeStatus,
        val filesystemName: String?,
        val tmdbName: String?
    )

    private data class SeriesTableData(
        val seasons: MutableMap<Int, Map<Int, ScannedEpisode>>,
        val tmdbSeasons: MutableMap<Int, Map<Int, TmdbEpisode>> = mutableMapOf()
    )
}
