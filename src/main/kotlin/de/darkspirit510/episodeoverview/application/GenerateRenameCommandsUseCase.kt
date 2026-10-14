package de.darkspirit510.episodeoverview.application

import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import de.darkspirit510.episodeoverview.domain.TmdbSeries
import de.darkspirit510.episodeoverview.infrastructure.CacheManagerInterface

class GenerateRenameCommandsUseCase(
    private val cacheManager: CacheManagerInterface
) {
    
    data class RenameCommand(
        val oldFileName: String,
        val newFileName: String
    )
    
    data class RenameCommandsResult(
        val seriesName: String,
        val seasonNumber: Int,
        val commands: List<RenameCommand>
    )
    
    fun generateRenameCommands(seriesName: String, seasonNumber: Int): RenameCommandsResult {
        val allScannedEpisodes = cacheManager.loadAllScannedEpisodes()
        
        val seriesKey = allScannedEpisodes.keys.find { key ->
            val normalizedKey = normalizeSeriesName(key.substringAfter("/", key))
            val normalizedSearch = normalizeSeriesName(seriesName)
            normalizedKey == normalizedSearch
        }
        
        val filesystemEpisodes = seriesKey?.let { allScannedEpisodes[it] }?.filter { 
            it.seasonNumber == seasonNumber 
        } ?: emptyList()
        
        val tmdbSeries = findTmdbSeriesForName(seriesName)
        val tmdbEpisodes = tmdbSeries?.seasons?.find { it.seasonNumber == seasonNumber }?.episodes ?: emptyList()
        
        val commands = generateCommands(filesystemEpisodes, tmdbEpisodes, seasonNumber)
        
        return RenameCommandsResult(seriesName, seasonNumber, commands)
    }
    
    private fun generateCommands(
        filesystemEpisodes: List<ScannedEpisode>,
        tmdbEpisodes: List<de.darkspirit510.episodeoverview.domain.TmdbEpisode>,
        seasonNumber: Int
    ): List<RenameCommand> {
        val commands = mutableListOf<RenameCommand>()
        val tmdbEpisodeMap = tmdbEpisodes.associateBy { it.episodeNumber }
        
        for (fsEpisode in filesystemEpisodes) {
            // Handle multiple episode numbers in a single file
            val episodeNumbers = if (fsEpisode.episodeNumbers.size > 1) {
                fsEpisode.episodeNumbers
            } else {
                listOf(fsEpisode.episodeNumber)
            }
            
            for (episodeNumber in episodeNumbers) {
                val tmdbEpisode = tmdbEpisodeMap[episodeNumber]
                
                if (tmdbEpisode != null) {
                    val fsName = extractNameFromFilename(fsEpisode.fileName)
                    val tmdbName = tmdbEpisode.name
                    
                    if (!areNamesMatching(fsName, tmdbName)) {
                        val newFileName = generateNewFileName(fsEpisode.fileName, tmdbEpisode, seasonNumber)
                        commands.add(RenameCommand(fsEpisode.fileName, newFileName))
                        // Only add one command per file, even if it matches multiple episodes
                        break
                    }
                }
            }
        }
        
        return commands
    }
    
    private fun generateNewFileName(oldFileName: String, tmdbEpisode: de.darkspirit510.episodeoverview.domain.TmdbEpisode, seasonNumber: Int): String {
        val extension = oldFileName.substringAfterLast('.', "")
        val seasonStr = "S${seasonNumber.toString().padStart(2, '0')}"
        val episodeStr = "E${tmdbEpisode.episodeNumber.toString().padStart(2, '0')}"
        val cleanTmdbName = cleanNameForFilename(tmdbEpisode.name)
        val newBaseName = "$seasonStr$episodeStr - $cleanTmdbName"
        
        return if (extension.isNotEmpty()) {
            "$newBaseName.$extension"
        } else {
            newBaseName
        }
    }
    
    private fun cleanNameForFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("[\\s-]+"), " ")
            .trim()
    }
    
    private fun extractNameFromFilename(filename: String): String {
        return filename
            .replace(Regex("S\\d+E\\d+"), "")
            .replace(Regex("^[\\s-]+"), "")
            .replace(Regex("\\.[^.]*$"), "")
            .replace(Regex("[\\s-]+"), " ")
            .trim()
    }
    
    private fun areNamesMatching(fsName: String, tmdbName: String): Boolean {
        if (fsName.isEmpty() || tmdbName.isEmpty()) {
            return false
        }
        
        val normFs = normalizeForComparison(fsName)
        val normTmdb = normalizeForComparison(tmdbName)
        
        return normFs.equals(normTmdb, ignoreCase = true) ||
               normFs.replace(Regex("\\s"), "").equals(normTmdb.replace(Regex("\\s"), ""), ignoreCase = true)
    }
    
    private fun normalizeForComparison(name: String): String {
        return name
            .replace("&", "und")
            .replace("\\", " ")
            .replace("/", " ")
            .replace("#", "")
            .replace(".", "")
            .replace("à", "a").replace("á", "a").replace("â", "a").replace("ã", "a").replace("å", "a")
            .replace("è", "e").replace("é", "e").replace("ê", "e").replace("ë", "e")
            .replace("ì", "i").replace("í", "i").replace("î", "i").replace("ï", "i")
            .replace("ò", "o").replace("ó", "o").replace("ô", "o").replace("õ", "o")
            .replace("ù", "u").replace("ú", "u").replace("û", "u")
            .replace("ý", "y").replace("ÿ", "y")
            .replace("ñ", "n").replace("ç", "c")
            .replace("À", "A").replace("Á", "A").replace("Â", "A").replace("Ã", "A").replace("Å", "A")
            .replace("È", "E").replace("É", "E").replace("Ê", "E").replace("Ë", "E")
            .replace("Ì", "I").replace("Í", "I").replace("Î", "I").replace("Ï", "I")
            .replace("Ò", "O").replace("Ó", "O").replace("Ô", "O").replace("Õ", "O")
            .replace("Ù", "U").replace("Ú", "U").replace("Û", "U")
            .replace("Ý", "Y").replace("Ñ", "N").replace("Ç", "C")
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
            .replace(Regex("\\((\\d+)\\)"), "Teil $1")
            .replace(Regex("[^a-zA-ZäöüßÄÖÜ\\s-]"), "")
            .replace(Regex("[\\s-]+"), " ")
            .replace("I\\b".toRegex(), "1").replace("II\\b".toRegex(), "2").replace("III\\b".toRegex(), "3")
            .replace("IV\\b".toRegex(), "4").replace("V\\b".toRegex(), "5").replace("VI\\b".toRegex(), "6")
            .replace("VII\\b".toRegex(), "7").replace("VIII\\b".toRegex(), "8").replace("IX\\b".toRegex(), "9")
            .replace("X\\b".toRegex(), "10")
            .trim()
            .lowercase()
    }
    
    private fun normalizeSeriesName(name: String): String {
        return name
            .replace("'", "")
            .replace(" ", "-")
            .lowercase()
    }
    
    private fun findTmdbSeriesForName(seriesName: String): TmdbSeries? {
        val allScannedEpisodes = cacheManager.loadAllScannedEpisodes()
        
        val matchingKey = allScannedEpisodes.keys.find { key ->
            val normalizedKey = normalizeSeriesName(key.substringAfter("/", key))
            val normalizedSearch = normalizeSeriesName(seriesName)
            normalizedKey == normalizedSearch
        }
        
        if (matchingKey != null) {
            val filesystemName = matchingKey.substringAfter("/", matchingKey)
            return cacheManager.loadTmdbSeriesByName(filesystemName)
        }
        
        return cacheManager.loadTmdbSeriesByName(seriesName)
    }
    
    fun formatAsShellCommands(result: RenameCommandsResult): String {
        return result.commands.joinToString("\n") { command ->
            val escapedOld = escapeShellArgument(command.oldFileName)
            val escapedNew = escapeShellArgument(command.newFileName)
            "mv $escapedOld $escapedNew"
        }
    }
    
    private fun escapeShellArgument(filename: String): String {
        return "'" + filename.replace("'", "'\\''") + "'"
    }
}
