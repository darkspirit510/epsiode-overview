package de.darkspirit510.episodeoverview.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import de.darkspirit510.episodeoverview.domain.TmdbSeries
import java.io.File

class CacheManager(
    private val cacheBaseDir: String = "cache"
) : CacheManagerInterface {
    private val mapper = jacksonObjectMapper().apply {
        enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
    }
    private val episodesCacheDir = File("$cacheBaseDir/episodes")
    private val tmdbCacheDir = File("$cacheBaseDir/tmdb")
    private val episodesCacheFile = File("$cacheBaseDir/episodes/all.json")

    // Blacklist configuration
    data class BlacklistConfig(
        val full: List<String> = emptyList(),
        val series: Map<String, List<String>> = emptyMap(),
        val episodes: Map<String, Map<String, List<String>>> = emptyMap()
    )

    private var blacklist: BlacklistConfig = BlacklistConfig()
    private var overwriteMap: Map<String, Int> = emptyMap()

    init {
        episodesCacheDir.mkdirs()
        tmdbCacheDir.mkdirs()
        loadBlacklist()
        loadOverwriteConfig()
    }

    private fun loadBlacklist() {
        try {
            // Try to load from classpath first
            val classPathBlacklist = javaClass.getResourceAsStream("/blacklist.conf")
            if (classPathBlacklist != null) {
                loadBlacklistFromStream(classPathBlacklist)
                return
            }
            
            // Fallback to file system
            val blacklistFile = File("blacklist.conf")
            if (blacklistFile.exists()) {
                blacklistFile.inputStream().use { loadBlacklistFromStream(it) }
            }
        } catch (e: Exception) {
            println("Error loading blacklist: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadBlacklistFromStream(stream: java.io.InputStream) {
        val fullList = mutableListOf<String>()
        val seriesMap = mutableMapOf<String, MutableList<String>>()
        val episodesMap = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        var currentSection: String? = null
        var currentSeries: String? = null
        
        stream.bufferedReader().forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            
            // Match full = ["series1", "series2"]
            val fullMatch = """full\s*=\s*\[(.*?)\]""".toRegex().find(trimmed)
            if (fullMatch != null) {
                val content = fullMatch.groupValues[1]
                fullList.addAll(parseArrayContent(content))
                return@forEachLine
            }
            
            // Match series = {
            if ("""series\s*=\s*\{""".toRegex().matches(trimmed)) {
                currentSection = "series"
                currentSeries = null
                return@forEachLine
            }
            
            // Match episodes = {
            if ("""episodes\s*=\s*\{""".toRegex().matches(trimmed)) {
                currentSection = "episodes"
                currentSeries = null
                return@forEachLine
            }
            
            // Match closing brace for sections
            if (trimmed == "}") {
                if (currentSection == "episodes" && currentSeries != null) {
                    currentSeries = null
                } else {
                    currentSection = null
                    currentSeries = null
                }
                return@forEachLine
            }
            
            // Match "Series Name" = ["S01", "02"] for series section
            val seriesMatch = """"([^"]+)"\s*=\s*\[(.*?)\]""".toRegex().find(trimmed)
            if (seriesMatch != null && currentSection == "series") {
                val seriesName = seriesMatch.groupValues[1]
                val content = seriesMatch.groupValues[2]
                seriesMap.getOrPut(seriesName) { mutableListOf() }.addAll(parseArrayContent(content))
                return@forEachLine
            }
            
            // Match "Series Name" = { for episodes section
            val seriesStartMatch = """"([^"]+)"\s*=\s*\{""".toRegex().find(trimmed)
            if (seriesStartMatch != null && currentSection == "episodes") {
                val seriesName = seriesStartMatch.groupValues[1]
                currentSeries = seriesName
                episodesMap.getOrPut(seriesName) { mutableMapOf() }
                return@forEachLine
            }
            
            // Match "SXX" = ["E01", "E02"] for episodes section
            val seasonMatch = """"(S\d+)"\s*=\s*\[(.*?)\]""".toRegex().find(trimmed)
            if (seasonMatch != null && currentSection == "episodes" && currentSeries != null) {
                val seasonKey = seasonMatch.groupValues[1]
                val content = seasonMatch.groupValues[2]
                val seriesEpisodes = episodesMap[currentSeries]
                if (seriesEpisodes != null) {
                    val seasonEpisodes = seriesEpisodes.getOrPut(seasonKey) { mutableListOf() }
                    seasonEpisodes.addAll(parseArrayContent(content))
                }
                return@forEachLine
            }
        }
        
        blacklist = BlacklistConfig(fullList, seriesMap, episodesMap)
        println("Loaded blacklist: full=$fullList, series=${seriesMap.keys}, episodes=${episodesMap.keys}")
    }
    
    private fun parseArrayContent(content: String): List<String> {
        // Extract all quoted strings from the array content
        return """"([^"]+)"""".toRegex().findAll(content).map { it.groupValues[1] }.toList()
    }

    override fun isSeriesBlacklisted(seriesName: String): Boolean {
        val normalizedName = normalizeSeriesName(seriesName)
        return blacklist.full.any { 
            it.equals(seriesName, ignoreCase = true) || 
            it.equals(normalizedName, ignoreCase = true)
        }
    }

    override fun isSeasonBlacklisted(seriesName: String, seasonNumber: Int): Boolean {
        val normalizedName = normalizeSeriesName(seriesName)
        val seasonStr = "S${seasonNumber.toString().padStart(2, '0')}"
        val seasonStrNoPrefix = seasonNumber.toString().padStart(2, '0')
        
        // Check both original and normalized series names
        val seriesNamesToCheck = listOf(seriesName, normalizedName)
        
        return blacklist.series.any { (blacklistSeries, blacklistSeasons) ->
            seriesNamesToCheck.any { name ->
                name.equals(blacklistSeries, ignoreCase = true) || 
                normalizeSeriesName(name).equals(normalizeSeriesName(blacklistSeries), ignoreCase = true)
            } && blacklistSeasons.any { blacklistSeason ->
                blacklistSeason.equals(seasonStr, ignoreCase = true) ||
                blacklistSeason.equals(seasonStrNoPrefix, ignoreCase = true) ||
                blacklistSeason.equals("S$seasonNumber", ignoreCase = true) ||
                blacklistSeason.equals(seasonNumber.toString(), ignoreCase = true)
            }
        }
    }

    override fun isEpisodeOverridden(seriesName: String, seasonNumber: Int, episodeNumber: Int): Boolean {
        val normalizedName = normalizeSeriesName(seriesName)
        val seasonStr = "S${seasonNumber.toString().padStart(2, '0')}"
        val episodeStr = "E${episodeNumber.toString().padStart(2, '0')}"
        
        // Check both original and normalized series names
        val seriesNamesToCheck = listOf(seriesName, normalizedName)
        
        return blacklist.episodes.any { (blacklistSeries, seasons) ->
            seriesNamesToCheck.any { name ->
                name.equals(blacklistSeries, ignoreCase = true) || 
                normalizeSeriesName(name).equals(normalizeSeriesName(blacklistSeries), ignoreCase = true)
            } && seasons.any { (blacklistSeason, episodes) ->
                blacklistSeason.equals(seasonStr, ignoreCase = true) &&
                episodes.any { it.equals(episodeStr, ignoreCase = true) }
            }
        }
    }

    private fun loadOverwriteConfig() {
        try {
            // Try to load from classpath first
            val classPathOverwrite = javaClass.getResourceAsStream("/overwrite.conf")
            if (classPathOverwrite != null) {
                loadOverwriteFromStream(classPathOverwrite)
                return
            }
            
            // Fallback to file system
            val overwriteFile = File("overwrite.conf")
            if (overwriteFile.exists()) {
                overwriteFile.inputStream().use { loadOverwriteFromStream(it) }
            }
        } catch (e: Exception) {
            println("Error loading overwrite config: ${e.message}")
        }
    }

    private fun loadOverwriteFromStream(stream: java.io.InputStream) {
        val overrides = mutableMapOf<String, Int>()
        stream.bufferedReader().forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            val parts = trimmed.split(":", limit = 2)
            if (parts.size == 2) {
                val seriesName = parts[0].trim()
                val id = parts[1].trim().toIntOrNull()
                if (id != null) {
                    overrides[seriesName] = id
                }
            }
        }
        overwriteMap = overrides
        println("Loaded overwrite config: ${overrides.keys}")
    }

    override fun getOverriddenId(seriesName: String): Int? {
        // Check exact match first
        if (overwriteMap.containsKey(seriesName)) {
            return overwriteMap[seriesName]
        }
        // Check normalized match
        val normalizedName = normalizeSeriesName(seriesName)
        return overwriteMap.entries.firstOrNull { (name, _) ->
            normalizeSeriesName(name).equals(normalizedName, ignoreCase = true)
        }?.value
    }

    override fun saveScannedEpisodes(seriesName: String, episodes: List<ScannedEpisode>) {
        // Load existing data
        val existingData = mutableMapOf<String, List<ScannedEpisode>>()
        if (episodesCacheFile.exists()) {
            try {
                val loaded = mapper.readValue(episodesCacheFile, 
                    object : TypeReference<Map<String, List<ScannedEpisode>>>() {})
                existingData.putAll(loaded)
            } catch (e: Exception) {
                // If file is corrupted, start fresh
            }
        }
        
        // Add/update this series
        existingData[seriesName] = episodes
        
        // Save all data
        mapper.writeValue(episodesCacheFile, existingData)
    }

    override fun loadScannedEpisodes(seriesName: String): List<ScannedEpisode>? {
        if (!episodesCacheFile.exists()) {
            // Try to migrate from old per-series files
            migrateOldCache()
        }
        
        if (!episodesCacheFile.exists()) return null
        
        try {
            val allData = mapper.readValue(episodesCacheFile, 
                object : TypeReference<Map<String, List<ScannedEpisode>>>() {})
            return allData[seriesName]
        } catch (e: Exception) {
            return null
        }
    }

    override fun loadAllScannedEpisodes(): Map<String, List<ScannedEpisode>> {
        if (!episodesCacheFile.exists()) {
            migrateOldCache()
        }
        
        if (!episodesCacheFile.exists()) return emptyMap()
        
        try {
            return mapper.readValue(episodesCacheFile, 
                object : TypeReference<Map<String, List<ScannedEpisode>>>() {})
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    private fun migrateOldCache() {
        println("Migrating old episode cache to single file...")
        val oldFiles = episodesCacheDir.listFiles { file: File -> file.name.endsWith(".json") } ?: return
        val allData = mutableMapOf<String, List<ScannedEpisode>>()
        
        oldFiles.forEach { file ->
            try {
                val seriesName = file.nameWithoutExtension
                val episodes = mapper.readValue(file, 
                    object : TypeReference<List<ScannedEpisode>>() {})
                allData[seriesName] = episodes
            } catch (e: Exception) {
                println("Error migrating ${file.name}: ${e.message}")
            }
        }
        
        if (allData.isNotEmpty()) {
            mapper.writeValue(episodesCacheFile, allData)
            println("Migrated ${allData.size} series to single cache file")
        }
    }

    override fun saveTmdbSeries(series: TmdbSeries, filesystemName: String?) {
        // Use filesystem name if provided, otherwise use TMDB series name
        val nameToUse = filesystemName ?: series.name
        val normalizedName = normalizeSeriesName(nameToUse)
        val fileName = "${series.id}-$normalizedName.json"
        val file = File(tmdbCacheDir, fileName)
        mapper.writeValue(file, series)
    }

    private fun normalizeSeriesName(name: String): String {
        return name
            .replace("'", "")  // Remove apostrophes
            .replace(" ", "-")  // Replace spaces with dashes
            .lowercase()
    }

    override fun loadTmdbSeries(seriesId: Int): TmdbSeries? {
        val files = tmdbCacheDir.listFiles { file: File -> file.name.startsWith("$seriesId-") } ?: return null

        return files.firstOrNull()?.let { file ->
            mapper.readValue(file, TmdbSeries::class.java)
        }
    }

    override fun loadTmdbSeriesByName(seriesName: String): TmdbSeries? {
        val normalizedName = normalizeSeriesName(seriesName)
        val files = tmdbCacheDir.listFiles { file: File -> file.name.endsWith("-$normalizedName.json") } ?: return null

        return files.firstOrNull()?.let { file ->
            mapper.readValue(file, TmdbSeries::class.java)
        }
    }

    override fun clearEpisodesCache() {
        episodesCacheFile.delete()
        episodesCacheDir.listFiles()?.forEach { it.delete() }
    }
}
