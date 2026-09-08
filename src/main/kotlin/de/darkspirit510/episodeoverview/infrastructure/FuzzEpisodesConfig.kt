package de.darkspirit510.episodeoverview.infrastructure

import java.io.File

/**
 * Configuration for series that should use episode fuzzing (season split detection)
 */
class FuzzEpisodesConfig {
    
    companion object {
        private const val CONFIG_FILE = "fuzz_episodes.conf"
        private var cachedConfig: Set<String>? = null

        /**
         * Reset the cached configuration for testing purposes
         */
        fun resetCache() {
            cachedConfig = null
        }
        
        /**
         * Get the set of series names that should use episode fuzzing
         */
         fun getFuzzSeries(): Set<String> {
            if (cachedConfig != null) {
                return cachedConfig!!
            }
            
            val seriesNames = mutableSetOf<String>()
            
            // Try to load from resources directory first
            try {
                val resourceUrl = javaClass.classLoader.getResource("/$CONFIG_FILE")
                if (resourceUrl != null) {
                    resourceUrl.openStream().bufferedReader().forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            seriesNames.add(trimmed)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            
            // Also try file system (for development/testing)
            try {
                val configFile = File(CONFIG_FILE)
                if (configFile.exists()) {
                    configFile.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            seriesNames.add(trimmed)
                        }
                    }
                } else {
                    // Try in resources directory
                    val resourcesConfigFile = File("src/main/resources/$CONFIG_FILE")
                    if (resourcesConfigFile.exists()) {
                        resourcesConfigFile.forEachLine { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                seriesNames.add(trimmed)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            
            cachedConfig = seriesNames
            
            // Log loaded fuzz series (similar to other config logging)
            println("Loaded fuzz episodes: ${if (seriesNames.isNotEmpty()) seriesNames.joinToString(", ") else "none"}")
            
            return seriesNames
        }
        
        /**
         * Normalize series name for comparison (case-insensitive, spaces/dashes equivalent)
         */
        private fun normalizeSeriesName(name: String): String {
            return name
                .replace("'", "")
                .replace(" ", "-")
                .lowercase()
        }
        
        /**
         * Check if a series should use episode fuzzing
         */
        fun shouldFuzzSeries(seriesName: String): Boolean {
            val normalizedName = normalizeSeriesName(seriesName)
            val fuzzSeries = getFuzzSeries()
            
            // Check both the original series name and the normalized version
            return fuzzSeries.any { configName ->
                val normalizedConfigName = normalizeSeriesName(configName)
                normalizedName.equals(normalizedConfigName, ignoreCase = true) ||
                seriesName.equals(configName, ignoreCase = true)
            }
        }
        
        /**
         * Reload the configuration (useful for testing)
         */
        fun reload() {
            cachedConfig = null
        }
    }
}