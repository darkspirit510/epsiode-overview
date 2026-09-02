package de.darkspirit510.episodeoverview.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.darkspirit510.episodeoverview.domain.TmdbEpisode
import de.darkspirit510.episodeoverview.domain.TmdbSeason
import de.darkspirit510.episodeoverview.domain.TmdbSeries
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class TmdbClient(
    private val apiToken: String,
    private val baseUrl: String = "https://api.themoviedb.org/3"
) {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()

    fun getSeries(seriesName: String): TmdbSeries? {
        // Try with original name
        var series = trySearchMatch(seriesName)

        if (series != null) {
            return series
        }

        // Try with normalized name (handle special characters)
        series = trySearchMatch(normalizeSeriesName(seriesName))

        if (series != null) {
            return series
        }

        // Try with English name
        series = trySearchMatch(seriesName, "en-US")

        if (series != null) {
            return series
        }

        // Try with normalized English name
        series = trySearchMatch(normalizeSeriesName(seriesName), "en-US")

        if (series != null) {
            return series
        }

        return null
    }

    private fun trySearchMatch(seriesName: String, language: String = "de-DE"): TmdbSeries? {
        println("Searching for series $seriesName")
        val encodedToken = java.net.URLEncoder.encode(apiToken, "UTF-8")
        val searchUrl = "$baseUrl/search/tv?api_key=$encodedToken&query=${java.net.URLEncoder.encode(seriesName, "UTF-8")}&language=$language"
        val searchResponse = executeRequest(searchUrl)

        val results = searchResponse["results"] as? List<Map<String, Any>> ?: return null
        val firstResult = results.firstOrNull() ?: return null

        val seriesId = firstResult["id"] as? Int ?: return null

        return getSeriesById(seriesId)
    }

    private fun normalizeSeriesName(name: String): String {
        return name
            .replace("'", "")  // Remove apostrophes
            .replace(" ", "+")  // Replace spaces with + for URL encoding
            .lowercase()
    }

    fun getSeriesById(seriesId: Int): TmdbSeries? {
        println("Fetching Metadata for series ID $seriesId")
        val encodedToken = java.net.URLEncoder.encode(apiToken, "UTF-8")
        val seriesUrl = "$baseUrl/tv/$seriesId?api_key=$encodedToken&language=de-DE&append_to_response=seasons"
        val seriesResponse = executeRequest(seriesUrl)

        val name = seriesResponse["name"] as? String ?: return null
        val seasonsResponse = seriesResponse["seasons"] as? List<Map<String, Any>> ?: return null

        val seasons = seasonsResponse.mapNotNull { seasonMap ->
            val seasonNumber = seasonMap["season_number"] as? Int ?: return@mapNotNull null
            // Skip season 0 (S00) - specials/extras
            if (seasonNumber == 0) return@mapNotNull null
            val seasonId = seasonMap["id"] as? Int ?: return@mapNotNull null
            val episodes = getSeasonEpisodes(seriesId, seasonNumber)
            TmdbSeason(seasonId, seasonNumber, episodes)
        }

        return TmdbSeries(seriesId, name, seasons)
    }

    private fun getSeasonEpisodes(seriesId: Int, seasonNumber: Int): List<TmdbEpisode> {
        println("Fetching Season $seasonNumber for series ID $seriesId")
        val encodedToken = java.net.URLEncoder.encode(apiToken, "UTF-8")
        val episodesUrl = "$baseUrl/tv/$seriesId/season/$seasonNumber?api_key=$encodedToken&language=de-DE"
        val episodesResponse = executeRequest(episodesUrl)

        val episodesList = episodesResponse["episodes"] as? List<Map<String, Any>> ?: return emptyList()

        return episodesList.mapNotNull { episodeMap ->
            val id = episodeMap["id"] as? Int ?: return@mapNotNull null
            val name = episodeMap["name"] as? String
            val episodeNumber = episodeMap["episode_number"] as? Int ?: return@mapNotNull null
            TmdbEpisode(id, name ?: "", episodeNumber, seasonNumber)
        }
    }

    private fun executeRequest(url: String): Map<String, Any> {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                val json = response.body?.string() ?: throw IOException("Empty response body")
                mapper.readValue(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
