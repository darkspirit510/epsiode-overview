package de.darkspirit510.episodeoverview.infrastructure

import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import java.io.File

class FileSystemScanner {

    fun scanDirectory(baseDir: String, subDirs: List<String>): Map<String, Map<Int, List<ScannedEpisode>>> {
        val result = mutableMapOf<String, Map<Int, List<ScannedEpisode>>>()

        subDirs.forEach { subDir ->
            if (subDir == "Bonus" || subDir == "Output") {
                println("Skipping directory: ${File(baseDir, subDir).absolutePath}")
                return@forEach
            }
            val fullPath = File(baseDir, subDir).absolutePath
            val seriesMap = scanRecursively(File(fullPath))
            // Prefix each series name with the subDir
            seriesMap.forEach { (seriesName, seasons) ->
                val prefixedSeriesName = "$subDir/$seriesName"
                result[prefixedSeriesName] = seasons
            }
        }

        return result
    }

    private fun scanRecursively(directory: File): Map<String, Map<Int, List<ScannedEpisode>>> {
        val result = mutableMapOf<String, MutableMap<Int, MutableList<ScannedEpisode>>>()

        if (!directory.exists() || !directory.isDirectory) {
            return result
        }

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name == "Bonus" || file.name == "Output") {
                    println("Skipping directory: ${file.absolutePath}")
                    return@forEach
                }
                 if (file.name.matches(Regex("S\\d+"))) {
                     // This is a season directory, find the parent series name
                     val seriesName = findSeriesName(file.parentFile, file.name)
                     if (seriesName != null) {
                         val seasonNumber = file.name.substring(1).toIntOrNull() ?: return@forEach
                         // Skip season 0 (S00) - specials/extras
                         if (seasonNumber == 0) return@forEach
                         val episodes = scanSeasonDirectory(file)
                         if (episodes.isNotEmpty()) {
                             val seriesSeasons = result.getOrPut(seriesName) { mutableMapOf() }
                             seriesSeasons[seasonNumber] = episodes.toMutableList()
                         }
                     }
                } else {
                    // This might be a series directory, recurse into it
                    val nestedResult = scanRecursively(file)
                    nestedResult.forEach { (seriesName, seasons) ->
                        val existingSeasons = result.getOrPut(seriesName) { mutableMapOf() }
                        seasons.forEach { (seasonNumber, episodes) ->
                            existingSeasons.getOrPut(seasonNumber) { mutableListOf() }.addAll(episodes)
                        }
                    }
                }
            } else if (file.isFile) {
                // File directly in a directory, try to extract episode info
                val episode = extractEpisodeInfo(file.name)
                if (episode != null) {
                    // Find the series name from the parent directory structure
                    val seriesName = findSeriesName(file.parentFile, null)
                    if (seriesName != null) {
                        val seriesSeasons = result.getOrPut(seriesName) { mutableMapOf() }
                        val seasonEpisodes = seriesSeasons.getOrPut(episode.seasonNumber) { mutableListOf() }
                        seasonEpisodes.add(episode)
                    }
                }
            }
        }

        return result
    }

    private fun findSeriesName(directory: File, seasonDirName: String?): String? {
        // If the directory is a season directory, go up one level to get the series
        val seriesDir = if (seasonDirName != null && directory.name == seasonDirName) {
            directory.parentFile
        } else {
            directory
        }

        if (seriesDir == null || !seriesDir.exists()) return null

        // The series name is always the immediate parent directory of the season directory
        // or the directory containing the episode files
        return seriesDir.name
    }

    private fun scanSeasonDirectory(seasonDir: File): List<ScannedEpisode> {
        val episodes = mutableListOf<ScannedEpisode>()
        val seasonNumber = seasonDir.name.substring(1).toIntOrNull() ?: return episodes

        seasonDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val episode = extractEpisodeInfo(file.name)
                if (episode != null) {
                    episodes.add(episode.copy(seasonNumber = seasonNumber))
                }
            }
        }

        return episodes
    }

     internal fun extractEpisodeInfo(fileName: String): ScannedEpisode? {
         val pattern = Regex("S(\\d+)E(\\d+)(E\\d+|-E\\d+)?")
         val match = pattern.find(fileName) ?: return null

         val seasonNumber = match.groupValues[1].toInt()
         // Skip season 0 (S00) - specials/extras
         if (seasonNumber == 0) return null
         
         val episodeNumber = match.groupValues[2].toInt()
         
         // Extract episode name by finding " - " after the full episode pattern match
         val afterEpisode = fileName.substring(match.range.last + 1)
         val episodeName = if (afterEpisode.trim().startsWith("-")) {
             afterEpisode.trim().substring(1).trim()
         } else {
             null
         }

         return ScannedEpisode(
             seasonNumber = seasonNumber,
             episodeNumber = episodeNumber,
             fileName = fileName,
             episodeName = episodeName
         )
     }
}
